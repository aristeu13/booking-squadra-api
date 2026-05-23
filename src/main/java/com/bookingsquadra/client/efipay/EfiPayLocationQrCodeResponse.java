package com.bookingsquadra.client.efipay;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Response from {@code GET /v2/loc/{id}/qrcode}. Gives us the renderable QR code payload
 * for a previously-created location, along with a hosted visualization URL.
 *
 * <ul>
 *   <li>{@code qrcode} — BR Code / "copia e cola" string. Customer pastes this into their
 *       banking app to pay.</li>
 *   <li>{@code imagemQrcode} — full {@code data:image/svg+xml;base64,...} URI, ready to set as
 *       an {@code <img src>} on the frontend without re-encoding.</li>
 *   <li>{@code linkVisualizacao} — EfiPay-hosted payment page; useful as a fallback link.</li>
 * </ul>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record EfiPayLocationQrCodeResponse(
        String qrcode,
        String imagemQrcode,
        String linkVisualizacao
) {
}
