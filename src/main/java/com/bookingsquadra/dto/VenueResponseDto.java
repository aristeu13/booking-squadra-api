package com.bookingsquadra.dto;

import java.util.List;
import java.util.UUID;

public record VenueResponseDto(
        UUID id,
        String slug,
        String name,
        String description,
        String imageUrl,
        String address,
        String city,
        String stateCode,
        List<String> sports,
        String amenities,
        Integer priceCents,
        Double distanceKm
) {}
