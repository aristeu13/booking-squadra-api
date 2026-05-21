package com.bookingsquadra.client.asaas;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.math.BigDecimal;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record AsaasTransferRequest(
        BigDecimal value,
        String pixAddressKey,
        String pixAddressKeyType,
        String operationType,
        String description,
        String externalReference
) {}
