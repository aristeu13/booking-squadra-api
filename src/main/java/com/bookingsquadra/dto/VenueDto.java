package com.bookingsquadra.dto;

import com.bookingsquadra.entity.Amenity;
import com.bookingsquadra.entity.Sport;

import java.util.List;
import java.util.UUID;

public record VenueDto(
        UUID id,
        String name,
        String slug,
        String description,
        String imageUrl,
        String address,
        Integer cityId,
        String city,
        String stateCode,
        String timezone,
        Double latitude,
        Double longitude,
        List<Sport> sports,
        List<Amenity> amenities,
        Integer priceCents,
        Short slotDurationMinutes,
        Boolean active,
        List<CourtDto> courts,
        List<OperatingHoursDto> operatingHours,
        CancelPolicyDto cancelPolicy
) {}
