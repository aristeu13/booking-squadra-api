package com.bookingsquadra.service;

import com.bookingsquadra.dto.CourtDto;
import com.bookingsquadra.dto.OwnerBookingDto;
import com.bookingsquadra.dto.OwnerVenueCourtDayDto;
import com.bookingsquadra.dto.OwnerVenueDayOverviewDto;
import com.bookingsquadra.dto.OwnerVenueSummaryDto;
import com.bookingsquadra.entity.City;
import com.bookingsquadra.entity.Court;
import com.bookingsquadra.entity.OperatingHours;
import com.bookingsquadra.entity.Payment;
import com.bookingsquadra.entity.RecurringTimeBlock;
import com.bookingsquadra.entity.Venue;
import com.bookingsquadra.exception.NotFoundException;
import com.bookingsquadra.exception.UnprocessableEntityException;
import com.bookingsquadra.repository.BookingRepository;
import com.bookingsquadra.repository.CityRepository;
import com.bookingsquadra.repository.CourtRepository;
import com.bookingsquadra.repository.OperatingHoursRepository;
import com.bookingsquadra.repository.RecurringTimeBlockRepository;
import com.bookingsquadra.repository.VenueRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class OwnerVenueService {

    private static final DateTimeFormatter ISO_DATE = DateTimeFormatter.ofPattern("uuuu-MM-dd")
            .withResolverStyle(ResolverStyle.STRICT);

    private final VenueRepository venueRepository;
    private final BookingRepository bookingRepository;
    private final CityRepository cityRepository;
    private final CourtRepository courtRepository;
    private final OperatingHoursRepository operatingHoursRepository;
    private final RecurringTimeBlockRepository recurringTimeBlockRepository;
    private final CourtAvailabilityService courtAvailabilityService;

    public OwnerVenueService(
            VenueRepository venueRepository,
            BookingRepository bookingRepository,
            CityRepository cityRepository,
            CourtRepository courtRepository,
            OperatingHoursRepository operatingHoursRepository,
            RecurringTimeBlockRepository recurringTimeBlockRepository,
            CourtAvailabilityService courtAvailabilityService
    ) {
        this.venueRepository = venueRepository;
        this.bookingRepository = bookingRepository;
        this.cityRepository = cityRepository;
        this.courtRepository = courtRepository;
        this.operatingHoursRepository = operatingHoursRepository;
        this.recurringTimeBlockRepository = recurringTimeBlockRepository;
        this.courtAvailabilityService = courtAvailabilityService;
    }

    @Transactional(readOnly = true)
    public List<OwnerVenueSummaryDto> listOwnedVenues() {
        return venueRepository.findOwnedVenuesByUserId(currentUserId()).stream()
                .map(p -> new OwnerVenueSummaryDto(
                        p.getId(),
                        p.getName(),
                        p.getSlug(),
                        p.getAddress(),
                        p.getImageUrl(),
                        p.getActive(),
                        p.getCourtCount()
                ))
                .toList();
    }

    @Transactional(readOnly = true)
    public Page<OwnerBookingDto> listVenueBookings(
            UUID venueId,
            LocalDate from,
            LocalDate to,
            String status,
            int page,
            int pageSize
    ) {
        Venue venue = venueRepository.findById(venueId)
                .orElseThrow(() -> new NotFoundException("Venue not found"));

        ZoneId zone = resolveVenueZone(venue);
        OffsetDateTime rangeStart = from.atStartOfDay(zone).toOffsetDateTime();
        OffsetDateTime rangeEnd   = to.plusDays(1).atStartOfDay(zone).toOffsetDateTime();

        return bookingRepository
                .findVenueBookingsForOwner(venueId, rangeStart, rangeEnd, status, PageRequest.of(page, pageSize))
                .map(p -> new OwnerBookingDto(
                        p.getId(),
                        p.getStartsAt(),
                        p.getEndsAt(),
                        p.getTimezone(),
                        p.getStatus(),
                        p.getBookingType(),
                        p.getAmountCents(),
                        p.getPaymentMethod(),
                        p.getPaymentStatus(),
                        p.getCourtId(),
                        p.getCourtName(),
                        p.getUserId(),
                        p.getUserName(),
                        p.getUserEmail(),
                        p.getUserPhone()
                ));
    }

    @Transactional(readOnly = true)
    public OwnerVenueDayOverviewDto getDayOverview(UUID venueId, String dateParam) {
        Venue venue = venueRepository.findById(venueId)
                .orElseThrow(() -> new NotFoundException("Venue not found"));

        ZoneId zone = resolveVenueZone(venue);
        LocalDate date = parseDate(dateParam);

        OffsetDateTime rangeStart = date.atStartOfDay(zone).toOffsetDateTime();
        OffsetDateTime rangeEnd   = date.plusDays(1).atStartOfDay(zone).toOffsetDateTime();

        long count    = bookingRepository.countActiveByVenueAndDateRange(venueId, rangeStart, rangeEnd);
        long capacity = courtAvailabilityService.countVenueDayCapacity(venueId, date);

        Optional<CourtAvailabilityService.CourtSlotInstant> next =
                courtAvailabilityService.findFirstAvailableSlotForVenue(venueId, date);

        OwnerVenueDayOverviewDto.NextAvailableSlot nextSlot = next
                .map(s -> new OwnerVenueDayOverviewDto.NextAvailableSlot(
                        s.courtId(), s.courtName(), s.startsAt(), s.endsAt()))
                .orElse(null);

        return new OwnerVenueDayOverviewDto(
                venueId,
                date,
                zone.getId(),
                new OwnerVenueDayOverviewDto.Reservations(count, capacity),
                nextSlot
        );
    }

    @Transactional(readOnly = true)
    public List<CourtDto> listVenueCourts(UUID venueId) {
        if (!venueRepository.existsById(venueId)) {
            throw new NotFoundException("Venue not found");
        }
        return courtRepository.findByVenueIdOrderBySortOrderAsc(venueId).stream()
                .map(c -> new CourtDto(
                        c.getId(),
                        c.getVenueId(),
                        c.getName(),
                        c.getSurfaceType(),
                        c.getIndoor(),
                        c.getSortOrder(),
                        c.getActive()
                ))
                .toList();
    }

    @Transactional(readOnly = true)
    public OwnerVenueCourtDayDto getCourtDay(UUID venueId, UUID courtId, String dateParam) {
        Venue venue = venueRepository.findById(venueId)
                .orElseThrow(() -> new NotFoundException("Venue not found"));
        Court court = courtRepository.findById(courtId)
                .orElseThrow(() -> new NotFoundException("Court not found"));
        if (!venueId.equals(court.getVenueId())) {
            throw new NotFoundException("Court not found");
        }

        ZoneId zone = resolveVenueZone(venue);
        LocalDate date = parseDate(dateParam);

        OffsetDateTime rangeStart = date.atStartOfDay(zone).toOffsetDateTime();
        OffsetDateTime rangeEnd   = date.plusDays(1).atStartOfDay(zone).toOffsetDateTime();

        OwnerVenueCourtDayDto.Schedule schedule = scheduleForDate(venueId, date);
        List<OwnerVenueCourtDayDto.Block> blocks = blocksForDate(venueId, courtId, date, zone);
        List<OwnerVenueCourtDayDto.Reservation> reservations =
                reservationsForCourtDay(courtId, court.getSurfaceType(), rangeStart, rangeEnd);

        OwnerVenueCourtDayDto.Court courtDay = new OwnerVenueCourtDayDto.Court(
                court.getId(),
                court.getName(),
                court.getSurfaceType(),
                Boolean.TRUE.equals(court.getIndoor()),
                schedule,
                blocks,
                reservations
        );

        return new OwnerVenueCourtDayDto(venueId, date, zone.getId(), List.of(courtDay));
    }

    private OwnerVenueCourtDayDto.Schedule scheduleForDate(UUID venueId, LocalDate date) {
        short dow = (short) (date.getDayOfWeek().getValue() % 7);
        Optional<OperatingHours> hours = operatingHoursRepository.findByVenueIdAndDayOfWeek(venueId, dow);
        return hours
                .map(h -> new OwnerVenueCourtDayDto.Schedule(
                        (int) h.getOpenTime().getHour(),
                        (int) h.getCloseTime().getHour()))
                .orElseGet(() -> new OwnerVenueCourtDayDto.Schedule(null, null));
    }

    private List<OwnerVenueCourtDayDto.Block> blocksForDate(
            UUID venueId, UUID courtId, LocalDate date, ZoneId zone
    ) {
        short dow = (short) (date.getDayOfWeek().getValue() % 7);
        List<RecurringTimeBlock> applicable =
                recurringTimeBlockRepository.findApplicableForDate(venueId, courtId, dow, date);
        List<OwnerVenueCourtDayDto.Block> blocks = new ArrayList<>(applicable.size());
        for (RecurringTimeBlock rb : applicable) {
            LocalTime startTime = rb.getStartTime();
            LocalTime endTime = rb.getEndTime();
            boolean overnight = !endTime.isAfter(startTime);
            ZonedDateTime startsLocal = date.atTime(startTime).atZone(zone);
            ZonedDateTime endsLocal = date.plusDays(overnight ? 1 : 0).atTime(endTime).atZone(zone);
            blocks.add(new OwnerVenueCourtDayDto.Block(
                    rb.getId(),
                    startsLocal.toOffsetDateTime(),
                    endsLocal.toOffsetDateTime(),
                    rb.getReason()
            ));
        }
        return blocks;
    }

    private List<OwnerVenueCourtDayDto.Reservation> reservationsForCourtDay(
            UUID courtId, String courtSurfaceType, OffsetDateTime rangeStart, OffsetDateTime rangeEnd
    ) {
        return bookingRepository.findCourtDayReservations(courtId, rangeStart, rangeEnd).stream()
                .map(p -> new OwnerVenueCourtDayDto.Reservation(
                        p.getId(),
                        p.getStartsAt(),
                        p.getEndsAt(),
                        courtSurfaceType,
                        p.getStatus(),
                        normalizePaymentStatus(p.getPaymentStatus()),
                        p.getAmountCents(),
                        p.getBookingType(),
                        new OwnerVenueCourtDayDto.Customer(p.getUserName(), p.getUserPhone()),
                        p.getNote()
                ))
                .toList();
    }

    private static String normalizePaymentStatus(String status) {
        if (status == null) return null;
        return switch (status) {
            case Payment.STATUS_RECEIVED -> "paid";
            case Payment.STATUS_PENDING -> "pending";
            case Payment.STATUS_OVERDUE -> "overdue";
            case Payment.STATUS_REFUND_REQUESTED -> "refund_requested";
            case Payment.STATUS_REFUND_DENIED -> "refund_denied";
            case Payment.STATUS_REFUNDED -> "refunded";
            case Payment.STATUS_DELETED -> "deleted";
            default -> status.toLowerCase();
        };
    }

    private static LocalDate parseDate(String dateParam) {
        if (dateParam == null || dateParam.isBlank()) {
            throw new UnprocessableEntityException("invalid_date", "date is required (YYYY-MM-DD)");
        }
        try {
            return LocalDate.parse(dateParam, ISO_DATE);
        } catch (DateTimeParseException e) {
            throw new UnprocessableEntityException("invalid_date", "date must be in YYYY-MM-DD format");
        }
    }

    private ZoneId resolveVenueZone(Venue venue) {
        City city = cityRepository.findById(venue.getCityId())
                .orElseThrow(() -> new NotFoundException("City not found"));
        return ZoneId.of(city.getTimezone());
    }

    private static UUID currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return UUID.fromString(auth.getName());
    }
}
