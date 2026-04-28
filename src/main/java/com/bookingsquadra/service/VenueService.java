package com.bookingsquadra.service;

import com.bookingsquadra.dto.BookingCountDto;
import com.bookingsquadra.dto.VenueResponseDto;
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

    private final VenueRepository venueRepository;

    public VenueService(VenueRepository venueRepository) {
        this.venueRepository = venueRepository;
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
    public BookingCountDto countBookings(UUID venueId) {
        if (!venueRepository.existsById(venueId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Venue not found");
        }
        return new BookingCountDto(venueId, venueRepository.countBookingsByVenueId(venueId));
    }

    private static VenueResponseDto toDto(VenueDistanceProjection p) {
        String[] sports = p.getSports();
        return new VenueResponseDto(
                p.getId(),
                p.getName(),
                p.getDescription(),
                p.getImageUrl(),
                p.getAddress(),
                p.getCity(),
                p.getStateCode(),
                sports == null ? Collections.emptyList() : List.of(sports),
                p.getAmenities(),
                p.getPriceCents(),
                p.getDistanceKm()
        );
    }
}
