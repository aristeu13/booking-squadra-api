package com.bookingsquadra.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.time.LocalTime;

public record CreateOperatingHoursDto(
        @NotNull @Min(0) @Max(6) Short dayOfWeek,
        @NotNull LocalTime openTime,
        @NotNull LocalTime closeTime
) {}
