package com.bookingsquadra.dto;

import jakarta.validation.constraints.NotBlank;

public record UpdateVenueWalletDto(
        @NotBlank String asaasWalletId
) {}
