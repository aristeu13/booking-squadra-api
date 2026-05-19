package com.bookingsquadra.controller;

import com.bookingsquadra.dto.OwnerDashboardSummaryDto;
import com.bookingsquadra.service.OwnerDashboardService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/owner/dashboard")
public class OwnerDashboardController {

    private final OwnerDashboardService ownerDashboardService;

    public OwnerDashboardController(OwnerDashboardService ownerDashboardService) {
        this.ownerDashboardService = ownerDashboardService;
    }

    @GetMapping("/summary")
    public OwnerDashboardSummaryDto summary(
            @RequestParam(name = "venueId", required = false) UUID venueId,
            @RequestParam(name = "date", required = false) String date,
            @RequestParam(name = "tz", required = false) String tz
    ) {
        return ownerDashboardService.getSummary(venueId, date, tz);
    }
}
