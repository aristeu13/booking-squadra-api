package com.bookingsquadra.client.asaas;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record AsaasCustomerRequest(
        String name,
        String cpfCnpj,
        String externalReference
) {}
