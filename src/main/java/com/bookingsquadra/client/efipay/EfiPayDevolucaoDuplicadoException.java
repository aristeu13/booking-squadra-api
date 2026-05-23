package com.bookingsquadra.client.efipay;

/**
 * Thrown when EfiPay returns 409 with {@code nome = "devolucao_id_duplicado"} from
 * {@code PUT /v2/pix/{e2eId}/devolucao/{id}}. The caller can either retry with a fresh
 * refund id or look up the existing devolução via {@code GET /v2/pix/{e2eId}/devolucao/{id}}.
 */
public class EfiPayDevolucaoDuplicadoException extends RuntimeException {

    private final String e2eId;
    private final String refundId;

    public EfiPayDevolucaoDuplicadoException(String e2eId, String refundId, String message) {
        super(message);
        this.e2eId = e2eId;
        this.refundId = refundId;
    }

    public String e2eId() {
        return e2eId;
    }

    public String refundId() {
        return refundId;
    }
}
