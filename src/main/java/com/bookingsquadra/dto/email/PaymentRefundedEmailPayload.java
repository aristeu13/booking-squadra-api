package com.bookingsquadra.dto.email;

import java.util.Map;

public record PaymentRefundedEmailPayload(
        String dataReserva,
        String nomeDaQuadra,
        String nomeUsuario,
        String valorReembolso
) {
    public Map<String, String> toTemplateVariables() {
        return Map.of(
                "data_reserva", dataReserva,
                "nome_da_quadra", nomeDaQuadra,
                "nome_usuario", nomeUsuario,
                "valor_reembolso", valorReembolso
        );
    }
}
