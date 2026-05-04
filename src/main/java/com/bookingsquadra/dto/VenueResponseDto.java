package com.bookingsquadra.dto;

import com.bookingsquadra.entity.Amenity;
import com.bookingsquadra.entity.Sport;

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
        List<Sport> sports,
        List<Amenity> amenities,
        Integer priceCents,
        Double distanceKm,
        Integer numberOfCourts
) {}
