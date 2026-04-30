package com.bookingsquadra.dto;

public record AuthTokenDto(
        String accessToken,
        String refreshToken
) {}
