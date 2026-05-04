package com.bookingsquadra.client.asaas;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record AsaasPixCodeResponse(
        Boolean success,
        String encodedImage,
        String payload,
        String expirationDate
) {}
