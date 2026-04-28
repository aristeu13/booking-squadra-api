package com.bookingsquadra.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.payments.webhook")
public record PaymentWebhookProperties(
        String token
) {}
