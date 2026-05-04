package com.bookingsquadra.client.asaas;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.math.BigDecimal;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record AsaasRefundRequest(
        BigDecimal value,
        String description,
        List<AsaasSplitRefund> splitRefunds
) {}
