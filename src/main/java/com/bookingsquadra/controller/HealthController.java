package com.bookingsquadra.controller;

import com.bookingsquadra.dto.HealthDto;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

@RestController
public class HealthController {

    @GetMapping("/health")
    public HealthDto health() {
        return new HealthDto("UP", OffsetDateTime.now(ZoneOffset.UTC));
    }
}
