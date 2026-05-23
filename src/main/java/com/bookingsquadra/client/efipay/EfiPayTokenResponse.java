package com.bookingsquadra.client.efipay;

import com.fasterxml.jackson.annotation.JsonProperty;

public record EfiPayTokenResponse(
        @JsonProperty("access_token") String accessToken,
        @JsonProperty("token_type") String tokenType,
        @JsonProperty("expires_in") Long expiresIn,
        String scope
) {
}
