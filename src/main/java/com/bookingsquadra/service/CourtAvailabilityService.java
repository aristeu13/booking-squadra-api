package com.bookingsquadra.service;

import com.bookingsquadra.dto.AvailableSlotDto;
import com.bookingsquadra.entity.City;
import com.bookingsquadra.entity.Court;
import com.bookingsquadra.entity.Venue;
import com.bookingsquadra.repository.BookingRepository;
import com.bookingsquadra.repository.CityRepository;
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
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
public class CourtAvailabilityService {

    private static final int MINUTES_PER_DAY = 24 * 60;
    private static final DateTimeFormatter SLOT_TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");
    private static final Set<String> ACTIVE_STATUSES = Set.of("pending", "confirmed");

    private final CityRepository cityRepository;
    private final CourtRepository courtRepository;
    private final VenueRepository venueRepository;
    private final OperatingHoursRepository operatingHoursRepository;
    private final BookingRepository bookingRepository;
    private final RecurringTimeBlockRepository recurringTimeBlockRepository;

    public CourtAvailabilityService(
            CityRepository cityRepository,
            CourtRepository courtRepository,
            VenueRepository venueRepository,
            OperatingHoursRepository operatingHoursRepository,
            BookingRepository bookingRepository,
            RecurringTimeBlockRepository recurringTimeBlockRepository
    ) {
        this.cityRepository = cityRepository;
        this.courtRepository = courtRepository;
        this.venueRepository = venueRepository;
        this.operatingHoursRepository = operatingHoursRepository;
        this.bookingRepository = bookingRepository;
        this.recurringTimeBlockRepository = recurringTimeBlockRepository;
    }

    @Transactional(readOnly = true)
    public AvailableSlotDto getAvailableSlots(UUID courtId, LocalDate date) {
        Court court = activeCourtOrThrow(courtId);
        Venue venue = activeVenueOrThrow(court.getVenueId());
        ZoneId venueZone = zoneIdForVenue(venue);

        if (date.isBefore(LocalDate.now(venueZone))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "date is in the past");
        }

        int slotMin = venue.getSlotDurationMinutes();
        List<int[]> windows = openWindowsForDate(venue.getId(), date);
        if (windows.isEmpty()) {
            return new AvailableSlotDto(date, venueZone.getId(), venue.getSlotDurationMinutes(), List.of());
        }

        List<int[]> blocked = blockedIntervalsForDate(courtId, venue.getId(), date, venueZone);
        int nowCutoff = date.equals(LocalDate.now(venueZone))
                ? toMinutes(LocalTime.now(venueZone))
                : Integer.MIN_VALUE;

        List<String> slots = new ArrayList<>();
        for (int[] window : windows) {
            int gridStart = window[0];
            int windowEnd = window[1];
            int firstK = gridStart < 0 ? Math.floorDiv(-gridStart + slotMin - 1, slotMin) : 0;
            for (int k = firstK; ; k++) {
                int slotStart = gridStart + k * slotMin;
                int slotEnd = slotStart + slotMin;
                if (slotEnd > windowEnd) break;
                if (slotStart >= MINUTES_PER_DAY) break;
                if (slotStart < 0) continue;
                if (slotStart < nowCutoff) continue;
                if (overlapsAny(slotStart, slotEnd, blocked)) continue;
                slots.add(toLocalTime(slotStart).format(SLOT_TIME_FORMATTER));
            }
        }
        return new AvailableSlotDto(date, venueZone.getId(), venue.getSlotDurationMinutes(), slots);
    }

    /**
     * Validates that the requested booking slot is bookable: court/venue active, inside operating
     * hours, aligned to the slot grid, and not overlapping a recurring maintenance block. Booking
     * overlap is enforced at write time via the DB exclusion constraint.
     */
    @Transactional(readOnly = true)
    public ValidatedSlot validateBookingSlot(UUID courtId, LocalDate date,
                                             LocalTime startTime, LocalTime endTime) {
        Court court = activeCourtOrThrow(courtId);
        Venue venue = activeVenueOrThrow(court.getVenueId());
        ZoneId venueZone = zoneIdForVenue(venue);

        if (date.isBefore(LocalDate.now(venueZone))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "booking_date is in the past");
        }

        int slotMin = venue.getSlotDurationMinutes();
        int startMin = toMinutes(startTime);
        int rawEndMin = toMinutes(endTime);
        int endMin = rawEndMin > startMin ? rawEndMin : rawEndMin + MINUTES_PER_DAY;
        int duration = endMin - startMin;
        if (duration <= 0 || duration % slotMin != 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "duration must be a positive multiple of " + slotMin + " minutes");
        }

        ZonedDateTime localStartsAt = date.atTime(startTime).atZone(venueZone);
        ZonedDateTime localEndsAt = date.plusDays(endMin >= MINUTES_PER_DAY ? 1 : 0)
                .atTime(endTime)
                .atZone(venueZone);
        OffsetDateTime startsAt = localStartsAt.withZoneSameInstant(ZoneOffset.UTC).toOffsetDateTime();
        OffsetDateTime endsAt = localEndsAt.withZoneSameInstant(ZoneOffset.UTC).toOffsetDateTime();
        if (!startsAt.isAfter(OffsetDateTime.now(ZoneOffset.UTC))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "start_time has already passed");
        }

        List<int[]> windows = openWindowsForDate(venue.getId(), date);
        int[] window = findContainingWindow(windows, startMin, endMin);
        if (window == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "slot is outside operating hours");
        }
        if (Math.floorMod(startMin - window[0], slotMin) != 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "start_time must align to the venue slot grid");
        }

        boolean blockedByMaintenance = recurringBlockedIntervalsForSlot(
                venue.getId(), courtId, date, endMin)
                .stream()
                .anyMatch(rb -> intervalsOverlap(startMin, endMin, rb[0], rb[1]));
        if (blockedByMaintenance) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "slot overlaps a recurring maintenance block");
        }

        return new ValidatedSlot(court, venue, startsAt, endsAt, venueZone.getId(), duration / slotMin);
    }

    public record ValidatedSlot(
            Court court,
            Venue venue,
            OffsetDateTime startsAt,
            OffsetDateTime endsAt,
            String venueTimezone,
            int slotCount
    ) {}

    public record CourtSlotInstant(
            UUID courtId,
            String courtName,
            OffsetDateTime startsAt,
            OffsetDateTime endsAt
    ) {}

    @Transactional(readOnly = true)
    public int countVenueDayCapacity(UUID venueId, LocalDate date) {
        Venue venue = venueOrThrow(venueId);
        int slotMin = venue.getSlotDurationMinutes();
        List<int[]> windows = openWindowsForDate(venueId, date);
        if (windows.isEmpty()) return 0;

        List<Court> courts = courtRepository.findByVenueIdAndActiveTrueOrderBySortOrderAsc(venueId);
        int total = 0;
        for (Court court : courts) {
            List<int[]> recurringBlocks = recurringBlocksForDate(venueId, court.getId(), date);
            for (int[] window : windows) {
                int gridStart = window[0];
                int windowEnd = window[1];
                int firstK = gridStart < 0 ? Math.floorDiv(-gridStart + slotMin - 1, slotMin) : 0;
                for (int k = firstK; ; k++) {
                    int slotStart = gridStart + k * slotMin;
                    int slotEnd = slotStart + slotMin;
                    if (slotEnd > windowEnd) break;
                    if (slotStart < 0) continue;
                    if (overlapsAny(slotStart, slotEnd, recurringBlocks)) continue;
                    total++;
                }
            }
        }
        return total;
    }

    @Transactional(readOnly = true)
    public Optional<CourtSlotInstant> findFirstAvailableSlotForVenue(UUID venueId, LocalDate date) {
        Venue venue = venueOrThrow(venueId);
        ZoneId venueZone = zoneIdForVenue(venue);
        if (date.isBefore(LocalDate.now(venueZone))) {
            return Optional.empty();
        }

        int slotMin = venue.getSlotDurationMinutes();
        List<int[]> windows = openWindowsForDate(venueId, date);
        if (windows.isEmpty()) return Optional.empty();
        int nowCutoff = date.equals(LocalDate.now(venueZone))
                ? toMinutes(LocalTime.now(venueZone))
                : Integer.MIN_VALUE;

        List<Court> courts = courtRepository.findByVenueIdAndActiveTrueOrderBySortOrderAsc(venueId);
        CourtSlotInstant earliest = null;
        int earliestMin = Integer.MAX_VALUE;

        for (Court court : courts) {
            List<int[]> blocked = blockedIntervalsForDate(court.getId(), venueId, date, venueZone);
            for (int[] window : windows) {
                int gridStart = window[0];
                int windowEnd = window[1];
                int firstK = gridStart < 0 ? Math.floorDiv(-gridStart + slotMin - 1, slotMin) : 0;
                for (int k = firstK; ; k++) {
                    int slotStart = gridStart + k * slotMin;
                    int slotEnd = slotStart + slotMin;
                    if (slotEnd > windowEnd) break;
                    if (slotStart < 0) continue;
                    if (slotStart < nowCutoff) continue;
                    if (overlapsAny(slotStart, slotEnd, blocked)) continue;
                    if (slotStart < earliestMin) {
                        earliestMin = slotStart;
                        ZonedDateTime startLocal = date.atStartOfDay(venueZone).plusMinutes(slotStart);
                        ZonedDateTime endLocal   = date.atStartOfDay(venueZone).plusMinutes(slotEnd);
                        earliest = new CourtSlotInstant(
                                court.getId(),
                                court.getName(),
                                startLocal.withZoneSameInstant(ZoneOffset.UTC).toOffsetDateTime(),
                                endLocal.withZoneSameInstant(ZoneOffset.UTC).toOffsetDateTime()
                        );
                    }
                    break;
                }
            }
        }
        return Optional.ofNullable(earliest);
    }

    private List<int[]> recurringBlocksForDate(UUID venueId, UUID courtId, LocalDate date) {
        List<int[]> blocked = new ArrayList<>();
        short dow = (short) (date.getDayOfWeek().getValue() % 7);
        recurringTimeBlockRepository.findApplicableForDate(venueId, courtId, dow, date)
                .forEach(rb -> blocked.add(new int[]{
                        toMinutes(rb.getStartTime()), toMinutes(rb.getEndTime())}));
        // overnight window: also include next-day blocks shifted by +1440
        LocalDate next = date.plusDays(1);
        short nextDow = (short) (next.getDayOfWeek().getValue() % 7);
        recurringTimeBlockRepository.findApplicableForDate(venueId, courtId, nextDow, next)
                .forEach(rb -> blocked.add(new int[]{
                        MINUTES_PER_DAY + toMinutes(rb.getStartTime()),
                        MINUTES_PER_DAY + toMinutes(rb.getEndTime())}));
        return blocked;
    }

    private Court activeCourtOrThrow(UUID courtId) {
        Court court = courtRepository.findById(courtId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Court not found"));
        if (!Boolean.TRUE.equals(court.getActive())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Court not found");
        }
        return court;
    }

    private Venue activeVenueOrThrow(UUID venueId) {
        Venue venue = venueRepository.findById(venueId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Venue not found"));
        if (!Boolean.TRUE.equals(venue.getActive())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Venue not found");
        }
        return venue;
    }


    private Venue venueOrThrow(UUID venueId) {
        return venueRepository.findById(venueId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Venue not found"));
    }

    private ZoneId zoneIdForVenue(Venue venue) {
        City city = cityRepository.findById(venue.getCityId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.INTERNAL_SERVER_ERROR, "Venue city not found"));
        return ZoneId.of(city.getTimezone());
    }

    /**
     * Open windows on `date`, expressed in `date`'s minute coordinate. Overnight windows can
     * start before 0 or end after 1440 so the slot grid remains anchored to the opening time.
     */
    private List<int[]> openWindowsForDate(UUID venueId, LocalDate date) {
        List<int[]> windows = new ArrayList<>();

        LocalDate prev = date.minusDays(1);
        short prevDow = (short) (prev.getDayOfWeek().getValue() % 7);
        operatingHoursRepository.findByVenueIdAndDayOfWeek(venueId, prevDow).ifPresent(h -> {
            int o = toMinutes(h.getOpenTime());
            int c = toMinutes(h.getCloseTime());
            if (c <= o) {
                // overnight on prev day spills into [0, c] of `date`; grid is anchored at o - 1440
                windows.add(new int[]{o - MINUTES_PER_DAY, c});
            }
        });

        short dow = (short) (date.getDayOfWeek().getValue() % 7);
        operatingHoursRepository.findByVenueIdAndDayOfWeek(venueId, dow).ifPresent(h -> {
            int o = toMinutes(h.getOpenTime());
            int c = toMinutes(h.getCloseTime());
            if (c <= o) {
                windows.add(new int[]{o, MINUTES_PER_DAY + c});
            } else {
                windows.add(new int[]{o, c});
            }
        });

        return windows;
    }

    private List<int[]> blockedIntervalsForDate(UUID courtId, UUID venueId, LocalDate date, ZoneId venueZone) {
        List<int[]> blocked = new ArrayList<>();
        ZonedDateTime localStartOfDate = date.atStartOfDay(venueZone);
        ZonedDateTime localStartOfNextDate = date.plusDays(1).atStartOfDay(venueZone);
        OffsetDateTime rangeStart = localStartOfDate.withZoneSameInstant(ZoneOffset.UTC).toOffsetDateTime();
        OffsetDateTime rangeEnd = localStartOfNextDate.withZoneSameInstant(ZoneOffset.UTC).toOffsetDateTime();
        bookingRepository.findByCourtIdAndStatusInAndStartsAtBeforeAndEndsAtAfter(
                        courtId, ACTIVE_STATUSES, rangeEnd, rangeStart)
                .forEach(b -> blocked.add(new int[]{
                        toMinuteCoordinate(date, b.getStartsAt(), venueZone),
                        toMinuteCoordinate(date, b.getEndsAt(), venueZone)}));

        short dow = (short) (date.getDayOfWeek().getValue() % 7);
        recurringTimeBlockRepository.findApplicableForDate(venueId, courtId, dow, date)
                .forEach(rb -> blocked.add(new int[]{
                        toMinutes(rb.getStartTime()), toMinutes(rb.getEndTime())}));
        LocalDate next = date.plusDays(1);
        short nextDow = (short) (next.getDayOfWeek().getValue() % 7);
        recurringTimeBlockRepository.findApplicableForDate(venueId, courtId, nextDow, next)
                .forEach(rb -> blocked.add(new int[]{
                        MINUTES_PER_DAY + toMinutes(rb.getStartTime()),
                        MINUTES_PER_DAY + toMinutes(rb.getEndTime())}));
        return blocked;
    }

    private List<int[]> recurringBlockedIntervalsForSlot(UUID venueId, UUID courtId, LocalDate date, int endMin) {
        List<int[]> blocked = new ArrayList<>();
        short dow = (short) (date.getDayOfWeek().getValue() % 7);
        recurringTimeBlockRepository.findApplicableForDate(venueId, courtId, dow, date)
                .forEach(rb -> blocked.add(new int[]{
                        toMinutes(rb.getStartTime()), toMinutes(rb.getEndTime())}));
        if (endMin > MINUTES_PER_DAY) {
            LocalDate next = date.plusDays(1);
            short nextDow = (short) (next.getDayOfWeek().getValue() % 7);
            recurringTimeBlockRepository.findApplicableForDate(venueId, courtId, nextDow, next)
                    .forEach(rb -> blocked.add(new int[]{
                            MINUTES_PER_DAY + toMinutes(rb.getStartTime()),
                            MINUTES_PER_DAY + toMinutes(rb.getEndTime())}));
        }
        return blocked;
    }

    private static int[] findContainingWindow(List<int[]> windows, int startMin, int endMin) {
        for (int[] w : windows) {
            if (startMin >= Math.max(w[0], 0) && endMin <= w[1]) {
                return w;
            }
        }
        return null;
    }

    private static int toMinutes(LocalTime time) {
        return time.getHour() * 60 + time.getMinute();
    }

    private static LocalTime toLocalTime(int minutes) {
        int normalized = Math.floorMod(minutes, MINUTES_PER_DAY);
        return LocalTime.of(normalized / 60, normalized % 60);
    }

    private static int toMinuteCoordinate(LocalDate date, OffsetDateTime instant, ZoneId zone) {
        ZonedDateTime local = instant.atZoneSameInstant(zone);
        long dayOffset = ChronoUnit.DAYS.between(date, local.toLocalDate());
        return Math.toIntExact(dayOffset * MINUTES_PER_DAY + toMinutes(local.toLocalTime()));
    }

    private static boolean overlapsAny(int start, int end, List<int[]> intervals) {
        for (int[] iv : intervals) {
            if (intervalsOverlap(start, end, iv[0], iv[1])) return true;
        }
        return false;
    }

    private static boolean intervalsOverlap(int aStart, int aEnd, int bStart, int bEnd) {
        return aStart < bEnd && aEnd > bStart;
    }
}
