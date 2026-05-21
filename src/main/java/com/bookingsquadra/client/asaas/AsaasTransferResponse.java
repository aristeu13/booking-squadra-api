package com.bookingsquadra.client.asaas;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;

@JsonIgnoreProperties(ignoreUnknown = true)
public record AsaasTransferResponse(
        String id,
        BigDecimal value,
        BigDecimal netValue,
        String status,
        String operationType,
        String dateCreated,
        String externalReference
) {}
