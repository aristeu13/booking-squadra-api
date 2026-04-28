package com.bookingsquadra.dto;

import jakarta.validation.constraints.NotBlank;

public record DeleteAccountDto(
        @NotBlank String code
) {}
