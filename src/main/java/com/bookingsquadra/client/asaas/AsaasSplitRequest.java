package com.bookingsquadra.client.asaas;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.math.BigDecimal;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record AsaasSplitRequest(
        String walletId,
        BigDecimal percentualValue,
        BigDecimal fixedValue
) {}
