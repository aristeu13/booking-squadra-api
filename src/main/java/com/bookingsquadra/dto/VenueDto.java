package com.bookingsquadra.dto;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public record VenueDto(
        UUID id,
        String name,
        String slug,
        String description,
        String imageUrl,
        String address,
        String city,
        String stateCode,
        Double latitude,
        Double longitude,
        List<String> sports,
        Map<String, Object> amenities,
        Integer priceCents,
        Short slotDurationMinutes,
        Boolean active,
        List<CourtDto> courts,
        List<OperatingHoursDto> operatingHours,
        CancelPolicyDto cancelPolicy
) {}
