package com.bookingsquadra.controller;

import com.bookingsquadra.dto.EmailChangeStartDto;
import com.bookingsquadra.dto.IdentifierChangeConfirmDto;
import com.bookingsquadra.dto.IdentifierChangeStartResponseDto;
import com.bookingsquadra.dto.PhoneChangeStartDto;
import com.bookingsquadra.dto.ProfileDto;
import com.bookingsquadra.service.IdentifierChangeService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/profiles/me/identifiers")
public class ProfileIdentifierController {

    private final IdentifierChangeService identifierChangeService;

    public ProfileIdentifierController(IdentifierChangeService identifierChangeService) {
        this.identifierChangeService = identifierChangeService;
    }

    @PostMapping("/phone/start")
    public ResponseEntity<IdentifierChangeStartResponseDto> startPhoneChange(
            @Valid @RequestBody PhoneChangeStartDto body,
            HttpServletRequest request
    ) {
        return ResponseEntity.ok(identifierChangeService.startPhoneChange(body.phone(), request));
    }

    @PostMapping("/phone/confirm")
    public ResponseEntity<ProfileDto> confirmPhoneChange(@Valid @RequestBody IdentifierChangeConfirmDto body) {
        return ResponseEntity.ok(identifierChangeService.confirmPhoneChange(body));
    }

    @PostMapping("/email/start")
    public ResponseEntity<IdentifierChangeStartResponseDto> startEmailChange(
            @Valid @RequestBody EmailChangeStartDto body,
            HttpServletRequest request
    ) {
        return ResponseEntity.ok(identifierChangeService.startEmailChange(body.email(), request));
    }

    @PostMapping("/email/confirm")
    public ResponseEntity<ProfileDto> confirmEmailChange(@Valid @RequestBody IdentifierChangeConfirmDto body) {
        return ResponseEntity.ok(identifierChangeService.confirmEmailChange(body));
    }
}
