package com.bookingsquadra.dto;

import com.bookingsquadra.entity.Amenity;
import com.bookingsquadra.entity.Sport;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.util.List;

public record CreateVenueDto(
        @NotBlank String name,
        @NotBlank @Size(max = 200) String slug,
        String description,
        String imageUrl,
        @NotBlank String address,
        @NotNull Integer cityId,
        @NotNull @DecimalMin("-90") @DecimalMax("90") Double latitude,
        @NotNull @DecimalMin("-180") @DecimalMax("180") Double longitude,
        List<Sport> sports,
        List<Amenity> amenities,
        @PositiveOrZero Integer priceCents,
        @Positive Short slotDurationMinutes,
        @Valid List<CreateCourtDto> courts,
        @NotEmpty @Valid List<CreateOperatingHoursDto> operatingHours,
        @Valid CreateCancelPolicyDto cancelPolicy
) {}
