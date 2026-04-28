package com.bookingsquadra.dto;

public record CancelPolicyDto(
        Short pixFullRefundHours,
        Short pixPartialRefundHours,
        Short pixPartialRefundPercent,
        Short localCancelHours,
        Short noShowPixThreshold
) {}
