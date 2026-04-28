package com.bookingsquadra.controller;

import com.bookingsquadra.dto.CourtDto;
import com.bookingsquadra.dto.CreateCourtDto;
import com.bookingsquadra.dto.CreateVenueDto;
import com.bookingsquadra.dto.VenueDto;
import com.bookingsquadra.service.AdminVenueService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/admin/venues")
public class AdminVenueController {

    private final AdminVenueService adminVenueService;

    public AdminVenueController(AdminVenueService adminVenueService) {
        this.adminVenueService = adminVenueService;
    }

    @PostMapping
    public ResponseEntity<VenueDto> createVenue(@Valid @RequestBody CreateVenueDto body) {
        VenueDto created = adminVenueService.createVenue(body);
        return ResponseEntity.created(URI.create("/admin/venues/" + created.id())).body(created);
    }

    @PostMapping("/{venueId}/courts")
    public ResponseEntity<CourtDto> createCourt(
            @PathVariable UUID venueId,
            @Valid @RequestBody CreateCourtDto body
    ) {
        CourtDto created = adminVenueService.createCourt(venueId, body);
        return ResponseEntity.created(URI.create("/courts/" + created.id())).body(created);
    }
}
