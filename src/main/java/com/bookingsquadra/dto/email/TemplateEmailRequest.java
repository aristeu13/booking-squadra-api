package com.bookingsquadra.dto.email;

import java.util.Map;

public record TemplateEmailRequest(
        String to,
        String templateId,
        String subject,
        Map<String, String> templateVariables
) {
}
