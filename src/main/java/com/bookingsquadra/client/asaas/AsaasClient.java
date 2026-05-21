package com.bookingsquadra.client.asaas;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;

@Component
public class AsaasClient {

    private static final Logger log = LoggerFactory.getLogger(AsaasClient.class);

    private final RestClient restClient;

    public AsaasClient(RestClient asaasRestClient) {
        this.restClient = asaasRestClient;
    }

    public AsaasCustomerResponse createCustomer(AsaasCustomerRequest request) {
        return execute("POST /customers", () -> restClient.post()
                .uri("/customers")
                .body(request)
                .retrieve()
                .body(AsaasCustomerResponse.class));
    }

    public AsaasPaymentResponse createPayment(AsaasPaymentRequest request, String idempotencyKey) {
        return execute("POST /payments", () -> restClient.post()
                .uri("/payments")
                .header("X-Idempotency-Key", idempotencyKey)
                .body(request)
                .retrieve()
                .body(AsaasPaymentResponse.class));
    }

    public void deletePayment(String paymentId) {
        String operation = "DELETE /payments/" + paymentId;
        try {
            restClient.delete()
                    .uri("/payments/{id}", paymentId)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientResponseException e) {
            String body = e.getResponseBodyAsString();
            HttpStatus status = HttpStatus.resolve(e.getStatusCode().value());
            if (status == HttpStatus.BAD_REQUEST && isNotDeletableBody(body)) {
                log.info("Asaas DELETE /payments/{} rejected: charge is no longer pending", paymentId);
                throw new AsaasPaymentNotDeletableException(
                        "Asaas refused to delete payment " + paymentId + ": " + truncate(body));
            }
            logResponseFailure(operation, status, body);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "Asaas rejected request: " + truncate(body), e);
        } catch (RuntimeException e) {
            log.warn("Asaas {} threw {}", operation, e.toString());
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "Asaas request failed", e);
        }
    }

    private static boolean isNotDeletableBody(String body) {
        if (body == null) {
            return false;
        }
        return body.contains("invalid_action") && body.contains("não pode ser removida");
    }

    public AsaasPixCodeResponse getPixQrCode(String paymentId) {
        return execute("GET /payments/" + paymentId + "/pixQrCode", () -> restClient.get()
                .uri("/payments/{id}/pixQrCode", paymentId)
                .retrieve()
                .body(AsaasPixCodeResponse.class));
    }

    public AsaasPaymentResponse refundPayment(String paymentId, AsaasRefundRequest request) {
        return execute("POST /payments/" + paymentId + "/refund", () -> restClient.post()
                .uri("/payments/{id}/refund", paymentId)
                .body(request)
                .retrieve()
                .body(AsaasPaymentResponse.class));
    }

    public AsaasTransferResponse createTransfer(AsaasTransferRequest request, String idempotencyKey) {
        return execute("POST /transfers", () -> restClient.post()
                .uri("/transfers")
                .header("X-Idempotency-Key", idempotencyKey)
                .body(request)
                .retrieve()
                .body(AsaasTransferResponse.class));
    }

    private static <T> T execute(String operation, AsaasCall<T> call) {
        try {
            return call.run();
        } catch (RestClientResponseException e) {
            HttpStatus status = HttpStatus.resolve(e.getStatusCode().value());
            String body = e.getResponseBodyAsString();
            logResponseFailure(operation, status, body);
            if (status != null && status.is4xxClientError()) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                        "Asaas rejected request: " + truncate(body), e);
            }
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "Asaas request failed", e);
        } catch (RuntimeException e) {
            log.warn("Asaas {} threw {}", operation, e.toString());
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "Asaas request failed", e);
        }
    }

    /**
     * Logs Asaas response failures so observability tooling can route alerts.
     * <ul>
     *   <li>Unexpected 4xx (excluding 429) → {@code ASAAS_UNEXPECTED_4XX} at ERROR — bug or config issue.</li>
     *   <li>429 / 5xx / unknown → WARN — transient; alert on aggregate rate, not per request.</li>
     * </ul>
     */
    private static void logResponseFailure(String operation, HttpStatus status, String body) {
        if (status != null && status.is4xxClientError() && status != HttpStatus.TOO_MANY_REQUESTS) {
            log.error("ASAAS_UNEXPECTED_4XX op=\"{}\" status={} body={}",
                    operation, status.value(), truncate(body));
            return;
        }
        log.warn("Asaas {} failed: status={} body={}", operation, status, truncate(body));
    }

    private static String truncate(String s) {
        if (s == null) {
            return "";
        }
        return s.length() > 500 ? s.substring(0, 500) + "..." : s;
    }

    @FunctionalInterface
    private interface AsaasCall<T> {
        T run();
    }
}
