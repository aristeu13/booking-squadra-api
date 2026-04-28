package com.bookingsquadra.service;

import com.bookingsquadra.dto.BookingDto;
import com.bookingsquadra.dto.CreateBookingDto;
import com.bookingsquadra.entity.Booking;
import com.bookingsquadra.entity.User;
import com.bookingsquadra.entity.Venue;
import com.bookingsquadra.repository.BookingRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.ZoneId;

@Service
public class BookingService {

    private static final String BOOKING_TYPE_RESERVATION = "reservation";
    private static final String STATUS_PENDING = "pending";

    private final BookingRepository bookingRepository;
    private final UserService userService;
    private final CourtAvailabilityService courtAvailabilityService;

    public BookingService(
            BookingRepository bookingRepository,
            UserService userService,
            CourtAvailabilityService courtAvailabilityService
    ) {
        this.bookingRepository = bookingRepository;
        this.userService = userService;
        this.courtAvailabilityService = courtAvailabilityService;
    }

    @Transactional
    public BookingDto create(CreateBookingDto body) {
        User user = userService.findCurrentOrThrow();

        CourtAvailabilityService.ValidatedSlot validated = courtAvailabilityService.validateBookingSlot(
                body.courtId(), body.bookingDate(), body.startTime(), body.endTime());

        Venue venue = validated.venue();
        int amountCents = Math.multiplyExact(venue.getPriceCents(), validated.slotCount());

        Booking booking = Booking.builder()
                .bookingType(BOOKING_TYPE_RESERVATION)
                .userId(user.getId())
                .courtId(body.courtId())
                .startsAt(validated.startsAt())
                .endsAt(validated.endsAt())
                .venueTimezone(validated.venueTimezone())
                .status(STATUS_PENDING)
                .amountCents(amountCents)
                .note(body.note())
                .build();

        Booking saved;
        try {
            saved = bookingRepository.saveAndFlush(booking);
        } catch (DataIntegrityViolationException e) {
            if (causedByConstraint(e, "bookings_no_overlap")) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "slot overlaps an existing booking", e);
            }
            throw e;
        }
        return toDto(saved);
    }

    private static boolean causedByConstraint(Throwable t, String constraintName) {
        for (Throwable cur = t; cur != null; cur = cur.getCause()) {
            String message = cur.getMessage();
            if (message != null && message.contains(constraintName)) {
                return true;
            }
        }
        return false;
    }

    private static BookingDto toDto(Booking b) {
        ZoneId venueZone = ZoneId.of(b.getVenueTimezone());
        return new BookingDto(
                b.getId(),
                b.getUserId(),
                b.getCourtId(),
                b.getStartsAt(),
                b.getEndsAt(),
                b.getVenueTimezone(),
                b.getStartsAt().atZoneSameInstant(venueZone).toLocalDate(),
                b.getStartsAt().atZoneSameInstant(venueZone).toLocalTime(),
                b.getEndsAt().atZoneSameInstant(venueZone).toLocalTime(),
                b.getStatus(),
                b.getBookingType(),
                b.getNote()
        );
    }
}
