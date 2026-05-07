package com.bookingsquadra.controller;

import com.bookingsquadra.config.PaymentWebhookProperties;
import com.bookingsquadra.service.PaymentWebhookService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/payments-webhook")
public class PaymentWebhookController {

    private static final String ASAAS_ACCESS_TOKEN_HEADER = "asaas-access-token";

    private final PaymentWebhookProperties paymentWebhookProperties;
    private final PaymentWebhookService paymentWebhookService;

    public PaymentWebhookController(
            PaymentWebhookProperties paymentWebhookProperties,
            PaymentWebhookService paymentWebhookService
    ) {
        this.paymentWebhookProperties = paymentWebhookProperties;
        this.paymentWebhookService = paymentWebhookService;
    }

    @PostMapping(consumes = "application/json")
    public ResponseEntity<Map<String, Boolean>> handleWebhook(
            @RequestHeader HttpHeaders headers,
            @RequestBody Map<String, Object> body
    ) {
        validateToken(headers);

        String eventId = body.get("id") instanceof String value ? value : null;
        String event = body.get("event") instanceof String value ? value : null;
        Object paymentValue = body.get("payment");
        Map<?, ?> payment = paymentValue instanceof Map<?, ?> value ? value : null;

        paymentWebhookService.handle(eventId, event, payment);

        return ResponseEntity.ok(Map.of("received", true));
    }

    private void validateToken(HttpHeaders headers) {
        String configuredToken = paymentWebhookProperties.token();
        if (configuredToken == null || configuredToken.isBlank()) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Payment webhook token is not configured");
        }

        String providedToken = headers.getFirst(ASAAS_ACCESS_TOKEN_HEADER);

        if (!constantTimeEquals(configuredToken, providedToken)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid webhook token");
        }
    }

    private static boolean constantTimeEquals(String expected, String actual) {
        if (actual == null) {
            return false;
        }
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                actual.getBytes(StandardCharsets.UTF_8)
        );
    }
}
