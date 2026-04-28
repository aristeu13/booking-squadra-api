package com.bookingsquadra.controller;

import com.bookingsquadra.dto.ProfileDto;
import com.bookingsquadra.dto.UpdateProfileDto;
import com.bookingsquadra.service.UserService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
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

    @DeleteMapping("/me")
    public ResponseEntity<Void> deleteMe() {
        userService.deleteCurrent();
        return ResponseEntity.noContent().build();
    }
}
