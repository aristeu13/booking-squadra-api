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
