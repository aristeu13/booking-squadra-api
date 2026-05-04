package com.bookingsquadra.service;

import com.bookingsquadra.dto.BookingCountDto;
import com.bookingsquadra.dto.CancelPolicyDto;
import com.bookingsquadra.dto.CourtDto;
import com.bookingsquadra.dto.OperatingHoursDto;
import com.bookingsquadra.dto.VenueDto;
import com.bookingsquadra.dto.VenueResponseDto;
import com.bookingsquadra.entity.Amenity;
import com.bookingsquadra.entity.CancelPolicy;
import com.bookingsquadra.entity.City;
import com.bookingsquadra.entity.Court;
import com.bookingsquadra.entity.OperatingHours;
import com.bookingsquadra.entity.Sport;
import com.bookingsquadra.entity.Venue;
import com.bookingsquadra.repository.CancelPolicyRepository;
import com.bookingsquadra.repository.CityRepository;
import com.bookingsquadra.repository.CourtRepository;
import com.bookingsquadra.repository.OperatingHoursRepository;
import com.bookingsquadra.repository.VenueDistanceProjection;
import com.bookingsquadra.repository.VenueRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class VenueService {

    private static final double DEFAULT_MAX_DISTANCE_KM = 999.0;
    private static final int MAX_PAGE_SIZE = 100;

    private static final ObjectMapper JSON = new ObjectMapper();

    private final CityRepository cityRepository;
    private final VenueRepository venueRepository;
    private final CourtRepository courtRepository;
    private final OperatingHoursRepository operatingHoursRepository;
    private final CancelPolicyRepository cancelPolicyRepository;

    public VenueService(
            CityRepository cityRepository,
            VenueRepository venueRepository,
            CourtRepository courtRepository,
            OperatingHoursRepository operatingHoursRepository,
            CancelPolicyRepository cancelPolicyRepository
    ) {
        this.cityRepository = cityRepository;
        this.venueRepository = venueRepository;
        this.courtRepository = courtRepository;
        this.operatingHoursRepository = operatingHoursRepository;
        this.cancelPolicyRepository = cancelPolicyRepository;
    }

    @Transactional(readOnly = true)
    public Page<VenueResponseDto> search(
            Double lat,
            Double lon,
            Double distanceKm,
            List<Sport> sportsFilters,
            List<Amenity> amenitiesFilters,
            String nameQuery,
            int page,
            int pageSize
    ) {
        double maxDistanceKm = distanceKm == null ? DEFAULT_MAX_DISTANCE_KM : distanceKm;
        String sportsParam = (sportsFilters == null || sportsFilters.isEmpty())
                ? ""
                : String.join(",", sportsFilters.stream()
                        .filter(s -> s != null).map(Sport::code).toList());
        String amenitiesParam = (amenitiesFilters == null || amenitiesFilters.isEmpty())
                ? ""
                : String.join(",", amenitiesFilters.stream()
                        .filter(a -> a != null).map(Amenity::code).toList());
        String nameQueryParam = (nameQuery == null || nameQuery.isBlank())
                ? ""
                : nameQuery.trim();

        PageRequest pageable = PageRequest.of(
                Math.max(page, 0),
                Math.min(Math.max(pageSize, 1), MAX_PAGE_SIZE)
        );

        return venueRepository
                .findVenuesWithDistance(lat, lon, maxDistanceKm, sportsParam, amenitiesParam, nameQueryParam, pageable)
                .map(VenueService::toDto);
    }

    @Transactional(readOnly = true)
    public VenueDto getById(UUID venueId) {
        Venue venue = venueRepository.findById(venueId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Venue not found"));
        City city = cityRepository.findById(venue.getCityId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.INTERNAL_SERVER_ERROR, "Venue city not found"));
        List<CourtDto> courts = courtRepository
                .findByVenueIdAndActiveTrueOrderBySortOrderAsc(venueId)
                .stream()
                .map(VenueService::toCourtDto)
                .toList();
        List<OperatingHoursDto> hours = operatingHoursRepository
                .findByVenueId(venueId)
                .stream()
                .sorted((a, b) -> Short.compare(a.getDayOfWeek(), b.getDayOfWeek()))
                .map(VenueService::toHoursDto)
                .toList();
        CancelPolicyDto policy = cancelPolicyRepository
                .findByVenueId(venueId)
                .map(VenueService::toPolicyDto)
                .orElse(null);
        return toVenueDto(venue, city, courts, hours, policy);
    }

    @Transactional(readOnly = true)
    public BookingCountDto countBookings(UUID venueId) {
        if (!venueRepository.existsById(venueId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Venue not found");
        }
        return new BookingCountDto(venueId, venueRepository.countBookingsByVenueId(venueId));
    }

    @Transactional(readOnly = true)
    public CancelPolicyDto getCancelPolicy(UUID venueId) {
        if (!venueRepository.existsById(venueId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Venue not found");
        }
        return cancelPolicyRepository.findByVenueId(venueId)
                .map(VenueService::toPolicyDto)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Cancel policy not found"));
    }

    private static VenueDto toVenueDto(
            Venue v,
            City city,
            List<CourtDto> courts,
            List<OperatingHoursDto> hours,
            CancelPolicyDto policy
    ) {
        return new VenueDto(
                v.getId(),
                v.getName(),
                v.getSlug(),
                v.getDescription(),
                v.getImageUrl(),
                v.getAddress(),
                city.getId(),
                city.getName(),
                city.getStateCode(),
                city.getTimezone(),
                v.getLatitude(),
                v.getLongitude(),
                sportsFromArray(v.getSports()),
                amenitiesFromMap(v.getAmenities()),
                v.getPriceCents(),
                v.getSlotDurationMinutes(),
                v.getActive(),
                courts,
                hours,
                policy
        );
    }

    private static CourtDto toCourtDto(Court c) {
        return new CourtDto(
                c.getId(),
                c.getVenueId(),
                c.getName(),
                c.getSurfaceType(),
                c.getIndoor(),
                c.getSortOrder(),
                c.getActive()
        );
    }

    private static OperatingHoursDto toHoursDto(OperatingHours h) {
        return new OperatingHoursDto(h.getDayOfWeek(), h.getOpenTime(), h.getCloseTime());
    }

    private static CancelPolicyDto toPolicyDto(CancelPolicy p) {
        return new CancelPolicyDto(
                p.getPixFullRefundHours(),
                p.getPixPartialRefundHours(),
                p.getPixPartialRefundPercent(),
                p.getLocalCancelHours(),
                p.getNoShowPixThreshold()
        );
    }

    private static VenueResponseDto toDto(VenueDistanceProjection p) {
        return new VenueResponseDto(
                p.getId(),
                p.getSlug(),
                p.getName(),
                p.getDescription(),
                p.getImageUrl(),
                p.getAddress(),
                p.getCityId(),
                p.getCity(),
                p.getStateCode(),
                p.getTimezone(),
                sportsFromArray(p.getSports()),
                amenitiesFromJson(p.getAmenities()),
                p.getPriceCents(),
                p.getDistanceKm(),
                p.getNumberOfCourts()
        );
    }

    private static List<Sport> sportsFromArray(String[] codes) {
        if (codes == null || codes.length == 0) return Collections.emptyList();
        List<Sport> out = new ArrayList<>(codes.length);
        for (String code : codes) {
            Sport s = Sport.fromCodeOrNull(code);
            if (s != null) out.add(s);
        }
        return out;
    }

    private static List<Amenity> amenitiesFromMap(Map<String, Object> map) {
        if (map == null || map.isEmpty()) return Collections.emptyList();
        List<Amenity> out = new ArrayList<>(map.size());
        for (Map.Entry<String, Object> e : map.entrySet()) {
            if (!isTruthy(e.getValue())) continue;
            Amenity a = Amenity.fromCodeOrNull(e.getKey());
            if (a != null && !out.contains(a)) out.add(a);
        }
        return out;
    }

    // The list query returns amenities as `jsonb::text`. Parse it here so the
    // FE always sees a typed `List<Amenity>` instead of a raw JSON string.
    private static List<Amenity> amenitiesFromJson(String json) {
        if (json == null || json.isBlank()) return Collections.emptyList();
        JsonNode node;
        try {
            node = JSON.readTree(json);
        } catch (JsonProcessingException e) {
            return Collections.emptyList();
        }
        List<Amenity> out = new ArrayList<>();
        if (node.isObject()) {
            Iterator<String> names = node.fieldNames();
            while (names.hasNext()) {
                String key = names.next();
                if (!isTruthy(node.get(key))) continue;
                Amenity a = Amenity.fromCodeOrNull(key);
                if (a != null && !out.contains(a)) out.add(a);
            }
        } else if (node.isArray()) {
            for (JsonNode item : node) {
                if (!item.isTextual()) continue;
                Amenity a = Amenity.fromCodeOrNull(item.asText());
                if (a != null && !out.contains(a)) out.add(a);
            }
        }
        return out;
    }

    private static boolean isTruthy(Object value) {
        if (value == null) return false;
        if (value instanceof Boolean b) return b;
        if (value instanceof String s) return !s.isBlank() && !"false".equalsIgnoreCase(s);
        return true;
    }

    private static boolean isTruthy(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) return false;
        if (node.isBoolean()) return node.asBoolean();
        if (node.isTextual()) {
            String s = node.asText();
            return !s.isBlank() && !"false".equalsIgnoreCase(s);
        }
        return true;
    }
}
