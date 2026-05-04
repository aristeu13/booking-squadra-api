package com.bookingsquadra.client.asaas;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record AsaasCustomerResponse(
        String id,
        String name,
        String cpfCnpj,
        String externalReference
) {}
