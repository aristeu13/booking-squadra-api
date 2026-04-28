package com.bookingsquadra.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.mailgun")
public record MailgunProperties(
        String apiKey,
        String domain,
        String from,
        String baseUrl,
        String otpTemplate
) {}
