package com.bookingsquadra.dto;

import java.time.LocalDate;

public record LegalTermsDto(
        String version,
        String title,
        String content,
        LocalDate updatedAt
) {}
