package com.bookingsquadra.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

@Service
public class CourtService {

    private final CourtAvailabilityService courtAvailabilityService;

    public CourtService(CourtAvailabilityService courtAvailabilityService) {
        this.courtAvailabilityService = courtAvailabilityService;
    }

    @Transactional(readOnly = true)
    public List<LocalTime> getAvailableSlots(UUID courtId, LocalDate date) {
        return courtAvailabilityService.getAvailableSlots(courtId, date);
    }
}
