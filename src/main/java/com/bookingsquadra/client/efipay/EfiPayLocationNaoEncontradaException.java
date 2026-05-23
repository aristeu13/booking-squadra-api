package com.bookingsquadra.client.efipay;

/**
 * Thrown when EfiPay returns {@code nome = "location_nao_encontrada"} from
 * {@code GET /v2/loc/{id}/qrcode} — the location id is unknown. Usually a caller bug
 * (wrong id) or the location was removed.
 */
public class EfiPayLocationNaoEncontradaException extends RuntimeException {

    private final Long locationId;

    public EfiPayLocationNaoEncontradaException(Long locationId, String message) {
        super(message);
        this.locationId = locationId;
    }

    public Long locationId() {
        return locationId;
    }
}
