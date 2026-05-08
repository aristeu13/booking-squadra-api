package com.bookingsquadra.service;

import com.bookingsquadra.entity.Booking;
import com.bookingsquadra.entity.Court;
import com.bookingsquadra.entity.User;
import com.bookingsquadra.entity.Venue;
import com.bookingsquadra.repository.CourtRepository;
import com.bookingsquadra.repository.UserRepository;
import com.bookingsquadra.repository.VenueRepository;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;

@Service
public class BookingNotificationDataLoader {

    private final UserRepository userRepository;
    private final CourtRepository courtRepository;
    private final VenueRepository venueRepository;

    public BookingNotificationDataLoader(
            UserRepository userRepository,
            CourtRepository courtRepository,
            VenueRepository venueRepository
    ) {
        this.userRepository = userRepository;
        this.courtRepository = courtRepository;
        this.venueRepository = venueRepository;
    }

    public Optional<BookingEmailPayloadMapper.BookingNotificationData> load(Booking booking) {
        UUID userId = booking.getUserId();
        if (userId == null) {
            return Optional.empty();
        }
        Optional<User> userOpt = userRepository.findById(userId);
        if (userOpt.isEmpty()) {
            return Optional.empty();
        }
        User user = userOpt.get();
        if (user.getEmail() == null || user.getEmail().isBlank()) {
            return Optional.empty();
        }
        Optional<Court> courtOpt = courtRepository.findById(booking.getCourtId());
        if (courtOpt.isEmpty()) {
            return Optional.empty();
        }
        Court court = courtOpt.get();
        Optional<Venue> venueOpt = venueRepository.findById(court.getVenueId());
        if (venueOpt.isEmpty()) {
            return Optional.empty();
        }
        Venue venue = venueOpt.get();
        return Optional.of(BookingEmailPayloadMapper.fromEntities(booking, user, court, venue));
    }
}
