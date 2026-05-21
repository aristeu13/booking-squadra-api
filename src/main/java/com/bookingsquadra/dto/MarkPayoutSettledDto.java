package com.bookingsquadra.dto;

public record MarkPayoutSettledDto(
        String manualTransferReference,
        String note
) {}
