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
        execute("DELETE /payments/" + paymentId, () -> restClient.delete()
                .uri("/payments/{id}", paymentId)
                .retrieve()
                .toBodilessEntity());
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

    private static <T> T execute(String operation, AsaasCall<T> call) {
        try {
            return call.run();
        } catch (RestClientResponseException e) {
            HttpStatus status = HttpStatus.resolve(e.getStatusCode().value());
            String body = e.getResponseBodyAsString();
            log.warn("Asaas {} failed: status={} body={}", operation, status, body);
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
