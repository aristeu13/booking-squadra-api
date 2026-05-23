package com.bookingsquadra.client.efipay;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.Instant;

/**
 * Represents a devolução record in EfiPay's API. Used both as the response of
 * {@code PUT /v2/pix/{e2eId}/devolucao/{id}} and {@code GET /v2/pix/{e2eId}/devolucao/{id}},
 * and as the element type of the {@code devolucoes} array in {@link EfiPayPixDetailsResponse}.
 *
 * <p>{@code status} is one of {@code EM_PROCESSAMENTO}, {@code DEVOLVIDO}, {@code NAO_REALIZADO}.
 * {@code motivo} is only present when the refund was rejected ({@code NAO_REALIZADO}).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record EfiPayPixRefundResponse(
        String id,
        String rtrId,
        String valor,
        Horario horario,
        String status,
        String motivo
) {

    /** Refund request accepted and queued. Initial state from PUT; terminal state arrives via webhook. */
    public static final String STATUS_EM_PROCESSAMENTO = "EM_PROCESSAMENTO";
    /** Refund was settled successfully (terminal). */
    public static final String STATUS_DEVOLVIDO = "DEVOLVIDO";
    /** Refund was rejected. {@code motivo} carries the reason (e.g. insufficient balance, timeout). */
    public static final String STATUS_NAO_REALIZADO = "NAO_REALIZADO";

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Horario(Instant solicitacao) {
    }
}
