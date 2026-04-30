package com.bookingsquadra.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.auth.google")
public record GoogleAuthProperties(
        String clientId
) {}
