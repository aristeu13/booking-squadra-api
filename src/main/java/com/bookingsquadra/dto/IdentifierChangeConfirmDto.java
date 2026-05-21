package com.bookingsquadra.dto;

import jakarta.validation.constraints.NotBlank;

public record IdentifierChangeConfirmDto(
        @NotBlank String code,
        Boolean acknowledgedMerge
) {

    public boolean mergeAcknowledged() {
        return Boolean.TRUE.equals(acknowledgedMerge);
    }
}
