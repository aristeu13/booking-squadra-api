package com.bookingsquadra.controller;

import com.bookingsquadra.dto.BookingCountDto;
import com.bookingsquadra.dto.VenueResponseDto;
import com.bookingsquadra.service.VenueService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/venues")
public class VenueController {

    private final VenueService venueService;

    public VenueController(VenueService venueService) {
        this.venueService = venueService;
    }

    @GetMapping
    public List<VenueResponseDto> list(
            @RequestParam(required = false) Double lat,
            @RequestParam(required = false) Double lon,
            @RequestParam(name = "distance_km", required = false) Double distanceKm,
            @RequestParam(name = "sports_filters", required = false) List<String> sportsFilters
    ) {
        return venueService.search(lat, lon, distanceKm, sportsFilters);
    }

    @GetMapping("/{id}/bookings/count")
    public BookingCountDto bookingsCount(@PathVariable UUID id) {
        return venueService.countBookings(id);
    }
}
