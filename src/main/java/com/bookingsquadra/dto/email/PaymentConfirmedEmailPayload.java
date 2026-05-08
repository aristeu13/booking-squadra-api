package com.bookingsquadra.dto.email;

import java.util.Map;

public record PaymentConfirmedEmailPayload(
        String dataReserva,
        String horaReserva,
        String nomeDaQuadra,
        String nomeUsuario,
        String numeroNomeQuadra
) {
    public Map<String, String> toTemplateVariables() {
        return Map.of(
                "data_reserva", dataReserva,
                "hora_reserva", horaReserva,
                "nome_da_quadra", nomeDaQuadra,
                "nome_usuario", nomeUsuario,
                "numero_nome_quadra", numeroNomeQuadra
        );
    }
}
