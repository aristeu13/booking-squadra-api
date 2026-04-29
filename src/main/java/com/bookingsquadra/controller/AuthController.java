package com.bookingsquadra.controller;

import com.bookingsquadra.dto.AuthTokenDto;
import com.bookingsquadra.dto.OtpRequestDto;
import com.bookingsquadra.dto.OtpVerifyDto;
import com.bookingsquadra.service.AuthService;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/otp/request")
    @SecurityRequirements
    public ResponseEntity<Void> requestOtp(@Valid @RequestBody OtpRequestDto body) {
        authService.requestOtp(body.email());
        return ResponseEntity.accepted().build();
    }

    @PostMapping("/otp/verify")
    @SecurityRequirements
    public ResponseEntity<AuthTokenDto> verifyOtp(@Valid @RequestBody OtpVerifyDto body) {
        return ResponseEntity.ok(authService.verifyOtp(body.email(), body.code()));
    }

    @PostMapping("/signout")
    public ResponseEntity<Void> signOut() {
        authService.signOut();
        return ResponseEntity.noContent().build();
    }
}
