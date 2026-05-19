package com.bookingsquadra.controller;

import com.bookingsquadra.dto.OwnerBookingDto;
import com.bookingsquadra.dto.OwnerVenueCourtDayDto;
import com.bookingsquadra.dto.OwnerVenueDayOverviewDto;
import com.bookingsquadra.dto.OwnerVenueSummaryDto;
import com.bookingsquadra.service.OwnerVenueService;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
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

    public OwnerVenueController(OwnerVenueService ownerVenueService) {
        this.ownerVenueService = ownerVenueService;
    }

    @GetMapping
    public List<OwnerVenueSummaryDto> listOwned() {
        return ownerVenueService.listOwnedVenues();
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
