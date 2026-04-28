package com.bookingsquadra.controller;

import com.bookingsquadra.dto.LegalTermsDto;
import com.bookingsquadra.service.LegalService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/legal")
public class LegalController {

    private final LegalService legalService;

    public LegalController(LegalService legalService) {
        this.legalService = legalService;
    }

    @GetMapping("/terms")
    public LegalTermsDto terms() {
        return legalService.getTerms();
    }
}
