package com.bookingsquadra.dto;

import java.util.UUID;

public record CourtDto(
        UUID id,
        UUID venueId,
        String name,
        String surfaceType,
        Boolean indoor,
        Short sortOrder,
        Boolean active
) {}
