package com.bookingsquadra.service;

import com.bookingsquadra.dto.BookingDto;
import com.bookingsquadra.dto.CreateBookingDto;
import com.bookingsquadra.entity.Booking;
import com.bookingsquadra.entity.User;
import com.bookingsquadra.repository.BookingRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;

@Service
public class BookingService {

    private static final String BOOKING_TYPE_RESERVATION = "reservation";
    private static final String STATUS_PENDING = "pending";

    private final BookingRepository bookingRepository;
    private final UserService userService;

    public BookingService(BookingRepository bookingRepository, UserService userService) {
        this.bookingRepository = bookingRepository;
        this.userService = userService;
    }

    @Transactional
    public BookingDto create(CreateBookingDto body) {
        validate(body);
        User user = userService.findCurrentOrThrow();

        Booking booking = Booking.builder()
                .bookingType(BOOKING_TYPE_RESERVATION)
                .userId(user.getId())
                .courtId(body.courtId())
                .bookingDate(body.bookingDate())
                .startTime(body.startTime())
                .endTime(body.endTime())
                .status(STATUS_PENDING)
                .amountCents(0)
                .note(body.note())
                .build();

        Booking saved = bookingRepository.save(booking);
        return toDto(saved);
    }

    private void validate(CreateBookingDto body) {
        if (!body.startTime().isBefore(body.endTime())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "start_time must be before end_time");
        }

        if (body.bookingDate().isBefore(LocalDate.now())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "booking_date is in the past");
        }

        if (bookingRepository.existsBookingOverlap(
                body.courtId(), body.bookingDate(), body.startTime(), body.endTime())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "slot overlaps an existing booking");
        }

        if (bookingRepository.existsRecurringBlockOverlap(
                body.courtId(), body.bookingDate(), body.startTime(), body.endTime())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "slot overlaps a recurring maintenance block");
        }
    }

    private static BookingDto toDto(Booking b) {
        return new BookingDto(
                b.getId(),
                b.getUserId(),
                b.getCourtId(),
                b.getBookingDate(),
                b.getStartTime(),
                b.getEndTime(),
                b.getStatus(),
                b.getBookingType(),
                b.getNote()
        );
    }
}
