package com.bookingsquadra.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public record CreateCancelPolicyDto(
        @NotNull @PositiveOrZero Short pixFullRefundHours,
        @NotNull @PositiveOrZero Short pixPartialRefundHours,
        @NotNull @Min(0) @Max(100) Short pixPartialRefundPercent,
        @NotNull @PositiveOrZero Short localCancelHours,
        @NotNull @PositiveOrZero Short noShowPixThreshold
) {}
