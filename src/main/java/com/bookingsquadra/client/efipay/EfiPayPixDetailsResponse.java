package com.bookingsquadra.client.efipay;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.Instant;
import java.util.List;

/**
 * Response from {@code GET /v2/pix/{e2eId}}. Returns the original PIX plus any devoluções
 * issued against it. The {@code pagador} block is only present when the request was made
 * with {@code ?exibirCodigoBanco=true}; older PIX events that pre-date that flag may also
 * lack it. Treat all optional sections as nullable.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record EfiPayPixDetailsResponse(
        String endToEndId,
        String txid,
        String valor,
        Instant horario,
        String infoPagador,
        Pagador pagador,
        List<EfiPayPixRefundResponse> devolucoes
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Pagador(ContaBanco contaBanco) {

        @JsonIgnoreProperties(ignoreUnknown = true)
        public record ContaBanco(String codigoBanco) {
        }
    }
}
