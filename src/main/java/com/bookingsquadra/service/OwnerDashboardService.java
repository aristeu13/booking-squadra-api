package com.bookingsquadra.service;

import com.bookingsquadra.dto.OwnerDashboardSummaryDto;
import com.bookingsquadra.entity.City;
import com.bookingsquadra.entity.Court;
import com.bookingsquadra.entity.OperatingHours;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.List;
import java.util.UUID;

@Service
public class OwnerDashboardService {

    private static final int MINUTES_PER_DAY = 24 * 60;
    private static final String CURRENCY_BRL = "BRL";
    private static final String COMPARE_PERIOD = "yesterday";
    private static final DateTimeFormatter ISO_DATE = DateTimeFormatter.ofPattern("uuuu-MM-dd")
            .withResolverStyle(ResolverStyle.STRICT);

    private final VenueRepository venueRepository;
    private final CityRepository cityRepository;
    private final CourtRepository courtRepository;
    private final OperatingHoursRepository operatingHoursRepository;
    private final RecurringTimeBlockRepository recurringTimeBlockRepository;
    private final BookingRepository bookingRepository;

    public OwnerDashboardService(
            VenueRepository venueRepository,
            CityRepository cityRepository,
            CourtRepository courtRepository,
            OperatingHoursRepository operatingHoursRepository,
            RecurringTimeBlockRepository recurringTimeBlockRepository,
            BookingRepository bookingRepository
    ) {
        this.venueRepository = venueRepository;
        this.cityRepository = cityRepository;
        this.courtRepository = courtRepository;
        this.operatingHoursRepository = operatingHoursRepository;
        this.recurringTimeBlockRepository = recurringTimeBlockRepository;
        this.bookingRepository = bookingRepository;
    }

    @Transactional(readOnly = true)
    public OwnerDashboardSummaryDto getSummary(UUID venueId, String dateParam) {
        Venue venue = venueRepository.findById(venueId)
                .orElseThrow(() -> new NotFoundException("venue_not_found", "Venue not found"));

        ZoneId zone = resolveVenueZone(venue);
        LocalDate date = parseDate(dateParam, zone);
        LocalDate yesterday = date.minusDays(1);

        DayMetrics today = computeDay(venue, zone, date);
        DayMetrics prior = computeDay(venue, zone, yesterday);

        OwnerDashboardSummaryDto.OccupancyCompare occupancyCompare = buildOccupancyCompare(today, prior);
        OwnerDashboardSummaryDto.RevenueCompare revenueCompare = buildRevenueCompare(today, prior);

        OwnerDashboardSummaryDto.Occupancy occupancy = new OwnerDashboardSummaryDto.Occupancy(
                today.rate(),
                today.bookedMinutes,
                today.availableMinutes,
                occupancyCompare
        );

        OwnerDashboardSummaryDto.Revenue revenue = new OwnerDashboardSummaryDto.Revenue(
                CURRENCY_BRL,
                today.confirmedCents,
                today.pendingCents,
                revenueCompare
        );

        return new OwnerDashboardSummaryDto(
                venueId,
                date,
                zone.getId(),
                OffsetDateTime.now(zone),
                occupancy,
                revenue
        );
    }

    private ZoneId resolveVenueZone(Venue venue) {
        City city = cityRepository.findById(venue.getCityId())
                .orElseThrow(() -> new NotFoundException("Venue city not found"));
        return ZoneId.of(city.getTimezone());
    }

    private static LocalDate parseDate(String dateParam, ZoneId zone) {
        if (dateParam == null || dateParam.isBlank()) {
            return LocalDate.now(zone);
        }
        try {
            return LocalDate.parse(dateParam, ISO_DATE);
        } catch (DateTimeParseException e) {
            throw new UnprocessableEntityException("invalid_date", "date must be in YYYY-MM-DD format");
        }
    }

    private DayMetrics computeDay(Venue venue, ZoneId zone, LocalDate date) {
        OffsetDateTime rangeStart = date.atStartOfDay(zone).toOffsetDateTime();
        OffsetDateTime rangeEnd = date.plusDays(1).atStartOfDay(zone).toOffsetDateTime();

        var agg = bookingRepository.aggregateDashboardForDay(venue.getId(), rangeStart, rangeEnd);
        long bookedMinutes = agg == null ? 0L : agg.getBookedMinutes();
        long confirmedCents = agg == null ? 0L : agg.getConfirmedCents();
        long pendingCents = agg == null ? 0L : agg.getPendingCents();

        long availableMinutes = computeAvailableMinutes(venue.getId(), date);
        return new DayMetrics(bookedMinutes, availableMinutes, confirmedCents, pendingCents);
    }

    private long computeAvailableMinutes(UUID venueId, LocalDate date) {
        List<Court> activeCourts = courtRepository.findByVenueIdAndActiveTrueOrderBySortOrderAsc(venueId);
        if (activeCourts.isEmpty()) {
            return 0L;
        }
        short dow = (short) (date.getDayOfWeek().getValue() % 7);
        OperatingHours hours = operatingHoursRepository
                .findByVenueIdAndDayOfWeek(venueId, dow)
                .orElse(null);
        if (hours == null) {
            return 0L;
        }
        int windowMinutes = windowMinutes(hours.getOpenTime(), hours.getCloseTime());
        if (windowMinutes <= 0) {
            return 0L;
        }

        long totalOpen = (long) windowMinutes * activeCourts.size();
        long totalBlocked = 0L;
        for (Court court : activeCourts) {
            List<RecurringTimeBlock> blocks = recurringTimeBlockRepository
                    .findApplicableForDate(venueId, court.getId(), dow, date);
            for (RecurringTimeBlock block : blocks) {
                totalBlocked += minutesBetween(block.getStartTime(), block.getEndTime());
            }
        }
        return Math.max(0L, totalOpen - totalBlocked);
    }

    private static int windowMinutes(LocalTime open, LocalTime close) {
        int openMin = open.getHour() * 60 + open.getMinute();
        int closeMin = close.getHour() * 60 + close.getMinute();
        if (closeMin > openMin) {
            return closeMin - openMin;
        }
        if (closeMin < openMin) {
            return MINUTES_PER_DAY - openMin + closeMin;
        }
        return 0;
    }

    private static int minutesBetween(LocalTime start, LocalTime end) {
        int startMin = start.getHour() * 60 + start.getMinute();
        int endMin = end.getHour() * 60 + end.getMinute();
        return Math.max(0, endMin - startMin);
    }

    private static OwnerDashboardSummaryDto.OccupancyCompare buildOccupancyCompare(DayMetrics today, DayMetrics prior) {
        double priorRate = prior.rate();
        double todayRate = today.rate();
        if (prior.availableMinutes == 0 || priorRate == 0.0) {
            return new OwnerDashboardSummaryDto.OccupancyCompare(COMPARE_PERIOD, priorRate, null, "flat");
        }
        double delta = round2((todayRate - priorRate) / priorRate);
        return new OwnerDashboardSummaryDto.OccupancyCompare(
                COMPARE_PERIOD, priorRate, delta, direction(delta));
    }

    private static OwnerDashboardSummaryDto.RevenueCompare buildRevenueCompare(DayMetrics today, DayMetrics prior) {
        if (prior.confirmedCents == 0L) {
            return new OwnerDashboardSummaryDto.RevenueCompare(
                    COMPARE_PERIOD, prior.confirmedCents, null, "flat");
        }
        double delta = round2(((double) today.confirmedCents - prior.confirmedCents) / prior.confirmedCents);
        return new OwnerDashboardSummaryDto.RevenueCompare(
                COMPARE_PERIOD, prior.confirmedCents, delta, direction(delta));
    }

    private static String direction(double deltaPct) {
        if (deltaPct > 0) return "up";
        if (deltaPct < 0) return "down";
        return "flat";
    }

    private static double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private record DayMetrics(long bookedMinutes, long availableMinutes, long confirmedCents, long pendingCents) {
        double rate() {
            if (availableMinutes <= 0) return 0.0;
            return (double) bookedMinutes / availableMinutes;
        }
    }
}
