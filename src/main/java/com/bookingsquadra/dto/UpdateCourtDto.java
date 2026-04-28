package com.bookingsquadra.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;

public record UpdateCourtDto(
        String name,
        @Pattern(regexp = "sand|synthetic|hard|clay|padel|wood|grass",
                message = "must be one of: sand, synthetic, hard, clay, padel, wood, grass")
        String surfaceType,
        Boolean indoor,
        @PositiveOrZero Short sortOrder,
        Boolean active
) {}
