package com.bookingsquadra.dto;

import java.util.UUID;

public record ProfileDto(
        UUID id,
        String name,
        String email,
        String phone,
        Boolean googleAuth
) {}
