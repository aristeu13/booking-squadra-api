package com.bookingsquadra.client.asaas;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;

@JsonIgnoreProperties(ignoreUnknown = true)
public record AsaasSplitResponse(
        String id,
        String walletId,
        BigDecimal fixedValue,
        BigDecimal percentualValue,
        BigDecimal totalValue,
        String status
) {}
