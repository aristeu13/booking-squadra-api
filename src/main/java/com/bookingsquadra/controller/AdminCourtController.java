package com.bookingsquadra.controller;

import com.bookingsquadra.dto.CourtDto;
import com.bookingsquadra.dto.UpdateCourtDto;
import com.bookingsquadra.service.AdminVenueService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/courts")
public class AdminCourtController {

    private final AdminVenueService adminVenueService;

    public AdminCourtController(AdminVenueService adminVenueService) {
        this.adminVenueService = adminVenueService;
    }

    @PatchMapping("/{id}")
    public CourtDto updateCourt(@PathVariable UUID id, @Valid @RequestBody UpdateCourtDto body) {
        return adminVenueService.updateCourt(id, body);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deactivateCourt(@PathVariable UUID id) {
        adminVenueService.deactivateCourt(id);
        return ResponseEntity.noContent().build();
    }
}
