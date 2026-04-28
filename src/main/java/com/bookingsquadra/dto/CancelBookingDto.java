package com.bookingsquadra.dto;

import java.util.UUID;

public record CancelBookingDto(
        UUID bookingId,
        Boolean cancelled,
        Integer refundPercent,
        Integer refundAmountCents,
        String paymentImpact,
        String message
) {}
