package com.bookingsquadra.client.efipay;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.math.BigDecimal;

/**
 * Body of {@code PUT /v2/pix/{e2eId}/devolucao/{id}}. Single field — the amount to refund
 * formatted in the EMV/BR Code shape ({@code \d+\.\d{2}}). EfiPay accepts partial refunds
 * (sum of devoluções ≤ original valor).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record EfiPayPixRefundRequest(String valor) {

    public static EfiPayPixRefundRequest of(BigDecimal amount) {
        return new EfiPayPixRefundRequest(EfiPayPixChargeRequest.Valor.of(amount).original());
    }
}
