package com.bookingsquadra.client.efipay;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.math.BigDecimal;
import java.util.List;

/**
 * Caller-facing payload for {@link EfiPayPixClient#createCharge}. The PIX DICT key
 * ({@code chave}) is intentionally absent — the client injects it from
 * {@code app.payments.efipay.pix-key} so the merchant key isn't duplicated across callsites.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record EfiPayPixChargeRequest(
        Calendario calendario,
        Devedor devedor,
        Valor valor,
        String solicitacaoPagador,
        List<InfoAdicional> infoAdicionais,
        Loc loc
) {

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Calendario(Integer expiracao) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Devedor(String cpf, String cnpj, String nome) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Valor(String original) {

        /**
         * Builds a {@code valor} field in EfiPay's required shape ({@code \d+\.\d{2}}).
         * Does not round — callers must supply an amount with at most 2 decimal places,
         * otherwise the call fails fast. Money rounding is a domain decision that belongs
         * at the price-computation layer, not in a wire-format helper.
         */
        public static Valor of(BigDecimal amount) {
            if (amount == null) {
                throw new IllegalArgumentException("EfiPay valor.original must not be null");
            }
            if (amount.signum() <= 0) {
                throw new IllegalArgumentException(
                        "EfiPay valor.original must be greater than zero (got: " + amount.toPlainString() + ")");
            }
            if (amount.scale() > 2) {
                throw new IllegalArgumentException(
                        "EfiPay valor.original must have at most 2 decimal places (got: " + amount.toPlainString() + ")");
            }
            return new Valor(amount.setScale(2).toPlainString());
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record InfoAdicional(String nome, String valor) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Loc(Long id) {
    }
}
