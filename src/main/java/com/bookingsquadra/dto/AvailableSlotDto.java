package com.bookingsquadra.dto;

import java.time.LocalDate;
import java.util.List;

public record AvailableSlotDto(
        LocalDate date,
        String timezone,
        Short slotDurationMinutes,
        List<String> slots
) {}
