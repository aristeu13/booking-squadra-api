package com.bookingsquadra.controller;

import com.bookingsquadra.dto.BookingDto;
import com.bookingsquadra.dto.CourtDto;
import com.bookingsquadra.dto.CreateOwnerBookingDto;
import com.bookingsquadra.dto.OwnerBookingDto;
import com.bookingsquadra.dto.OwnerDashboardSummaryDto;
import com.bookingsquadra.dto.OwnerVenueCourtDayDto;
import com.bookingsquadra.dto.OwnerVenueDayOverviewDto;
import com.bookingsquadra.dto.OwnerVenueSummaryDto;
import com.bookingsquadra.service.OwnerDashboardService;
import com.bookingsquadra.service.OwnerVenueService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/owner/venues")
public class OwnerVenueController {

    private final OwnerVenueService ownerVenueService;
    private final OwnerDashboardService ownerDashboardService;

    public OwnerVenueController(
            OwnerVenueService ownerVenueService,
            OwnerDashboardService ownerDashboardService
    ) {
        this.ownerVenueService = ownerVenueService;
        this.ownerDashboardService = ownerDashboardService;
    }

    @GetMapping
    public List<OwnerVenueSummaryDto> listOwned() {
        return ownerVenueService.listOwnedVenues();
    }

    @GetMapping("/{venueId}/courts")
    @PreAuthorize("@venueAccess.canManage(#venueId)")
    public List<CourtDto> courts(@PathVariable UUID venueId) {
        return ownerVenueService.listVenueCourts(venueId);
    }

    @GetMapping("/{venueId}/dashboard-summary")
    @PreAuthorize("@venueAccess.canManage(#venueId)")
    public OwnerDashboardSummaryDto dashboardSummary(
            @PathVariable UUID venueId,
            @RequestParam(name = "date", required = false) String date
    ) {
        return ownerDashboardService.getSummary(venueId, date);
    }

    @GetMapping("/{venueId}/day-overview")
    @PreAuthorize("@venueAccess.canManage(#venueId)")
    public OwnerVenueDayOverviewDto dayOverview(
            @PathVariable UUID venueId,
            @RequestParam String date
    ) {
        return ownerVenueService.getDayOverview(venueId, date);
    }

    @GetMapping("/{venueId}/courts/{courtId}/day")
    @PreAuthorize("@venueAccess.canManage(#venueId)")
    public OwnerVenueCourtDayDto courtDay(
            @PathVariable UUID venueId,
            @PathVariable UUID courtId,
            @RequestParam String date
    ) {
        return ownerVenueService.getCourtDay(venueId, courtId, date);
    }

    @PostMapping("/{venueId}/bookings")
    @PreAuthorize("@venueAccess.canManage(#venueId)")
    public ResponseEntity<BookingDto> createManualBooking(
            @PathVariable UUID venueId,
            @Valid @RequestBody CreateOwnerBookingDto body
    ) {
        BookingDto created = ownerVenueService.createManualBooking(venueId, body);
        return ResponseEntity.status(201).body(created);
    }

    @PostMapping("/{venueId}/bookings/{bookingId}/cancel")
    @PreAuthorize("@venueAccess.canManage(#venueId)")
    public ResponseEntity<Void> cancelManualBooking(
            @PathVariable UUID venueId,
            @PathVariable UUID bookingId
    ) {
        ownerVenueService.cancelManualBooking(venueId, bookingId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{venueId}/bookings/{bookingId}/no-show")
    @PreAuthorize("@venueAccess.canManage(#venueId)")
    public ResponseEntity<Void> markNoShow(
            @PathVariable UUID venueId,
            @PathVariable UUID bookingId
    ) {
        ownerVenueService.markBookingNoShow(venueId, bookingId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{venueId}/bookings")
    @PreAuthorize("@venueAccess.canManage(#venueId)")
    public Page<OwnerBookingDto> bookings(
            @PathVariable UUID venueId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(name = "page_size", defaultValue = "20") int pageSize
    ) {
        return ownerVenueService.listVenueBookings(venueId, from, to, status, page, pageSize);
    }
}
