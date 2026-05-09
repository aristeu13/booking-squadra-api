package com.bookingsquadra.service;

import com.bookingsquadra.dto.OwnerBookingDto;
import com.bookingsquadra.dto.OwnerVenueSummaryDto;
import com.bookingsquadra.dto.RevenueReportDto;
import com.bookingsquadra.entity.City;
import com.bookingsquadra.entity.Venue;
import com.bookingsquadra.exception.NotFoundException;
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
import java.util.List;
import java.util.UUID;

@Service
public class OwnerVenueService {

    private final VenueRepository venueRepository;
    private final BookingRepository bookingRepository;
    private final PaymentRepository paymentRepository;
    private final CityRepository cityRepository;

    public OwnerVenueService(
            VenueRepository venueRepository,
            BookingRepository bookingRepository,
            PaymentRepository paymentRepository,
            CityRepository cityRepository
    ) {
        this.venueRepository = venueRepository;
        this.bookingRepository = bookingRepository;
        this.paymentRepository = paymentRepository;
        this.cityRepository = cityRepository;
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
