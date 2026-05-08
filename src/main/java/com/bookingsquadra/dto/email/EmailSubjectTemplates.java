package com.bookingsquadra.dto.email;

public final class EmailSubjectTemplates {

    public static final String PAYMENT_CONFIRMED = "Reserva confirmada em {{nome_da_quadra}}";
    public static final String PAYMENT_REFUND_IN_PROGRESS = "Solicitação de reembolso recebida - {{nome_da_quadra}}";
    public static final String PAYMENT_REFUNDED = "Seu reembolso foi concluído - {{nome_da_quadra}}";
    public static final String PAYMENT_REFUND_DENIED = "Atualização sobre o cancelamento - {{nome_da_quadra}}";
    public static final String PRERESERVATION_CANCELLED = "Sua pré-reserva foi cancelada - {{nome_da_quadra}}";

    private EmailSubjectTemplates() {
    }

    public static String interpolateNomeDaQuadra(String template, String venueName) {
        return template.replace("{{nome_da_quadra}}", venueName == null ? "" : venueName);
    }
}
