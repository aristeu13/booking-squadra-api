package com.bookingsquadra.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;

public record CreateCourtDto(
        @NotBlank String name,
        @NotBlank
        @Pattern(regexp = "sand|synthetic|hard|clay|padel|wood|grass",
                message = "must be one of: sand, synthetic, hard, clay, padel, wood, grass")
        String surfaceType,
        Boolean indoor,
        @PositiveOrZero Short sortOrder
) {}
