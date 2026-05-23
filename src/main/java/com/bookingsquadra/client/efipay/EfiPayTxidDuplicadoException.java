package com.bookingsquadra.client.efipay;

/**
 * Thrown when EfiPay returns 409 with {@code nome = "txid_duplicado"} from
 * {@code PUT /v2/cob/{txid}}. The caller can retry with a new txid (or treat
 * the existing charge as authoritative via {@code GET /v2/cob/{txid}}).
 */
public class EfiPayTxidDuplicadoException extends RuntimeException {

    private final String txid;

    public EfiPayTxidDuplicadoException(String txid, String message) {
        super(message);
        this.txid = txid;
    }

    public String txid() {
        return txid;
    }
}
