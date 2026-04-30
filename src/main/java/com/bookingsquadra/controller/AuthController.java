package com.bookingsquadra.controller;

import com.bookingsquadra.dto.AuthTokenDto;
import com.bookingsquadra.dto.GoogleAuthRequestDto;
import com.bookingsquadra.dto.OtpRequestDto;
import com.bookingsquadra.dto.OtpVerifyDto;
import com.bookingsquadra.dto.RefreshTokenDto;
import com.bookingsquadra.service.AuthService;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import jakarta.servlet.http.HttpServletRequest;
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
    public ResponseEntity<Void> requestOtp(@Valid @RequestBody OtpRequestDto body, HttpServletRequest request) {
        authService.requestOtp(body.email(), request);
        return ResponseEntity.accepted().build();
    }

    @PostMapping("/otp/verify")
    @SecurityRequirements
    public ResponseEntity<AuthTokenDto> verifyOtp(@Valid @RequestBody OtpVerifyDto body, HttpServletRequest request) {
        return ResponseEntity.ok(authService.verifyOtp(body.email(), body.code(), request));
    }

    @PostMapping("/google")
    @SecurityRequirements
    public ResponseEntity<AuthTokenDto> google(@Valid @RequestBody GoogleAuthRequestDto body) {
        return ResponseEntity.ok(authService.verifyGoogle(body.idToken()));
    }

    @PostMapping("/refresh")
    @SecurityRequirements
    public ResponseEntity<AuthTokenDto> refresh(@Valid @RequestBody RefreshTokenDto body) {
        return ResponseEntity.ok(authService.refresh(body.refreshToken()));
    }

    @PostMapping("/signout")
    @SecurityRequirements
    public ResponseEntity<Void> signOut(@Valid @RequestBody RefreshTokenDto body) {
        authService.signOut(body.refreshToken());
        return ResponseEntity.noContent().build();
    }
}
