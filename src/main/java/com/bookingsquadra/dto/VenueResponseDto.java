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
        Integer cityId,
        String city,
        String stateCode,
        String timezone,
        List<String> sports,
        String amenities,
        Integer priceCents,
        Double distanceKm
) {}
