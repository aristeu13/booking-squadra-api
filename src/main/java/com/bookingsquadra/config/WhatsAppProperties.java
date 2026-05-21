package com.bookingsquadra.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.whatsapp")
public record WhatsAppProperties(
        String baseUrl,
        String phoneNumberId,
        String accessToken,
        String otpTemplateName,
        String otpTemplateLanguage
) {

    public boolean isConfigured() {
        return hasText(baseUrl)
                && hasText(phoneNumberId)
                && hasText(accessToken)
                && hasText(otpTemplateName)
                && hasText(otpTemplateLanguage);
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
