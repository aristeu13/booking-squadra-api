package com.bookingsquadra.dto;

import jakarta.validation.constraints.NotBlank;

public record RefreshTokenDto(
        @NotBlank String refreshToken
) {}
