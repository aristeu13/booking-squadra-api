package com.bookingsquadra.controller;

import com.bookingsquadra.dto.AppointmentDto;
import com.bookingsquadra.dto.BookingDto;
import com.bookingsquadra.dto.CancelBookingDto;
import com.bookingsquadra.dto.CancelBookingRequestDto;
import com.bookingsquadra.dto.CreateBookingDto;
import com.bookingsquadra.service.BookingService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/bookings")
public class BookingController {

    private final BookingService bookingService;

    public BookingController(BookingService bookingService) {
        this.bookingService = bookingService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public BookingDto create(@Valid @RequestBody CreateBookingDto body) {
        return bookingService.create(body);
    }

    @GetMapping("/me")
    public Page<AppointmentDto> myBookings(
            @RequestParam(defaultValue = "upcoming") String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(name = "page_size", defaultValue = "20") int pageSize
    ) {
        return bookingService.getCurrentUserAppointments(status, page, pageSize);
    }

    @GetMapping("/{id}")
    public AppointmentDto getById(@PathVariable UUID id) {
        return bookingService.getCurrentUserBooking(id);
    }

    @GetMapping("/me/pending")
    public ResponseEntity<AppointmentDto> currentPending() {
        return bookingService.getCurrentUserPendingBooking()
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @PostMapping("/{id}/cancel")
    public CancelBookingDto cancel(
            @PathVariable UUID id,
            @Valid @RequestBody(required = false) CancelBookingRequestDto body
    ) {
        return bookingService.cancelCurrentUserBooking(id, body);
    }
}
