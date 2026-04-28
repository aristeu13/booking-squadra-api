package com.bookingsquadra.service;

import com.bookingsquadra.entity.Booking;
import com.bookingsquadra.entity.Court;
import com.bookingsquadra.entity.OperatingHours;
import com.bookingsquadra.entity.RecurringTimeBlock;
import com.bookingsquadra.entity.Venue;
import com.bookingsquadra.repository.BookingRepository;
import com.bookingsquadra.repository.CourtRepository;
import com.bookingsquadra.repository.OperatingHoursRepository;
import com.bookingsquadra.repository.RecurringTimeBlockRepository;
import com.bookingsquadra.repository.VenueRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class CourtAvailabilityService {

    private static final int MINUTES_PER_DAY = 24 * 60;
    private static final Set<String> ACTIVE_STATUSES = Set.of("pending", "confirmed");
    private static final DateTimeFormatter SLOT_FORMAT = DateTimeFormatter.ofPattern("HH:mm");

    private final CourtRepository courtRepository;
    private final VenueRepository venueRepository;
    private final OperatingHoursRepository operatingHoursRepository;
    private final BookingRepository bookingRepository;
    private final RecurringTimeBlockRepository recurringTimeBlockRepository;

    public CourtAvailabilityService(
            CourtRepository courtRepository,
            VenueRepository venueRepository,
            OperatingHoursRepository operatingHoursRepository,
            BookingRepository bookingRepository,
            RecurringTimeBlockRepository recurringTimeBlockRepository
    ) {
        this.courtRepository = courtRepository;
        this.venueRepository = venueRepository;
        this.operatingHoursRepository = operatingHoursRepository;
        this.bookingRepository = bookingRepository;
        this.recurringTimeBlockRepository = recurringTimeBlockRepository;
    }

    @Transactional(readOnly = true)
    public List<String> getAvailableSlots(UUID courtId, LocalDate date) {
        Court court = courtRepository.findById(courtId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Court not found"));

        Venue venue = venueRepository.findById(court.getVenueId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Venue not found"));

        // Postgres day_of_week convention: 0 = Sunday … 6 = Saturday.
        short dow = (short) (date.getDayOfWeek().getValue() % 7);

        OperatingHours hours = operatingHoursRepository
                .findByVenueIdAndDayOfWeek(venue.getId(), dow)
                .orElse(null);
        if (hours == null) {
            return List.of();
        }

        int slotMinutes = venue.getSlotDurationMinutes();
        int openMin = toMinutes(hours.getOpenTime());
        int closeMin = toMinutes(hours.getCloseTime());

        // Overnight shift: close_time wraps past midnight (e.g. 18:00–02:00).
        boolean overnight = closeMin <= openMin;
        if (overnight) {
            closeMin += MINUTES_PER_DAY;
        }

        LocalDate nextDate = date.plusDays(1);
        List<LocalDate> dates = overnight ? List.of(date, nextDate) : List.of(date);

        List<int[]> blockedIntervals = new ArrayList<>();

        bookingRepository.findByCourtIdAndBookingDateInAndStatusIn(courtId, dates, ACTIVE_STATUSES)
                .forEach(b -> blockedIntervals.add(bookingInterval(b, date, nextDate)));

        for (LocalDate target : dates) {
            short targetDow = (short) (target.getDayOfWeek().getValue() % 7);
            recurringTimeBlockRepository
                    .findApplicableForDate(venue.getId(), courtId, targetDow, target)
                    .forEach(b -> blockedIntervals.add(blockInterval(b, target, date)));
        }

        List<String> available = new ArrayList<>();
        for (int t = openMin; t + slotMinutes <= closeMin; t += slotMinutes) {
            int slotStart = t;
            int slotEnd = t + slotMinutes;
            if (overlapsAny(slotStart, slotEnd, blockedIntervals)) {
                continue;
            }
            int normalized = slotStart % MINUTES_PER_DAY;
            available.add(LocalTime.of(normalized / 60, normalized % 60).format(SLOT_FORMAT));
        }
        return available;
    }

    private static int[] bookingInterval(Booking booking, LocalDate baseDate, LocalDate nextDate) {
        int start = toMinutes(booking.getStartTime());
        int end = toMinutes(booking.getEndTime());
        if (booking.getBookingDate().equals(nextDate) && !nextDate.equals(baseDate)) {
            start += MINUTES_PER_DAY;
            end += MINUTES_PER_DAY;
        }
        return new int[]{start, end};
    }

    private static int[] blockInterval(RecurringTimeBlock block, LocalDate target, LocalDate baseDate) {
        int start = toMinutes(block.getStartTime());
        int end = toMinutes(block.getEndTime());
        if (!target.equals(baseDate)) {
            start += MINUTES_PER_DAY;
            end += MINUTES_PER_DAY;
        }
        return new int[]{start, end};
    }

    private static int toMinutes(LocalTime time) {
        return time.getHour() * 60 + time.getMinute();
    }

    private static boolean overlapsAny(int start, int end, List<int[]> intervals) {
        for (int[] iv : intervals) {
            if (start < iv[1] && end > iv[0]) {
                return true;
            }
        }
        return false;
    }
}
