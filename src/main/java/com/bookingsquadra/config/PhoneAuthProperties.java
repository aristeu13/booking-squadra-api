package com.bookingsquadra.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.auth.phone-login")
public record PhoneAuthProperties(
        boolean enabled
) {}
