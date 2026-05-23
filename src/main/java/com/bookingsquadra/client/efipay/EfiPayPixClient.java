package com.bookingsquadra.client.efipay;

import com.bookingsquadra.config.EfiPayProperties;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;

/**
 * EfiPay PIX API client. Currently exposes immediate-charge creation
 * ({@code PUT /v2/cob/{txid}}). All requests carry the Bearer token issued by
 * {@link EfiPayTokenService}; mTLS is enforced by the underlying
 * {@code efiPayRestClient}.
 *
 * <p>Idempotency note: PIX uses the {@code txid} in the URL as the idempotency token by
 * design (BCB spec). EfiPay returns 409 {@code txid_duplicado} on collision rather than
 * replaying the original charge — callers must either retry with a fresh txid or fall back
 * to {@code GET /v2/cob/{txid}} to read the existing one.
 */
@Component
public class EfiPayPixClient {

    private static final Logger log = LoggerFactory.getLogger(EfiPayPixClient.class);
    private static final Pattern TXID_PATTERN = Pattern.compile("^[a-zA-Z0-9]{26,35}$");

    private final RestClient restClient;
    private final EfiPayTokenService tokenService;
    private final EfiPayProperties properties;
    private final ObjectMapper objectMapper;

    public EfiPayPixClient(RestClient efiPayRestClient,
                           EfiPayTokenService tokenService,
                           EfiPayProperties properties,
                           ObjectMapper objectMapper) {
        this.restClient = efiPayRestClient;
        this.tokenService = tokenService;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    /**
     * Creates (or replaces, per EfiPay's PUT semantics) an immediate PIX charge for the given
     * {@code txid}. The txid must match {@code ^[a-zA-Z0-9]{26,35}$} — validated client-side to
     * fail fast instead of paying for the mTLS round-trip on a malformed value.
     *
     * <p>If EfiPay rejects the call with 401, the cached access token is invalidated and the
     * request is retried once with a fresh token; a second 401 propagates as 502.
     *
     * @throws IllegalArgumentException when {@code txid} does not match the EfiPay contract,
     *         or when the configured PIX key is missing.
     * @throws EfiPayTxidDuplicadoException when EfiPay returns 409 {@code txid_duplicado}.
     * @throws ResponseStatusException with 502 BAD_GATEWAY for any other upstream failure.
     */
    public EfiPayPixChargeResponse createCharge(String txid, EfiPayPixChargeRequest request) {
        if (txid == null || !TXID_PATTERN.matcher(txid).matches()) {
            throw new IllegalArgumentException(
                    "EfiPay txid must match ^[a-zA-Z0-9]{26,35}$ (got: " + txid + ")");
        }
        if (!properties.isPixKeyConfigured()) {
            throw new IllegalArgumentException(
                    "app.payments.efipay.pix-key is not configured (EFIPAY_PIX_KEY)");
        }
        WirePayload payload = WirePayload.from(request, properties.pixKey());
        try {
            return putCharge(txid, payload);
        } catch (RestClientResponseException e) {
            if (e.getStatusCode().value() == HttpStatus.UNAUTHORIZED.value()) {
                log.info("EfiPay PUT /v2/cob/{} returned 401 — invalidating cached token and retrying once", txid);
                tokenService.invalidate();
                try {
                    return putCharge(txid, payload);
                } catch (RestClientResponseException retryFailure) {
                    throw mapResponseFailure(txid, retryFailure);
                } catch (RuntimeException retryFailure) {
                    return rethrowUnexpected(txid, retryFailure);
                }
            }
            throw mapResponseFailure(txid, e);
        } catch (ResponseStatusException e) {
            throw e;
        } catch (RuntimeException e) {
            return rethrowUnexpected(txid, e);
        }
    }

    private EfiPayPixChargeResponse putCharge(String txid, WirePayload payload) {
        String bearer = tokenService.getAccessToken();
        EfiPayPixChargeResponse response = restClient.put()
                .uri("/v2/cob/{txid}", txid)
                .header("Authorization", "Bearer " + bearer)
                .body(payload)
                .retrieve()
                .body(EfiPayPixChargeResponse.class);
        if (response == null) {
            log.warn("EfiPay PUT /v2/cob/{} returned empty body", txid);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "EfiPay returned empty charge response");
        }
        return response;
    }

    private RuntimeException mapResponseFailure(String txid, RestClientResponseException e) {
        String operation = "PUT /v2/cob/" + txid;
        HttpStatus status = HttpStatus.resolve(e.getStatusCode().value());
        String body = e.getResponseBodyAsString();
        ErrorEnvelope error = parseError(body);
        if (status == HttpStatus.CONFLICT && error != null && "txid_duplicado".equals(error.nome())) {
            log.info("EfiPay {} rejected: txid already in use", operation);
            return new EfiPayTxidDuplicadoException(txid,
                    error.mensagem() != null ? error.mensagem() : "txid já utilizado");
        }
        logResponseFailure(operation, status, body);
        return new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                "EfiPay rejected charge: " + truncate(body), e);
    }

    private EfiPayPixChargeResponse rethrowUnexpected(String txid, RuntimeException e) {
        log.warn("EfiPay PUT /v2/cob/{} threw {}", txid, e.toString());
        throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                "EfiPay charge request failed", e);
    }

    private ErrorEnvelope parseError(String body) {
        if (body == null || body.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(body, ErrorEnvelope.class);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static void logResponseFailure(String operation, HttpStatus status, String body) {
        if (status != null && status.is4xxClientError() && status != HttpStatus.TOO_MANY_REQUESTS) {
            log.error("EFIPAY_UNEXPECTED_4XX op=\"{}\" status={} body={}",
                    operation, status.value(), truncate(body));
            return;
        }
        log.warn("EfiPay {} failed: status={} body={}", operation, status, truncate(body));
    }

    private static String truncate(String s) {
        if (s == null) {
            return "";
        }
        return s.length() > 500 ? s.substring(0, 500) + "..." : s;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ErrorEnvelope(String nome, String mensagem) {
    }

    /**
     * Wire shape sent to EfiPay — same as {@link EfiPayPixChargeRequest} plus the merchant
     * {@code chave} pulled from configuration. Kept private so the merchant key never leaks
     * into the caller-facing API surface.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private record WirePayload(
            EfiPayPixChargeRequest.Calendario calendario,
            EfiPayPixChargeRequest.Devedor devedor,
            EfiPayPixChargeRequest.Valor valor,
            String chave,
            String solicitacaoPagador,
            List<EfiPayPixChargeRequest.InfoAdicional> infoAdicionais,
            EfiPayPixChargeRequest.Loc loc
    ) {
        static WirePayload from(EfiPayPixChargeRequest request, String chave) {
            return new WirePayload(
                    request.calendario(),
                    request.devedor(),
                    request.valor(),
                    chave,
                    request.solicitacaoPagador(),
                    request.infoAdicionais(),
                    request.loc()
            );
        }
    }
}
