package com.bookingsquadra.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record VenuePayoutDto(
        UUID id,
        UUID bookingId,
        UUID venueId,
        Integer amountCents,
        String pixKey,
        String pixKeyType,
        OffsetDateTime scheduledFor,
        String status,
        String asaasTransferId,
        OffsetDateTime sentAt,
        String failureReason,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {}
