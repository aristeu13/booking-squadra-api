package com.bookingsquadra.dto;

import jakarta.validation.constraints.Size;

public record CancelBookingRequestDto(
        @Size(max = 1000) String reason
) {}
