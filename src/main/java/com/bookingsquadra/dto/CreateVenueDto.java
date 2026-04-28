package com.bookingsquadra.dto;

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
import java.util.Map;

public record CreateVenueDto(
        @NotBlank String name,
        @NotBlank @Size(max = 200) String slug,
        String description,
        String imageUrl,
        @NotBlank String address,
        @NotBlank String city,
        @NotBlank @Size(min = 2, max = 2) String stateCode,
        Integer cityId,
        @NotNull @DecimalMin("-90") @DecimalMax("90") Double latitude,
        @NotNull @DecimalMin("-180") @DecimalMax("180") Double longitude,
        List<String> sports,
        Map<String, Object> amenities,
        @PositiveOrZero Integer priceCents,
        @Positive Short slotDurationMinutes,
        @Valid List<CreateCourtDto> courts,
        @NotEmpty @Valid List<CreateOperatingHoursDto> operatingHours,
        @Valid CreateCancelPolicyDto cancelPolicy
) {}
