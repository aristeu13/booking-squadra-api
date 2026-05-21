package com.bookingsquadra.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.auth.test-otp")
public record TestOtpProperties(
        boolean enabled,
        String email,
        String phone,
        String code
) {

    public boolean matchesEmail(String candidate) {
        return enabled && hasText(email) && email.equalsIgnoreCase(candidate);
    }

    public boolean matchesPhone(String candidate) {
        return enabled && hasText(phone) && phone.equals(candidate);
    }

    public boolean matchesCode(String candidate) {
        return enabled && hasText(code) && code.equals(candidate);
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
