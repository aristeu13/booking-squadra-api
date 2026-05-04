package com.bookingsquadra.dto;

import java.util.UUID;

public record RefundResponseDto(
        UUID bookingId,
        String paymentId,
        Integer refundPercent,
        Integer grossRefundCents,
        Integer feeCents,
        Integer netRefundCents,
        String status,
        String message
) {}
