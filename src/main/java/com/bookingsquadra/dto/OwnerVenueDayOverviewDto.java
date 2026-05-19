package com.bookingsquadra.dto;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Snapshot of a single day at a venue for the owner dashboard.
 *
 *  - {@code reservations.count}     — active bookings (pending|confirmed) that overlap the day
 *  - {@code reservations.capacity}  — total bookable slots in the day across active courts
 *  - {@code nextAvailableSlot}      — earliest still-free slot from now (or start of day for future
 *                                     dates). {@code null} when nothing is bookable for the rest
 *                                     of the day.
 */
public record OwnerVenueDayOverviewDto(
        UUID venueId,
        LocalDate date,
        String timezone,
        Reservations reservations,
        NextAvailableSlot nextAvailableSlot
) {

    public record Reservations(long count, long capacity) {}

    public record NextAvailableSlot(
            UUID courtId,
            String courtName,
            OffsetDateTime startsAt,
            OffsetDateTime endsAt
    ) {}
}
