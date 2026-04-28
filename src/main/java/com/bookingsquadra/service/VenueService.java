package com.bookingsquadra.service;

import com.bookingsquadra.dto.BookingCountDto;
import com.bookingsquadra.dto.CancelPolicyDto;
import com.bookingsquadra.dto.CourtDto;
import com.bookingsquadra.dto.OperatingHoursDto;
import com.bookingsquadra.dto.VenueDto;
import com.bookingsquadra.dto.VenueResponseDto;
import com.bookingsquadra.entity.CancelPolicy;
import com.bookingsquadra.entity.City;
import com.bookingsquadra.entity.Court;
import com.bookingsquadra.entity.OperatingHours;
import com.bookingsquadra.entity.Venue;
import com.bookingsquadra.repository.CancelPolicyRepository;
import com.bookingsquadra.repository.CityRepository;
import com.bookingsquadra.repository.CourtRepository;
import com.bookingsquadra.repository.OperatingHoursRepository;
import com.bookingsquadra.repository.VenueDistanceProjection;
import com.bookingsquadra.repository.VenueRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Service
public class VenueService {

    private static final double DEFAULT_MAX_DISTANCE_KM = 999.0;

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
    public List<VenueResponseDto> search(
            Double lat,
            Double lon,
            Double distanceKm,
            List<String> sportsFilters
    ) {
        double maxDistanceKm = distanceKm == null ? DEFAULT_MAX_DISTANCE_KM : distanceKm;
        String sportsParam = (sportsFilters == null || sportsFilters.isEmpty())
                ? ""
                : String.join(",", sportsFilters);

        return venueRepository
                .findVenuesWithDistance(lat, lon, maxDistanceKm, sportsParam)
                .stream()
                .map(VenueService::toDto)
                .toList();
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
                v.getSports() == null ? Collections.emptyList() : List.of(v.getSports()),
                v.getAmenities(),
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
        String[] sports = p.getSports();
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
                sports == null ? Collections.emptyList() : List.of(sports),
                p.getAmenities(),
                p.getPriceCents(),
                p.getDistanceKm()
        );
    }
}
