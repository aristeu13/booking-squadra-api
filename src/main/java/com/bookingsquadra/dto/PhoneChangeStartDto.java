package com.bookingsquadra.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PhoneChangeStartDto(
        @NotBlank @Size(max = 32) String phone
) {}
