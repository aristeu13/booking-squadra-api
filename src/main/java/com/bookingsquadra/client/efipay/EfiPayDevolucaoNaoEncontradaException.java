package com.bookingsquadra.client.efipay;

/**
 * Thrown when EfiPay returns 400 with {@code nome = "devolucao_nao_encontrada"} from
 * {@code GET /v2/pix/{e2eId}/devolucao/{id}} — no refund exists for the given pair.
 * Distinct from {@link EfiPayPixNaoEncontradoException}, which means the parent PIX itself
 * is unknown.
 */
public class EfiPayDevolucaoNaoEncontradaException extends RuntimeException {

    private final String e2eId;
    private final String refundId;

    public EfiPayDevolucaoNaoEncontradaException(String e2eId, String refundId, String message) {
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
