package com.bookingsquadra.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record CheckoutRequestDto(
        @NotNull UUID bookingId,
        String name,
        String cpfCnpj
) {}
