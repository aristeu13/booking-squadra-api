package com.bookingsquadra.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;

public record OtpRequestDto(
        @Email @Size(max = 254) String email,
        @Size(max = 32) String phone
) {

    @AssertTrue(message = "exactly one of email or phone must be provided")
    @JsonIgnore
    public boolean isExactlyOneIdentifierProvided() {
        boolean hasEmail = email != null && !email.isBlank();
        boolean hasPhone = phone != null && !phone.isBlank();
        return hasEmail ^ hasPhone;
    }
}
