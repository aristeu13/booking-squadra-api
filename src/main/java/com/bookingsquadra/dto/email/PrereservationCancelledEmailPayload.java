package com.bookingsquadra.dto.email;

import java.util.Map;

public record PrereservationCancelledEmailPayload(
        String dataReserva,
        String horaReserva,
        String nomeDaQuadra,
        String nomeUsuario
) {
    public Map<String, String> toTemplateVariables() {
        return Map.of(
                "data_reserva", dataReserva,
                "hora_reserva", horaReserva,
                "nome_da_quadra", nomeDaQuadra,
                "nome_usuario", nomeUsuario
        );
    }
}
