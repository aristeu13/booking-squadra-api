package com.bookingsquadra.client.asaas;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record AsaasPaymentResponse(
        String id,
        String customer,
        String billingType,
        String status,
        BigDecimal value,
        BigDecimal netValue,
        String dueDate,
        String invoiceUrl,
        String externalReference,
        List<AsaasSplitResponse> split
) {}
