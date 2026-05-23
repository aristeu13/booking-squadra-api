package com.bookingsquadra.client.efipay;

import com.bookingsquadra.config.EfiPayProperties;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;

@Component
public class EfiPayAuthClient {

    private static final Logger log = LoggerFactory.getLogger(EfiPayAuthClient.class);

    private final RestClient restClient;
    private final EfiPayProperties properties;

    public EfiPayAuthClient(RestClient efiPayRestClient, EfiPayProperties properties) {
        this.restClient = efiPayRestClient;
        this.properties = properties;
    }

    public EfiPayTokenResponse requestClientCredentialsToken() {
        if (!properties.isCredentialsConfigured()) {
            throw new IllegalStateException("EfiPay client-id/client-secret are not configured");
        }
        String basic = Base64.getEncoder().encodeToString(
                (properties.clientId() + ":" + properties.clientSecret()).getBytes(StandardCharsets.UTF_8));

        try {
            EfiPayTokenResponse response = restClient.post()
                    .uri("/oauth/token")
                    .header("Authorization", "Basic " + basic)
                    .body(Map.of("grant_type", "client_credentials"))
                    .retrieve()
                    .body(EfiPayTokenResponse.class);
            if (response == null || response.accessToken() == null || response.accessToken().isBlank()) {
                log.warn("EfiPay POST /oauth/token returned empty access_token");
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                        "EfiPay token response missing access_token");
            }
            return response;
        } catch (RestClientResponseException e) {
            HttpStatus status = HttpStatus.resolve(e.getStatusCode().value());
            String body = truncate(e.getResponseBodyAsString());
            log.warn("EfiPay POST /oauth/token failed: status={} body={}", status, body);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "EfiPay rejected token request: " + body, e);
        } catch (ResponseStatusException e) {
            throw e;
        } catch (RuntimeException e) {
            log.warn("EfiPay POST /oauth/token threw {}", e.toString());
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "EfiPay token request failed", e);
        }
    }

    private static String truncate(String s) {
        if (s == null) {
            return "";
        }
        return s.length() > 500 ? s.substring(0, 500) + "..." : s;
    }
}
