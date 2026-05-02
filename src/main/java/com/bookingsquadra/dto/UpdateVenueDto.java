package com.bookingsquadra.dto;

import com.bookingsquadra.entity.Amenity;
import com.bookingsquadra.entity.Sport;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.util.List;

public record UpdateVenueDto(
        String name,
        @Size(max = 200) String slug,
        String description,
        String imageUrl,
        String address,
        Integer cityId,
        @DecimalMin("-90") @DecimalMax("90") Double latitude,
        @DecimalMin("-180") @DecimalMax("180") Double longitude,
        List<Sport> sports,
        List<Amenity> amenities,
        @PositiveOrZero Integer priceCents,
        @Positive Short slotDurationMinutes,
        Boolean active
) {}
