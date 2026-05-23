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
 * EfiPay PIX API client. Exposes immediate-charge creation ({@code PUT /v2/cob/{txid}}),
 * partial/full refund requests ({@code PUT /v2/pix/{e2eId}/devolucao/{id}}), PIX lookup
 * ({@code GET /v2/pix/{e2eId}}), refund lookup ({@code GET /v2/pix/{e2eId}/devolucao/{id}}),
 * and location QR code lookup ({@code GET /v2/loc/{id}/qrcode}). All requests carry the
 * Bearer token issued by {@link EfiPayTokenService}; mTLS is enforced by the underlying
 * {@code efiPayRestClient}.
 *
 * <p>Idempotency note: PIX uses the path identifiers ({@code txid}, {@code refundId}) as
 * the idempotency token by design (BCB spec). EfiPay returns 409 on collision rather than
 * replaying the original operation — callers either retry with a fresh id or read the
 * existing resource via the corresponding GET endpoint.
 */
@Component
public class EfiPayPixClient {

    private static final Logger log = LoggerFactory.getLogger(EfiPayPixClient.class);
    private static final Pattern TXID_PATTERN = Pattern.compile("^[a-zA-Z0-9]{26,35}$");
    private static final Pattern E2EID_PATTERN = Pattern.compile("^[a-zA-Z0-9]{32}$");
    private static final Pattern REFUND_ID_PATTERN = Pattern.compile("^[a-zA-Z0-9]{1,35}$");

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
        ChargeWirePayload payload = ChargeWirePayload.from(request, properties.pixKey());
        String operation = "PUT /v2/cob/" + txid;
        return executeWithTokenRetry(operation,
                () -> putCharge(txid, payload),
                (op, e) -> mapChargeFailure(op, txid, e));
    }

    /**
     * Initiates a refund (devolução) for a previously received PIX. The amount may be partial
     * (sum across all devoluções of the same e2eId must stay ≤ the original valor).
     *
     * <p>The {@code refundId} is a caller-supplied idempotency key — submitting the same id
     * twice produces {@link EfiPayDevolucaoDuplicadoException}. The {@code e2eId} must be the
     * canonical {@code endToEndId} captured from the original payment's webhook.
     *
     * @throws IllegalArgumentException when either identifier fails its regex check.
     * @throws EfiPayPixNaoEncontradoException when EfiPay returns 400 {@code pix_nao_encontrado}.
     * @throws EfiPayDevolucaoDuplicadoException when EfiPay returns 409 {@code devolucao_id_duplicado}.
     * @throws ResponseStatusException with 502 BAD_GATEWAY for any other upstream failure.
     */
    public EfiPayPixRefundResponse requestRefund(String e2eId,
                                                 String refundId,
                                                 EfiPayPixRefundRequest request) {
        if (e2eId == null || !E2EID_PATTERN.matcher(e2eId).matches()) {
            throw new IllegalArgumentException(
                    "EfiPay e2eId must match ^[a-zA-Z0-9]{32}$ (got: " + e2eId + ")");
        }
        if (refundId == null || !REFUND_ID_PATTERN.matcher(refundId).matches()) {
            throw new IllegalArgumentException(
                    "EfiPay refundId must match ^[a-zA-Z0-9]{1,35}$ (got: " + refundId + ")");
        }
        String operation = "PUT /v2/pix/" + e2eId + "/devolucao/" + refundId;
        return executeWithTokenRetry(operation,
                () -> putRefund(e2eId, refundId, request),
                (op, e) -> mapRefundFailure(op, e2eId, refundId, e));
    }

    /**
     * Looks up the original PIX plus its devoluções by {@code endToEndId}. When
     * {@code exibirCodigoBanco} is true, EfiPay includes {@code pagador.contaBanco.codigoBanco}
     * in the response (otherwise that block is omitted).
     *
     * @throws IllegalArgumentException when {@code e2eId} does not match the EfiPay contract.
     * @throws EfiPayPixNaoEncontradoException when EfiPay returns 400 {@code pix_nao_encontrado}.
     * @throws ResponseStatusException with 502 BAD_GATEWAY for any other upstream failure.
     */
    public EfiPayPixDetailsResponse getPix(String e2eId, boolean exibirCodigoBanco) {
        if (e2eId == null || !E2EID_PATTERN.matcher(e2eId).matches()) {
            throw new IllegalArgumentException(
                    "EfiPay e2eId must match ^[a-zA-Z0-9]{32}$ (got: " + e2eId + ")");
        }
        String operation = "GET /v2/pix/" + e2eId;
        return executeWithTokenRetry(operation,
                () -> fetchPix(e2eId, exibirCodigoBanco),
                (op, e) -> mapPixLookupFailure(op, e2eId, e));
    }

    /**
     * Fetches the QR code payload (BR Code string, embedded SVG image, and EfiPay-hosted
     * visualization URL) for a previously-created location. The {@code locationId} is the
     * value returned in {@link EfiPayPixChargeResponse.Loc#id()} when a charge was created
     * with a {@code loc} attachment.
     *
     * @throws IllegalArgumentException when {@code locationId} is null or non-positive.
     * @throws EfiPayLocationNaoEncontradaException when EfiPay returns {@code location_nao_encontrada}.
     * @throws ResponseStatusException with 502 BAD_GATEWAY for any other upstream failure.
     */
    public EfiPayLocationQrCodeResponse getLocationQrCode(Long locationId) {
        if (locationId == null || locationId <= 0) {
            throw new IllegalArgumentException(
                    "EfiPay locationId must be a positive long (got: " + locationId + ")");
        }
        String operation = "GET /v2/loc/" + locationId + "/qrcode";
        return executeWithTokenRetry(operation,
                () -> fetchLocationQrCode(locationId),
                (op, e) -> mapLocationQrCodeFailure(op, locationId, e));
    }

    private EfiPayLocationQrCodeResponse fetchLocationQrCode(Long locationId) {
        String bearer = tokenService.getAccessToken();
        EfiPayLocationQrCodeResponse response = restClient.get()
                .uri("/v2/loc/{id}/qrcode", locationId)
                .header("Authorization", "Bearer " + bearer)
                .retrieve()
                .body(EfiPayLocationQrCodeResponse.class);
        if (response == null) {
            log.warn("EfiPay GET /v2/loc/{}/qrcode returned empty body", locationId);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "EfiPay returned empty location QR code response");
        }
        return response;
    }

    /**
     * Looks up a single devolução by {@code (e2eId, refundId)}. Useful for polling a refund
     * that was created via {@link #requestRefund} until it reaches a terminal state
     * ({@code DEVOLVIDO} or {@code NAO_REALIZADO}) when the webhook hasn't arrived yet.
     *
     * @throws IllegalArgumentException when either identifier fails its regex check.
     * @throws EfiPayPixNaoEncontradoException when EfiPay returns 400 {@code pix_nao_encontrado}.
     * @throws EfiPayDevolucaoNaoEncontradaException when EfiPay returns 400 {@code devolucao_nao_encontrada}.
     * @throws ResponseStatusException with 502 BAD_GATEWAY for any other upstream failure.
     */
    public EfiPayPixRefundResponse getRefund(String e2eId, String refundId) {
        if (e2eId == null || !E2EID_PATTERN.matcher(e2eId).matches()) {
            throw new IllegalArgumentException(
                    "EfiPay e2eId must match ^[a-zA-Z0-9]{32}$ (got: " + e2eId + ")");
        }
        if (refundId == null || !REFUND_ID_PATTERN.matcher(refundId).matches()) {
            throw new IllegalArgumentException(
                    "EfiPay refundId must match ^[a-zA-Z0-9]{1,35}$ (got: " + refundId + ")");
        }
        String operation = "GET /v2/pix/" + e2eId + "/devolucao/" + refundId;
        return executeWithTokenRetry(operation,
                () -> fetchRefund(e2eId, refundId),
                (op, e) -> mapRefundLookupFailure(op, e2eId, refundId, e));
    }

    private EfiPayPixRefundResponse fetchRefund(String e2eId, String refundId) {
        String bearer = tokenService.getAccessToken();
        EfiPayPixRefundResponse response = restClient.get()
                .uri("/v2/pix/{e2eId}/devolucao/{id}", e2eId, refundId)
                .header("Authorization", "Bearer " + bearer)
                .retrieve()
                .body(EfiPayPixRefundResponse.class);
        if (response == null) {
            log.warn("EfiPay GET /v2/pix/{}/devolucao/{} returned empty body", e2eId, refundId);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "EfiPay returned empty refund lookup response");
        }
        return response;
    }

    private EfiPayPixDetailsResponse fetchPix(String e2eId, boolean exibirCodigoBanco) {
        String bearer = tokenService.getAccessToken();
        EfiPayPixDetailsResponse response = restClient.get()
                .uri(uriBuilder -> {
                    uriBuilder.path("/v2/pix/{e2eId}");
                    if (exibirCodigoBanco) {
                        uriBuilder.queryParam("exibirCodigoBanco", "true");
                    }
                    return uriBuilder.build(e2eId);
                })
                .header("Authorization", "Bearer " + bearer)
                .retrieve()
                .body(EfiPayPixDetailsResponse.class);
        if (response == null) {
            log.warn("EfiPay GET /v2/pix/{} returned empty body", e2eId);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "EfiPay returned empty PIX lookup response");
        }
        return response;
    }

    private EfiPayPixChargeResponse putCharge(String txid, ChargeWirePayload payload) {
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

    private EfiPayPixRefundResponse putRefund(String e2eId,
                                              String refundId,
                                              EfiPayPixRefundRequest request) {
        String bearer = tokenService.getAccessToken();
        EfiPayPixRefundResponse response = restClient.put()
                .uri("/v2/pix/{e2eId}/devolucao/{id}", e2eId, refundId)
                .header("Authorization", "Bearer " + bearer)
                .body(request)
                .retrieve()
                .body(EfiPayPixRefundResponse.class);
        if (response == null) {
            log.warn("EfiPay PUT /v2/pix/{}/devolucao/{} returned empty body", e2eId, refundId);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "EfiPay returned empty refund response");
        }
        return response;
    }

    private RuntimeException mapChargeFailure(String operation, String txid,
                                              RestClientResponseException e) {
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

    private RuntimeException mapLocationQrCodeFailure(String operation, Long locationId,
                                                      RestClientResponseException e) {
        HttpStatus status = HttpStatus.resolve(e.getStatusCode().value());
        String body = e.getResponseBodyAsString();
        ErrorEnvelope error = parseError(body);
        // EfiPay's docs don't pin a status code for this error, so we match on `nome` only —
        // `nome` is the canonical identifier in their error envelope.
        if (error != null && "location_nao_encontrada".equals(error.nome())) {
            log.info("EfiPay {} rejected: location not found", operation);
            return new EfiPayLocationNaoEncontradaException(locationId,
                    error.mensagem() != null ? error.mensagem() : "location não encontrada");
        }
        logResponseFailure(operation, status, body);
        return new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                "EfiPay rejected location QR code lookup: " + truncate(body), e);
    }

    private RuntimeException mapRefundLookupFailure(String operation, String e2eId, String refundId,
                                                    RestClientResponseException e) {
        HttpStatus status = HttpStatus.resolve(e.getStatusCode().value());
        String body = e.getResponseBodyAsString();
        ErrorEnvelope error = parseError(body);
        if (status == HttpStatus.BAD_REQUEST && error != null) {
            if ("devolucao_nao_encontrada".equals(error.nome())) {
                log.info("EfiPay {} rejected: no refund found for (e2eId, refundId)", operation);
                return new EfiPayDevolucaoNaoEncontradaException(e2eId, refundId,
                        error.mensagem() != null ? error.mensagem() : "devolução não encontrada");
            }
            if ("pix_nao_encontrado".equals(error.nome())) {
                log.info("EfiPay {} rejected: no PIX found for e2eId", operation);
                return new EfiPayPixNaoEncontradoException(e2eId,
                        error.mensagem() != null ? error.mensagem() : "PIX não encontrado");
            }
        }
        logResponseFailure(operation, status, body);
        return new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                "EfiPay rejected refund lookup: " + truncate(body), e);
    }

    private RuntimeException mapPixLookupFailure(String operation, String e2eId,
                                                 RestClientResponseException e) {
        HttpStatus status = HttpStatus.resolve(e.getStatusCode().value());
        String body = e.getResponseBodyAsString();
        ErrorEnvelope error = parseError(body);
        if (status == HttpStatus.BAD_REQUEST && error != null
                && "pix_nao_encontrado".equals(error.nome())) {
            log.info("EfiPay {} rejected: no PIX found for e2eId", operation);
            return new EfiPayPixNaoEncontradoException(e2eId,
                    error.mensagem() != null ? error.mensagem() : "PIX não encontrado");
        }
        logResponseFailure(operation, status, body);
        return new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                "EfiPay rejected PIX lookup: " + truncate(body), e);
    }

    private RuntimeException mapRefundFailure(String operation, String e2eId, String refundId,
                                              RestClientResponseException e) {
        HttpStatus status = HttpStatus.resolve(e.getStatusCode().value());
        String body = e.getResponseBodyAsString();
        ErrorEnvelope error = parseError(body);
        if (error != null) {
            if (status == HttpStatus.BAD_REQUEST && "pix_nao_encontrado".equals(error.nome())) {
                log.info("EfiPay {} rejected: no PIX found for e2eId", operation);
                return new EfiPayPixNaoEncontradoException(e2eId,
                        error.mensagem() != null ? error.mensagem() : "PIX não encontrado");
            }
            if (status == HttpStatus.CONFLICT && "devolucao_id_duplicado".equals(error.nome())) {
                log.info("EfiPay {} rejected: refund id already in use", operation);
                return new EfiPayDevolucaoDuplicadoException(e2eId, refundId,
                        error.mensagem() != null ? error.mensagem() : "devolução duplicada");
            }
        }
        logResponseFailure(operation, status, body);
        return new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                "EfiPay rejected refund: " + truncate(body), e);
    }

    /**
     * Shared upstream-call wrapper. On 401 it invalidates the cached token and retries once
     * (covers token revocation, clock skew, secret rotation). Other failures are routed
     * through {@code mapper} so each public method can attach its own typed exceptions for
     * known business errors before falling through to a generic 502.
     */
    private <T> T executeWithTokenRetry(String operation,
                                        UpstreamCall<T> call,
                                        ResponseErrorMapper mapper) {
        try {
            return call.run();
        } catch (RestClientResponseException e) {
            if (e.getStatusCode().value() == HttpStatus.UNAUTHORIZED.value()) {
                log.info("EfiPay {} returned 401 — invalidating cached token and retrying once",
                        operation);
                tokenService.invalidate();
                try {
                    return call.run();
                } catch (RestClientResponseException retry) {
                    throw mapper.map(operation, retry);
                } catch (ResponseStatusException retry) {
                    throw retry;
                } catch (RuntimeException retry) {
                    throw wrapUnexpected(operation, retry);
                }
            }
            throw mapper.map(operation, e);
        } catch (ResponseStatusException e) {
            throw e;
        } catch (RuntimeException e) {
            throw wrapUnexpected(operation, e);
        }
    }

    private static ResponseStatusException wrapUnexpected(String operation, RuntimeException e) {
        log.warn("EfiPay {} threw {}", operation, e.toString());
        return new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                "EfiPay request failed", e);
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
     * Wire shape sent to EfiPay for charge creation — same as {@link EfiPayPixChargeRequest}
     * plus the merchant {@code chave} pulled from configuration. Kept private so the merchant
     * key never leaks into the caller-facing API surface.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private record ChargeWirePayload(
            EfiPayPixChargeRequest.Calendario calendario,
            EfiPayPixChargeRequest.Devedor devedor,
            EfiPayPixChargeRequest.Valor valor,
            String chave,
            String solicitacaoPagador,
            List<EfiPayPixChargeRequest.InfoAdicional> infoAdicionais,
            EfiPayPixChargeRequest.Loc loc
    ) {
        static ChargeWirePayload from(EfiPayPixChargeRequest request, String chave) {
            return new ChargeWirePayload(
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

    @FunctionalInterface
    private interface UpstreamCall<T> {
        T run();
    }

    @FunctionalInterface
    private interface ResponseErrorMapper {
        RuntimeException map(String operation, RestClientResponseException e);
    }
}
