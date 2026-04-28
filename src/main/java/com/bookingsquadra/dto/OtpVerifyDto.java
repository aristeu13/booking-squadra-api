package com.bookingsquadra.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record OtpVerifyDto(
        @NotBlank @Email String email,
        @NotBlank String code
) {}
