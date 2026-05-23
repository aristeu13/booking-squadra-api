package com.bookingsquadra.client.efipay;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.Instant;

@JsonIgnoreProperties(ignoreUnknown = true)
public record EfiPayPixChargeResponse(
        Calendario calendario,
        String txid,
        Integer revisao,
        Loc loc,
        String location,
        String status,
        Devedor devedor,
        Valor valor,
        String chave,
        String solicitacaoPagador,
        String pixCopiaECola
) {

    /** Charge generated and awaiting payment. Initial state for a fresh charge. */
    public static final String STATUS_ATIVA = "ATIVA";
    /** Charge was paid by the customer (terminal). */
    public static final String STATUS_CONCLUIDA = "CONCLUIDA";
    /** Charge was cancelled by us, the receiver (terminal). */
    public static final String STATUS_REMOVIDA_PELO_USUARIO_RECEBEDOR = "REMOVIDA_PELO_USUARIO_RECEBEDOR";
    /** Charge was cancelled by EfiPay/the PSP, not by us (terminal). */
    public static final String STATUS_REMOVIDA_PELO_PSP = "REMOVIDA_PELO_PSP";

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Calendario(Instant criacao, Integer expiracao) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Loc(Long id, String location, String tipoCob) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Devedor(String cpf, String cnpj, String nome) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Valor(String original) {
    }
}
