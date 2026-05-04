package com.bookingsquadra.dto;

import java.time.OffsetDateTime;

public record PixCodeDto(
        String encodedImage,
        String payload,
        OffsetDateTime expiresAt
) {}
