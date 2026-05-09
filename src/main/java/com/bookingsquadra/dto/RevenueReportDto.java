package com.bookingsquadra.dto;

import java.time.LocalDate;
import java.util.UUID;

public record RevenueReportDto(
        UUID venueId,
        LocalDate from,
        LocalDate to,
        long paidCount,
        long grossCents,
        long refundedCents,
        long netCents
) {}
