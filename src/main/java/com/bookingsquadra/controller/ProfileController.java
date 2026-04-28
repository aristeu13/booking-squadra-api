package com.bookingsquadra.controller;

import com.bookingsquadra.dto.DeleteAccountDto;
import com.bookingsquadra.dto.ProfileDto;
import com.bookingsquadra.dto.UpdateProfileDto;
import com.bookingsquadra.service.UserService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/profiles")
public class ProfileController {

    private final UserService userService;

    public ProfileController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/me")
    public ProfileDto me() {
        return userService.getCurrent();
    }

    @PutMapping("/me")
    public ProfileDto updateMe(@Valid @RequestBody UpdateProfileDto body) {
        return userService.updateCurrent(body);
    }

    @PostMapping("/me/delete/otp/request")
    public ResponseEntity<Void> requestDeleteOtp() {
        userService.requestDeleteAccountOtp();
        return ResponseEntity.accepted().build();
    }

    @DeleteMapping("/me")
    public ResponseEntity<Void> deleteMe(@Valid @RequestBody DeleteAccountDto body) {
        userService.deleteCurrent(body.code());
        return ResponseEntity.noContent().build();
    }
}
