package com.bookingsquadra.client.asaas;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.math.BigDecimal;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record AsaasSplitRefund(
        String id,
        BigDecimal value
) {}
