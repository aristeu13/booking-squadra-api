package com.bookingsquadra.controller;

import com.bookingsquadra.dto.BookingCountDto;
import com.bookingsquadra.dto.CancelPolicyDto;
import com.bookingsquadra.dto.VenueDto;
import com.bookingsquadra.dto.VenueResponseDto;
import com.bookingsquadra.entity.Amenity;
import com.bookingsquadra.entity.Sport;
import com.bookingsquadra.service.VenueService;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/venues")
public class VenueController {

    private final VenueService venueService;

    public VenueController(VenueService venueService) {
        this.venueService = venueService;
    }

    @GetMapping
    public Page<VenueResponseDto> list(
            @RequestParam(required = false) Double lat,
            @RequestParam(required = false) Double lon,
            @RequestParam(name = "distance_km", required = false) Double distanceKm,
            @RequestParam(name = "sports_filters", required = false) List<Sport> sportsFilters,
            @RequestParam(name = "amenities_filters", required = false) List<Amenity> amenitiesFilters,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(name = "page_size", defaultValue = "20") int pageSize
    ) {
        return venueService.search(lat, lon, distanceKm, sportsFilters, amenitiesFilters, page, pageSize);
    }

    @GetMapping("/{id}")
    public VenueDto getById(@PathVariable UUID id) {
        return venueService.getById(id);
    }

    @GetMapping("/{id}/bookings/count")
    public BookingCountDto bookingsCount(@PathVariable UUID id) {
        return venueService.countBookings(id);
    }

    @GetMapping("/{id}/cancel-policy")
    public CancelPolicyDto cancelPolicy(@PathVariable UUID id) {
        return venueService.getCancelPolicy(id);
    }
}
