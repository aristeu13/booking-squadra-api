package com.bookingsquadra.client.efipay;

/**
 * Thrown when EfiPay returns 400 with {@code nome = "pix_nao_encontrado"} — no PIX exists
 * for the {@code endToEndId} we tried to refund. Usually means the e2eId was never recorded
 * on our side (race with the webhook), or the original payment is too old to be reversed
 * via {@code PUT /v2/pix/{e2eId}/devolucao/{id}}.
 */
public class EfiPayPixNaoEncontradoException extends RuntimeException {

    private final String e2eId;

    public EfiPayPixNaoEncontradoException(String e2eId, String message) {
        super(message);
        this.e2eId = e2eId;
    }

    public String e2eId() {
        return e2eId;
    }
}
