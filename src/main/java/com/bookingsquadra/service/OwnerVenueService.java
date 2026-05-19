package com.bookingsquadra.service;

import com.bookingsquadra.dto.OwnerBookingDto;
import com.bookingsquadra.dto.OwnerVenueDayOverviewDto;
import com.bookingsquadra.dto.OwnerVenueSummaryDto;
import com.bookingsquadra.dto.RevenueReportDto;
import com.bookingsquadra.entity.City;
import com.bookingsquadra.entity.Venue;
import com.bookingsquadra.exception.NotFoundException;
import com.bookingsquadra.exception.UnprocessableEntityException;
import com.bookingsquadra.repository.BookingRepository;
import com.bookingsquadra.repository.CityRepository;
import com.bookingsquadra.repository.PaymentRepository;
import com.bookingsquadra.repository.VenueRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class OwnerVenueService {

    private static final DateTimeFormatter ISO_DATE = DateTimeFormatter.ofPattern("uuuu-MM-dd")
            .withResolverStyle(ResolverStyle.STRICT);

    private final VenueRepository venueRepository;
    private final BookingRepository bookingRepository;
    private final PaymentRepository paymentRepository;
    private final CityRepository cityRepository;
    private final CourtAvailabilityService courtAvailabilityService;

    public OwnerVenueService(
            VenueRepository venueRepository,
            BookingRepository bookingRepository,
            PaymentRepository paymentRepository,
            CityRepository cityRepository,
            CourtAvailabilityService courtAvailabilityService
    ) {
        this.venueRepository = venueRepository;
        this.bookingRepository = bookingRepository;
        this.paymentRepository = paymentRepository;
        this.cityRepository = cityRepository;
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
    public RevenueReportDto getRevenue(UUID venueId, LocalDate from, LocalDate to) {
        Venue venue = venueRepository.findById(venueId)
                .orElseThrow(() -> new NotFoundException("Venue not found"));

        ZoneId zone = resolveVenueZone(venue);
        OffsetDateTime rangeStart = from.atStartOfDay(zone).toOffsetDateTime();
        OffsetDateTime rangeEnd   = to.plusDays(1).atStartOfDay(zone).toOffsetDateTime();

        var agg = paymentRepository.aggregateRevenueForVenue(venueId, rangeStart, rangeEnd);
        long gross    = agg == null ? 0L : agg.getGrossCents();
        long refunded = agg == null ? 0L : agg.getRefundedCents();
        long count    = agg == null ? 0L : agg.getPaidCount();

        return new RevenueReportDto(venueId, from, to, count, gross, refunded, gross - refunded);
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
