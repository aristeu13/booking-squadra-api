package com.bookingsquadra.dto;

import java.time.OffsetDateTime;

public record HealthDto(
        String status,
        OffsetDateTime timestamp
) {}
