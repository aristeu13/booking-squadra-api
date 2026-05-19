package com.bookingsquadra.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

public record OwnerDashboardSummaryDto(
        UUID venueId,
        LocalDate date,
        String timezone,
        OffsetDateTime generatedAt,
        Occupancy occupancy,
        Revenue revenue
) {

    public record Occupancy(
            double rate,
            long bookedMinutes,
            long availableMinutes,
            OccupancyCompare compare
    ) {}

    public record OccupancyCompare(
            String period,
            double rate,
            @JsonInclude(JsonInclude.Include.NON_NULL) Double deltaPct,
            String direction
    ) {}

    public record Revenue(
            String currency,
            long confirmedCents,
            long pendingCents,
            RevenueCompare compare
    ) {}

    public record RevenueCompare(
            String period,
            long confirmedCents,
            @JsonInclude(JsonInclude.Include.NON_NULL) Double deltaPct,
            String direction
    ) {}
}
