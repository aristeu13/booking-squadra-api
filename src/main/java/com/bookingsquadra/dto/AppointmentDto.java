package com.bookingsquadra.dto;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.UUID;

public record AppointmentDto(
        UUID id,
        String status,
        String bookingType,
        OffsetDateTime startsAt,
        OffsetDateTime endsAt,
        String timezone,
        LocalDate bookingDate,
        LocalTime startTime,
        LocalTime endTime,
        Integer amountCents,
        String paymentMethod,
        UUID venueId,
        String venueName,
        String venueSlug,
        String venueAddress,
        String city,
        String stateCode,
        UUID courtId,
        String courtName,
        String courtSurfaceType,
        String note,
        OffsetDateTime expiresAt
) {}
