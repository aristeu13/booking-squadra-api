package com.bookingsquadra.controller;

import com.bookingsquadra.dto.CancelPolicyDto;
import com.bookingsquadra.dto.CourtDto;
import com.bookingsquadra.dto.CreateCancelPolicyDto;
import com.bookingsquadra.dto.CreateCourtDto;
import com.bookingsquadra.dto.CreateOperatingHoursDto;
import com.bookingsquadra.dto.CreateVenueDto;
import com.bookingsquadra.dto.OperatingHoursDto;
import com.bookingsquadra.dto.UpdateVenueDto;
import com.bookingsquadra.dto.VenueDto;
import com.bookingsquadra.service.AdminVenueService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;
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

    @PatchMapping("/{id}")
    public VenueDto updateVenue(@PathVariable UUID id, @Valid @RequestBody UpdateVenueDto body) {
        return adminVenueService.updateVenue(id, body);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deactivateVenue(@PathVariable UUID id) {
        adminVenueService.deactivateVenue(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{venueId}/operating-hours")
    public List<OperatingHoursDto> operatingHours(@PathVariable UUID venueId) {
        return adminVenueService.listOperatingHours(venueId);
    }

    @PutMapping("/{venueId}/operating-hours/{dayOfWeek}")
    public OperatingHoursDto upsertOperatingHours(
            @PathVariable UUID venueId,
            @PathVariable short dayOfWeek,
            @Valid @RequestBody CreateOperatingHoursDto body
    ) {
        return adminVenueService.upsertOperatingHours(venueId, dayOfWeek, body);
    }

    @DeleteMapping("/{venueId}/operating-hours/{dayOfWeek}")
    public ResponseEntity<Void> deleteOperatingHours(
            @PathVariable UUID venueId,
            @PathVariable short dayOfWeek
    ) {
        adminVenueService.deleteOperatingHours(venueId, dayOfWeek);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{venueId}/cancel-policy")
    public CancelPolicyDto cancelPolicy(@PathVariable UUID venueId) {
        return adminVenueService.getCancelPolicy(venueId);
    }

    @PutMapping("/{venueId}/cancel-policy")
    public CancelPolicyDto updateCancelPolicy(
            @PathVariable UUID venueId,
            @Valid @RequestBody CreateCancelPolicyDto body
    ) {
        return adminVenueService.updateCancelPolicy(venueId, body);
    }
}
