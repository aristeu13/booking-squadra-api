package com.bookingsquadra.dto;

import java.util.UUID;

public record LocalPaymentEligibilityDto(
        UUID venueId,
        int threshold,
        long successfulCount,
        boolean eligible
) {}
