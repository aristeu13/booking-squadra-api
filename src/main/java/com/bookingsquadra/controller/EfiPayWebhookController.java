package com.bookingsquadra.controller;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Receives EfiPay PIX webhook callbacks. EfiPay posts every event type to the same URL
 * (received, refunded, payout) so this controller dispatches by inspecting which keys are
 * present on each item of the {@code pix} array.
 *
 * <p>The path ends in {@code /pix} because EfiPay's webhook registration handshake appends
 * that suffix to the URL it tests, and the production callback always lands on it.
 *
 */
@RestController
@RequestMapping("/api/v1/efipay-webhook")
public class EfiPayWebhookController {

    private static final Logger log = LoggerFactory.getLogger(EfiPayWebhookController.class);
    private static final Map<String, String> OK_RESPONSE = Map.of("status", "ok");

    @PostMapping(path = "/pix", consumes = "application/json")
    public ResponseEntity<Map<String, String>> handlePixEvents(@RequestBody JsonNode body) {
        JsonNode pix = body.path("pix");
        if (!pix.isArray() || pix.isEmpty()) {
            log.warn("EfiPay webhook payload missing or empty 'pix' array: {}", body);
            return ResponseEntity.ok(OK_RESPONSE);
        }

        for (JsonNode event : pix) {
            try {
                dispatch(event);
            } catch (RuntimeException e) {
                // Never fail the response back to EfiPay — they retry aggressively on non-2xx,
                // which would amplify any per-event processing bug. Log and move on.
                log.error("EfiPay webhook event processing failed: {}", event, e);
            }
        }
        return ResponseEntity.ok(OK_RESPONSE);
    }

    private void dispatch(JsonNode event) {
        if (event.has("devolucoes")) {
            handleRefundSent(event);
            return;
        }

        if (event.path("gnExtras").has("idEnvio")
                || "SOLICITACAO".equals(event.path("tipo").asText(null))) {
            handlePixSent(event);
            return;
        }
        if (event.has("txid")) {
            handlePixReceived(event);
            return;
        }
        log.warn("EfiPay webhook event did not match any known shape: {}", event);
    }

    /**
     * Refund settled at the BCB (arena cancellation, weather, etc.). The {@code devolucoes}
     * array carries the refund leg(s); we route on the first.
     */
    private void handleRefundSent(JsonNode event) {
        String txid = event.path("txid").asText(null);
        JsonNode firstRefund = event.path("devolucoes").path(0);
        String refundId = firstRefund.path("id").asText(null);
        String refundStatus = firstRefund.path("status").asText(null);

        log.info("EfiPay refund event: txid={} refundId={} status={}", txid, refundId, refundStatus);
        // TODO: look up the booking/payment by txid and transition the booking to "refunded"
        //  (and the payment row to the matching refund status). Make this idempotent — EfiPay
        //  may redeliver the same devolucao.id.
    }

    /**
     * Payout we initiated (split to the venue) reached its terminal state at EfiPay. Identified
     * by {@code gnExtras.idEnvio} or by {@code tipo = "SOLICITACAO"}.
     */
    private void handlePixSent(JsonNode event) {
        String idEnvio = event.path("gnExtras").path("idEnvio").asText(null);
        String status = event.path("status").asText(null);

        log.info("EfiPay payout event: idEnvio={} status={}", idEnvio, status);
        // TODO: look up the VenuePayout by idEnvio and mark it as completed (or failed,
        //  depending on status). Idempotency: the same idEnvio may be redelivered.
    }

    /**
     * Customer paid the QR Code — the booking moves from pending to confirmed. Identified by a
     * top-level {@code txid} when the event is not a refund ({@code devolucoes}) and not a
     * payout ({@code gnExtras.idEnvio} / {@code tipo = "SOLICITACAO"}). May include
     * {@code gnExtras.pagador} with the payer's CPF/CNPJ when EfiPay has it.
     */
    private void handlePixReceived(JsonNode event) {
        String txid = event.path("txid").asText(null);
        String valor = event.path("valor").asText(null);
        JsonNode pagador = event.path("gnExtras").path("pagador");
        String payerName = pagador.path("nome").asText(null);
        String payerDoc = pagador.hasNonNull("cpf")
                ? pagador.path("cpf").asText(null)
                : pagador.path("cnpj").asText(null);

        log.info("EfiPay PIX received: txid={} valor={} payerName={} payerDoc={}",
                txid, valor, payerName, payerDoc);
        // TODO: look up the booking by txid and transition it to "confirmed"; persist the
        //  paid amount on the payment row. If gnExtras.pagador is present, optionally store
        //  the payer name + masked CPF/CNPJ for the receipt. Idempotency: same txid may be
        //  redelivered.
    }
}
