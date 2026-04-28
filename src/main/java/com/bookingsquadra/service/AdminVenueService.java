package com.bookingsquadra.service;

import com.bookingsquadra.dto.CourtDto;
import com.bookingsquadra.dto.CreateCourtDto;
import com.bookingsquadra.dto.CreateVenueDto;
import com.bookingsquadra.dto.VenueDto;
import com.bookingsquadra.entity.Court;
import com.bookingsquadra.entity.Venue;
import com.bookingsquadra.repository.CourtRepository;
import com.bookingsquadra.repository.VenueRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class AdminVenueService {

    private static final short DEFAULT_SLOT_DURATION_MINUTES = 60;
    private static final int DEFAULT_PRICE_CENTS = 0;
    private static final short DEFAULT_SORT_ORDER = 0;

    private final VenueRepository venueRepository;
    private final CourtRepository courtRepository;

    public AdminVenueService(VenueRepository venueRepository, CourtRepository courtRepository) {
        this.venueRepository = venueRepository;
        this.courtRepository = courtRepository;
    }

    @Transactional
    public VenueDto createVenue(CreateVenueDto dto) {
        Venue venue = Venue.builder()
                .name(dto.name())
                .slug(dto.slug())
                .description(dto.description() == null ? "" : dto.description())
                .imageUrl(dto.imageUrl())
                .address(dto.address())
                .city(dto.city())
                .stateCode(dto.stateCode())
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

        return toDto(saved, createdCourts);
    }

    @Transactional
    public CourtDto createCourt(UUID venueId, CreateCourtDto dto) {
        if (!venueRepository.existsById(venueId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Venue not found");
        }
        Court saved = courtRepository.save(buildCourt(venueId, dto));
        return toDto(saved);
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

    private static VenueDto toDto(Venue v, List<Court> courts) {
        return new VenueDto(
                v.getId(),
                v.getName(),
                v.getSlug(),
                v.getDescription(),
                v.getImageUrl(),
                v.getAddress(),
                v.getCity(),
                v.getStateCode(),
                v.getLatitude(),
                v.getLongitude(),
                v.getSports() == null ? Collections.emptyList() : List.of(v.getSports()),
                v.getAmenities(),
                v.getPriceCents(),
                v.getSlotDurationMinutes(),
                v.getActive(),
                courts.stream().map(AdminVenueService::toDto).toList()
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
}
