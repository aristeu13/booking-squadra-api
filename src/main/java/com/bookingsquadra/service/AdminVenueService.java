package com.bookingsquadra.service;

import com.bookingsquadra.dto.CancelPolicyDto;
import com.bookingsquadra.dto.CourtDto;
import com.bookingsquadra.dto.CreateCancelPolicyDto;
import com.bookingsquadra.dto.CreateCourtDto;
import com.bookingsquadra.dto.CreateOperatingHoursDto;
import com.bookingsquadra.dto.CreateVenueDto;
import com.bookingsquadra.dto.OperatingHoursDto;
import com.bookingsquadra.dto.UpdateCourtDto;
import com.bookingsquadra.dto.UpdateVenueDto;
import com.bookingsquadra.dto.VenueDto;
import com.bookingsquadra.entity.CancelPolicy;
import com.bookingsquadra.entity.City;
import com.bookingsquadra.entity.Court;
import com.bookingsquadra.entity.OperatingHours;
import com.bookingsquadra.entity.Venue;
import com.bookingsquadra.repository.CancelPolicyRepository;
import com.bookingsquadra.repository.CityRepository;
import com.bookingsquadra.repository.CourtRepository;
import com.bookingsquadra.repository.OperatingHoursRepository;
import com.bookingsquadra.repository.VenueRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class AdminVenueService {

    private static final short DEFAULT_SLOT_DURATION_MINUTES = 60;
    private static final int DEFAULT_PRICE_CENTS = 0;
    private static final short DEFAULT_SORT_ORDER = 0;

    private static final CreateCancelPolicyDto DEFAULT_CANCEL_POLICY = new CreateCancelPolicyDto(
            (short) 24, (short) 12, (short) 50, (short) 4, (short) 1
    );

    private final CityRepository cityRepository;
    private final VenueRepository venueRepository;
    private final CourtRepository courtRepository;
    private final OperatingHoursRepository operatingHoursRepository;
    private final CancelPolicyRepository cancelPolicyRepository;

    public AdminVenueService(
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

    @Transactional
    public VenueDto createVenue(CreateVenueDto dto) {
        validateNoDuplicateDays(dto.operatingHours());
        City city = cityRepository.findById(dto.cityId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNPROCESSABLE_CONTENT, "City not found"));

        Venue venue = Venue.builder()
                .name(dto.name())
                .slug(dto.slug())
                .description(dto.description() == null ? "" : dto.description())
                .imageUrl(dto.imageUrl())
                .address(dto.address())
                .cityId(dto.cityId())
                .latitude(dto.latitude())
                .longitude(dto.longitude())
                .sports(dto.sports() == null ? new String[0] : dto.sports().toArray(new String[0]))
                .amenities(dto.amenities() == null ? Map.of() : dto.amenities())
                .priceCents(dto.priceCents() == null ? DEFAULT_PRICE_CENTS : dto.priceCents())
                .slotDurationMinutes(dto.slotDurationMinutes() == null
                        ? DEFAULT_SLOT_DURATION_MINUTES
                        : dto.slotDurationMinutes())
                .active(true)
                .build();

        Venue saved;
        try {
            saved = venueRepository.saveAndFlush(venue);
        } catch (DataIntegrityViolationException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Slug already in use", e);
        }

        List<Court> createdCourts = new ArrayList<>();
        if (dto.courts() != null) {
            for (CreateCourtDto courtDto : dto.courts()) {
                createdCourts.add(courtRepository.save(buildCourt(saved.getId(), courtDto)));
            }
        }

        List<OperatingHours> createdHours = new ArrayList<>();
        for (CreateOperatingHoursDto hours : dto.operatingHours()) {
            createdHours.add(operatingHoursRepository.save(buildHours(saved.getId(), hours)));
        }

        CreateCancelPolicyDto policyDto = dto.cancelPolicy() == null
                ? DEFAULT_CANCEL_POLICY
                : dto.cancelPolicy();
        CancelPolicy savedPolicy = cancelPolicyRepository.save(buildPolicy(saved.getId(), policyDto));

        return toDto(saved, city, createdCourts, createdHours, savedPolicy);
    }

    @Transactional
    public CourtDto createCourt(UUID venueId, CreateCourtDto dto) {
        if (!venueRepository.existsById(venueId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Venue not found");
        }
        Court saved = courtRepository.save(buildCourt(venueId, dto));
        return toDto(saved);
    }

    @Transactional
    public VenueDto updateVenue(UUID venueId, UpdateVenueDto dto) {
        Venue venue = venueRepository.findById(venueId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Venue not found"));
        if (dto.name() != null) venue.setName(dto.name());
        if (dto.slug() != null) venue.setSlug(dto.slug());
        if (dto.description() != null) venue.setDescription(dto.description());
        if (dto.imageUrl() != null) venue.setImageUrl(dto.imageUrl());
        if (dto.address() != null) venue.setAddress(dto.address());
        if (dto.cityId() != null) {
            if (!cityRepository.existsById(dto.cityId())) {
                throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_CONTENT, "City not found");
            }
            venue.setCityId(dto.cityId());
        }
        if (dto.latitude() != null) venue.setLatitude(dto.latitude());
        if (dto.longitude() != null) venue.setLongitude(dto.longitude());
        if (dto.sports() != null) venue.setSports(dto.sports().toArray(new String[0]));
        if (dto.amenities() != null) venue.setAmenities(dto.amenities());
        if (dto.priceCents() != null) venue.setPriceCents(dto.priceCents());
        if (dto.slotDurationMinutes() != null) venue.setSlotDurationMinutes(dto.slotDurationMinutes());
        if (dto.active() != null) venue.setActive(dto.active());
        try {
            venueRepository.saveAndFlush(venue);
        } catch (DataIntegrityViolationException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Slug already in use", e);
        }
        return toVenueDto(venue);
    }

    @Transactional
    public void deactivateVenue(UUID venueId) {
        Venue venue = venueRepository.findById(venueId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Venue not found"));
        venue.setActive(false);
        venueRepository.save(venue);
    }

    @Transactional
    public CourtDto updateCourt(UUID courtId, UpdateCourtDto dto) {
        Court court = courtRepository.findById(courtId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Court not found"));
        if (dto.name() != null) court.setName(dto.name());
        if (dto.surfaceType() != null) court.setSurfaceType(dto.surfaceType());
        if (dto.indoor() != null) court.setIndoor(dto.indoor());
        if (dto.sortOrder() != null) court.setSortOrder(dto.sortOrder());
        if (dto.active() != null) court.setActive(dto.active());
        return toDto(courtRepository.save(court));
    }

    @Transactional
    public void deactivateCourt(UUID courtId) {
        Court court = courtRepository.findById(courtId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Court not found"));
        court.setActive(false);
        courtRepository.save(court);
    }

    @Transactional(readOnly = true)
    public List<OperatingHoursDto> listOperatingHours(UUID venueId) {
        if (!venueRepository.existsById(venueId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Venue not found");
        }
        return operatingHoursRepository.findByVenueId(venueId)
                .stream()
                .sorted((a, b) -> Short.compare(a.getDayOfWeek(), b.getDayOfWeek()))
                .map(AdminVenueService::toDto)
                .toList();
    }

    @Transactional
    public OperatingHoursDto upsertOperatingHours(UUID venueId, short dayOfWeek, CreateOperatingHoursDto dto) {
        if (!venueRepository.existsById(venueId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Venue not found");
        }
        if (dto.dayOfWeek() != dayOfWeek) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "dayOfWeek must match path");
        }
        OperatingHours hours = operatingHoursRepository
                .findByVenueIdAndDayOfWeek(venueId, dayOfWeek)
                .orElseGet(() -> OperatingHours.builder()
                        .venueId(venueId)
                        .dayOfWeek(dayOfWeek)
                        .build());
        hours.setOpenTime(dto.openTime());
        hours.setCloseTime(dto.closeTime());
        return toDto(operatingHoursRepository.save(hours));
    }

    @Transactional
    public void deleteOperatingHours(UUID venueId, short dayOfWeek) {
        OperatingHours hours = operatingHoursRepository
                .findByVenueIdAndDayOfWeek(venueId, dayOfWeek)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Operating hours not found"));
        operatingHoursRepository.delete(hours);
    }

    @Transactional(readOnly = true)
    public CancelPolicyDto getCancelPolicy(UUID venueId) {
        if (!venueRepository.existsById(venueId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Venue not found");
        }
        return cancelPolicyRepository.findByVenueId(venueId)
                .map(AdminVenueService::toDto)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Cancel policy not found"));
    }

    @Transactional
    public CancelPolicyDto updateCancelPolicy(UUID venueId, CreateCancelPolicyDto dto) {
        if (!venueRepository.existsById(venueId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Venue not found");
        }
        CancelPolicy policy = cancelPolicyRepository.findByVenueId(venueId)
                .orElseGet(() -> CancelPolicy.builder().venueId(venueId).build());
        policy.setPixFullRefundHours(dto.pixFullRefundHours());
        policy.setPixPartialRefundHours(dto.pixPartialRefundHours());
        policy.setPixPartialRefundPercent(dto.pixPartialRefundPercent());
        policy.setLocalCancelHours(dto.localCancelHours());
        policy.setNoShowPixThreshold(dto.noShowPixThreshold());
        return toDto(cancelPolicyRepository.save(policy));
    }

    private static void validateNoDuplicateDays(List<CreateOperatingHoursDto> hours) {
        Set<Short> seen = new HashSet<>();
        for (CreateOperatingHoursDto h : hours) {
            if (!seen.add(h.dayOfWeek())) {
                throw new ResponseStatusException(
                        HttpStatus.UNPROCESSABLE_CONTENT,
                        "Duplicate operating hours for day_of_week=" + h.dayOfWeek());
            }
        }
    }

    private VenueDto toVenueDto(Venue venue) {
        City city = cityRepository.findById(venue.getCityId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.INTERNAL_SERVER_ERROR, "Venue city not found"));
        List<Court> courts = courtRepository.findByVenueIdAndActiveTrueOrderBySortOrderAsc(venue.getId());
        List<OperatingHours> hours = operatingHoursRepository.findByVenueId(venue.getId())
                .stream()
                .sorted((a, b) -> Short.compare(a.getDayOfWeek(), b.getDayOfWeek()))
                .toList();
        CancelPolicy policy = cancelPolicyRepository.findByVenueId(venue.getId()).orElse(null);
        return toDto(venue, city, courts, hours, policy);
    }

    private Court buildCourt(UUID venueId, CreateCourtDto dto) {
        return Court.builder()
                .venueId(venueId)
                .name(dto.name())
                .surfaceType(dto.surfaceType())
                .indoor(dto.indoor() != null && dto.indoor())
                .sortOrder(dto.sortOrder() == null ? DEFAULT_SORT_ORDER : dto.sortOrder())
                .active(true)
                .build();
    }

    private OperatingHours buildHours(UUID venueId, CreateOperatingHoursDto dto) {
        return OperatingHours.builder()
                .venueId(venueId)
                .dayOfWeek(dto.dayOfWeek())
                .openTime(dto.openTime())
                .closeTime(dto.closeTime())
                .build();
    }

    private CancelPolicy buildPolicy(UUID venueId, CreateCancelPolicyDto dto) {
        return CancelPolicy.builder()
                .venueId(venueId)
                .pixFullRefundHours(dto.pixFullRefundHours())
                .pixPartialRefundHours(dto.pixPartialRefundHours())
                .pixPartialRefundPercent(dto.pixPartialRefundPercent())
                .localCancelHours(dto.localCancelHours())
                .noShowPixThreshold(dto.noShowPixThreshold())
                .build();
    }

    private static VenueDto toDto(
            Venue v,
            City city,
            List<Court> courts,
            List<OperatingHours> hours,
            CancelPolicy policy
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
                courts.stream().map(AdminVenueService::toDto).toList(),
                hours.stream().map(AdminVenueService::toDto).toList(),
                toDto(policy)
        );
    }

    private static CourtDto toDto(Court c) {
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

    private static OperatingHoursDto toDto(OperatingHours h) {
        return new OperatingHoursDto(h.getDayOfWeek(), h.getOpenTime(), h.getCloseTime());
    }

    private static CancelPolicyDto toDto(CancelPolicy p) {
        return new CancelPolicyDto(
                p.getPixFullRefundHours(),
                p.getPixPartialRefundHours(),
                p.getPixPartialRefundPercent(),
                p.getLocalCancelHours(),
                p.getNoShowPixThreshold()
        );
    }
}
