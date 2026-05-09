package com.bookingsquadra.dto;

import java.util.UUID;

public record OwnerVenueSummaryDto(
        UUID id,
        String name,
        String slug,
        String address,
        String imageUrl,
        Boolean active,
        Long courtCount
) {}
