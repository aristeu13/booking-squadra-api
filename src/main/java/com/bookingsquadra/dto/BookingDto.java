package com.bookingsquadra.dto;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

public record BookingDto(
        UUID id,
        UUID userId,
        UUID courtId,
        LocalDate bookingDate,
        LocalTime startTime,
        LocalTime endTime,
        String status,
        String bookingType,
        String note
) {}
