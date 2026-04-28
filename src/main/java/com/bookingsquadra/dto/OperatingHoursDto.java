package com.bookingsquadra.dto;

import java.time.LocalTime;

public record OperatingHoursDto(
        Short dayOfWeek,
        LocalTime openTime,
        LocalTime closeTime
) {}
