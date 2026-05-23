package com.bookingsquadra.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.payments.efipay")
public record EfiPayProperties(
        String baseUrl,
        String clientId,
        String clientSecret,
        String certificatePem,
        String privateKeyPem,
        String pixKey
) {

    public boolean isCredentialsConfigured() {
        return hasText(clientId) && hasText(clientSecret);
    }

    public boolean isMtlsConfigured() {
        return hasText(certificatePem) && hasText(privateKeyPem);
    }

    public boolean isPixKeyConfigured() {
        return hasText(pixKey);
    }

    public boolean isFullyConfigured() {
        return hasText(baseUrl) && isCredentialsConfigured() && isMtlsConfigured() && isPixKeyConfigured();
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
