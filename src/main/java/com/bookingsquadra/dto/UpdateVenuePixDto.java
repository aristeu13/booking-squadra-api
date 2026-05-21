package com.bookingsquadra.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record UpdateVenuePixDto(
        @NotBlank String pixKey,
        @NotBlank
        @Pattern(regexp = "CPF|CNPJ|EMAIL|PHONE|EVP",
                message = "pixKeyType must be one of CPF, CNPJ, EMAIL, PHONE, EVP")
        String pixKeyType
) {}
