package com.bookingsquadra.dto;

import java.time.LocalTime;

public record AvailableSlotDto(
        LocalTime start,
        LocalTime end
) {}
