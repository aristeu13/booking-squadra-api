package com.bookingsquadra.service;

import com.bookingsquadra.config.WhatsAppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

@Service
public class OtpWhatsAppSender {

    private static final Logger log = LoggerFactory.getLogger(OtpWhatsAppSender.class);

    private final RestClient restClient;
    private final WhatsAppProperties props;

    public OtpWhatsAppSender(RestClient whatsAppRestClient, WhatsAppProperties props) {
        this.restClient = whatsAppRestClient;
        this.props = props;
    }

    /**
     * Sends an OTP code via WhatsApp Cloud API template message.
     *
     * <p>Assumes the configured template is body-only with a single text parameter ({{1}} = code).
     * Authentication-category templates with a one-tap copy button need an additional button
     * component in the payload — extend this method once the production template is approved.
     */
    public void sendLoginOtp(String phoneE164, String code) {
        if (!props.isConfigured()) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "WhatsApp OTP delivery is not configured");
        }

        Map<String, Object> payload = Map.of(
                "messaging_product", "whatsapp",
                "to", stripPlus(phoneE164),
                "type", "template",
                "template", Map.of(
                        "name", props.otpTemplateName(),
                        "language", Map.of("code", props.otpTemplateLanguage()),
                        "components", List.of(
                                Map.of(
                                        "type", "body",
                                        "parameters", List.of(
                                                Map.of("type", "text", "text", code)
                                        )
                                )
                        )
                )
        );

        try {
            restClient.post()
                    .uri("/messages")
                    .body(payload)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientResponseException e) {
            log.warn("WhatsApp template send failed status={} body={}",
                    e.getStatusCode(), truncate(e.getResponseBodyAsString()));
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY, "Failed to deliver OTP via WhatsApp", e);
        } catch (RuntimeException e) {
            log.warn("WhatsApp template send threw {}", e.toString());
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY, "Failed to deliver OTP via WhatsApp", e);
        }
    }

    private static String stripPlus(String phoneE164) {
        return phoneE164.startsWith("+") ? phoneE164.substring(1) : phoneE164;
    }

    private static String truncate(String body) {
        if (body == null) {
            return "";
        }
        return body.length() > 500 ? body.substring(0, 500) + "…" : body;
    }
}
