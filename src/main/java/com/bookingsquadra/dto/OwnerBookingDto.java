package com.bookingsquadra.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record OwnerBookingDto(
        UUID id,
        OffsetDateTime startsAt,
        OffsetDateTime endsAt,
        String timezone,
        String status,
        String bookingType,
        Integer amountCents,
        String paymentMethod,
        String paymentStatus,
        UUID courtId,
        String courtName,
        UUID userId,
        String userName,
        String userEmail,
        String userPhone
) {}
