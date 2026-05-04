package com.bookingsquadra.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record CheckoutResponseDto(
        UUID bookingId,
        String paymentId,
        String status,
        Integer amountCents,
        String invoiceUrl,
        OffsetDateTime expiresAt
) {}
