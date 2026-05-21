package com.bookingsquadra.dto;

import jakarta.validation.constraints.Size;

public record UpdateProfileDto(
        @Size(max = 255) String name,
        @Size(max = 14) String cpf
) {}
