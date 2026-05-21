package com.bookingsquadra.controller;

import com.bookingsquadra.dto.MarkPayoutSettledDto;
import com.bookingsquadra.dto.VenuePayoutDto;
import com.bookingsquadra.service.VenuePayoutService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/payouts")
public class AdminPayoutController {

    private final VenuePayoutService venuePayoutService;

    public AdminPayoutController(VenuePayoutService venuePayoutService) {
        this.venuePayoutService = venuePayoutService;
    }

    @GetMapping
    public List<VenuePayoutDto> list(@RequestParam(required = false) String status) {
        return venuePayoutService.listByStatus(status);
    }

    @GetMapping("/{payoutId}")
    public VenuePayoutDto get(@PathVariable UUID payoutId) {
        return venuePayoutService.get(payoutId);
    }

    @PostMapping("/{payoutId}/reconcile")
    public VenuePayoutDto reconcile(@PathVariable UUID payoutId) {
        return venuePayoutService.reconcile(payoutId);
    }

    @PostMapping("/{payoutId}/mark-settled")
    public VenuePayoutDto markSettled(
            @PathVariable UUID payoutId,
            @RequestBody(required = false) MarkPayoutSettledDto body
    ) {
        return venuePayoutService.markSettled(payoutId, body);
    }
}
