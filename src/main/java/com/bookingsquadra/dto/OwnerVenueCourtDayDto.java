package com.bookingsquadra.dto;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Per-court day timeline for the owner dashboard: schedule window, recurring blocks
 * resolved for the date, and bookings (with customer and payment snapshot).
 */
public record OwnerVenueCourtDayDto(
        UUID venueId,
        LocalDate date,
        String timezone,
        List<Court> courts
) {

    public record Court(
            UUID id,
            String name,
            String type,
            boolean isIndoor,
            Schedule schedule,
            List<Block> blocks,
            List<Reservation> reservations
    ) {}

    public record Schedule(Integer openHour, Integer closeHour) {}

    public record Block(
            UUID id,
            OffsetDateTime startsAt,
            OffsetDateTime endsAt,
            String reason
    ) {}

    public record Reservation(
            UUID id,
            OffsetDateTime startsAt,
            OffsetDateTime endsAt,
            String sport,
            String status,
            String paymentStatus,
            Integer amountCents,
            String source,
            Customer customer,
            String notes
    ) {}

    public record Customer(String name, String phone) {}
}
