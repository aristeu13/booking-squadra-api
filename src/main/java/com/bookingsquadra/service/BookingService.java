package com.bookingsquadra.service;

import com.bookingsquadra.config.AsaasProperties;
import com.bookingsquadra.dto.AppointmentDto;
import com.bookingsquadra.dto.BookingDto;
import com.bookingsquadra.dto.CancelBookingDto;
import com.bookingsquadra.dto.CancelBookingRequestDto;
import com.bookingsquadra.dto.CreateBookingDto;
import com.bookingsquadra.dto.RefundResponseDto;
import com.bookingsquadra.entity.Booking;
import com.bookingsquadra.entity.CancelPolicy;
import com.bookingsquadra.entity.City;
import com.bookingsquadra.entity.Court;
import com.bookingsquadra.entity.User;
import com.bookingsquadra.entity.Venue;
import com.bookingsquadra.exception.ConflictException;
import com.bookingsquadra.repository.BookingRepository;
import com.bookingsquadra.repository.CancelPolicyRepository;
import com.bookingsquadra.repository.CityRepository;
import com.bookingsquadra.repository.CourtRepository;
import com.bookingsquadra.repository.VenueRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

@Service
public class BookingService {

    private static final String BOOKING_TYPE_RESERVATION = "reservation";
    private static final String STATUS_PENDING = "pending";
    private static final String STATUS_CANCELLED = "cancelled";
    private static final String PAYMENT_METHOD_PIX = "pix";

    private final BookingRepository bookingRepository;
    private final CancelPolicyRepository cancelPolicyRepository;
    private final CityRepository cityRepository;
    private final CourtRepository courtRepository;
    private final VenueRepository venueRepository;
    private final UserService userService;
    private final CourtAvailabilityService courtAvailabilityService;
    private final PaymentService paymentService;
    private final AsaasProperties asaasProperties;

    public BookingService(
            BookingRepository bookingRepository,
            CancelPolicyRepository cancelPolicyRepository,
            CityRepository cityRepository,
            CourtRepository courtRepository,
            VenueRepository venueRepository,
            UserService userService,
            CourtAvailabilityService courtAvailabilityService,
            PaymentService paymentService,
            AsaasProperties asaasProperties) {
        this.bookingRepository = bookingRepository;
        this.cancelPolicyRepository = cancelPolicyRepository;
        this.cityRepository = cityRepository;
        this.courtRepository = courtRepository;
        this.venueRepository = venueRepository;
        this.userService = userService;
        this.courtAvailabilityService = courtAvailabilityService;
        this.paymentService = paymentService;
        this.asaasProperties = asaasProperties;
    }

    @Transactional
    public BookingDto create(CreateBookingDto body) {
        User user = userService.findCurrentOrThrow();

        bookingRepository
                .findFirstByUserIdAndStatusAndStartsAtAfterOrderByCreatedAtDesc(
                        user.getId(), STATUS_PENDING, OffsetDateTime.now(ZoneOffset.UTC))
                .ifPresent(existing -> {
                    throw new ConflictException("pending_booking_exists",
                            "You already have a pending booking. Please pay it or cancel it before creating a new one.");
                });

        CourtAvailabilityService.ValidatedSlot validated = courtAvailabilityService.validateBookingSlot(
                body.courtId(), body.bookingDate(), body.startTime(), body.endTime());

        Venue venue = validated.venue();
        int amountCents = Math.multiplyExact(venue.getPriceCents(), validated.slotCount());

        OffsetDateTime expiresAt = OffsetDateTime.now(ZoneOffset.UTC)
                .plusMinutes(asaasProperties.bookingGraceMinutesOrDefault());

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
                .expiresAt(expiresAt)
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

    @Transactional(readOnly = true)
    public Page<AppointmentDto> getCurrentUserAppointments(String status, int page, int pageSize) {
        User user = userService.findCurrentOrThrow();
        PageRequest pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(pageSize, 1), 100));
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        Page<Booking> bookings = switch (status == null ? "upcoming" : status) {
            case "upcoming" -> bookingRepository
                    .findByUserIdAndEndsAtAfterOrderByStartsAtAsc(user.getId(), now, pageable);
            case "past" -> bookingRepository
                    .findByUserIdAndEndsAtBeforeOrderByStartsAtDesc(user.getId(), now, pageable);
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "status must be upcoming or past");
        };
        return bookings.map(this::toAppointmentDto);
    }

    @Transactional(readOnly = true)
    public AppointmentDto getCurrentUserBooking(UUID bookingId) {
        User user = userService.findCurrentOrThrow();
        Booking booking = bookingRepository.findByIdAndUserId(bookingId, user.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Booking not found"));
        return toAppointmentDto(booking);
    }

    @Transactional(readOnly = true)
    public Optional<AppointmentDto> getCurrentUserPendingBooking() {
        User user = userService.findCurrentOrThrow();
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        return bookingRepository
                .findFirstByUserIdAndStatusAndStartsAtAfterOrderByCreatedAtDesc(
                        user.getId(), STATUS_PENDING, now)
                .map(this::toAppointmentDto);
    }

    @Transactional
    public void deleteCurrentUserPendingBooking(UUID bookingId) {
        User user = userService.findCurrentOrThrow();
        Booking booking = bookingRepository.findByIdAndUserId(bookingId, user.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Booking not found"));
        if (STATUS_CANCELLED.equals(booking.getStatus())) {
            return;
        }
        if (!STATUS_PENDING.equals(booking.getStatus())) {
            throw new ConflictException("not_pending",
                    "Only pending bookings can be deleted. Cancel confirmed bookings instead.");
        }

        if (PAYMENT_METHOD_PIX.equals(booking.getPaymentMethod())) {
            paymentService.cancelPendingPayment(booking);
        }

        booking.setStatus(STATUS_CANCELLED);
        booking.setCancelledAt(OffsetDateTime.now(ZoneOffset.UTC));
        booking.setCancelReason("user_deleted_pending");
        bookingRepository.save(booking);
    }

    @Transactional
    public CancelBookingDto cancelCurrentUserBooking(UUID bookingId, CancelBookingRequestDto request) {
        User user = userService.findCurrentOrThrow();
        Booking booking = bookingRepository.findByIdAndUserId(bookingId, user.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Booking not found"));
        if (STATUS_CANCELLED.equals(booking.getStatus())) {
            return new CancelBookingDto(
                    booking.getId(),
                    true,
                    0,
                    0,
                    "already_cancelled",
                    "Reserva ja estava cancelada.");
        }
        if (STATUS_PENDING.equals(booking.getStatus())) {
            throw new ConflictException("pending_booking",
                    "Pending bookings cannot be cancelled. Delete it instead.");
        }
        if (!booking.getStartsAt().isAfter(OffsetDateTime.now(ZoneOffset.UTC))) {
            return new CancelBookingDto(
                    booking.getId(),
                    false,
                    0,
                    0,
                    "not_cancellable",
                    "Reserva nao pode ser cancelada apos o horario de inicio.");
        }

        Court court = courtOrThrow(booking.getCourtId());
        Venue venue = venueOrThrow(court.getVenueId());
        CancelPolicy policy = cancelPolicyRepository.findByVenueId(venue.getId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.INTERNAL_SERVER_ERROR, "Cancel policy not found"));
        CancelOutcome outcome = calculateCancelOutcome(booking, policy);

        booking.setStatus(STATUS_CANCELLED);
        booking.setCancelledAt(OffsetDateTime.now(ZoneOffset.UTC));
        booking.setCancelReason(request == null ? null : request.reason());
        bookingRepository.save(booking);

        if (PAYMENT_METHOD_PIX.equals(booking.getPaymentMethod())) {
            RefundResponseDto refund = paymentService.refundForCancellation(
                    booking, outcome.refundPercent());
            if (refund != null) {
                return new CancelBookingDto(
                        booking.getId(),
                        true,
                        outcome.refundPercent(),
                        refund.netRefundCents(),
                        outcome.paymentImpact(),
                        outcome.message());
            }
        }

        return new CancelBookingDto(
                booking.getId(),
                true,
                outcome.refundPercent(),
                outcome.refundAmountCents(),
                outcome.paymentImpact(),
                outcome.message());
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

    private AppointmentDto toAppointmentDto(Booking booking) {
        Court court = courtOrThrow(booking.getCourtId());
        Venue venue = venueOrThrow(court.getVenueId());
        City city = cityRepository.findById(venue.getCityId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.INTERNAL_SERVER_ERROR, "Venue city not found"));
        ZoneId venueZone = ZoneId.of(booking.getVenueTimezone());
        return new AppointmentDto(
                booking.getId(),
                booking.getStatus(),
                booking.getBookingType(),
                booking.getStartsAt(),
                booking.getEndsAt(),
                booking.getVenueTimezone(),
                booking.getStartsAt().atZoneSameInstant(venueZone).toLocalDate(),
                booking.getStartsAt().atZoneSameInstant(venueZone).toLocalTime(),
                booking.getEndsAt().atZoneSameInstant(venueZone).toLocalTime(),
                booking.getAmountCents(),
                booking.getPaymentMethod(),
                venue.getId(),
                venue.getName(),
                venue.getSlug(),
                venue.getAddress(),
                city.getName(),
                city.getStateCode(),
                court.getId(),
                court.getName(),
                court.getSurfaceType(),
                booking.getNote(),
                booking.getExpiresAt());
    }

    private CancelOutcome calculateCancelOutcome(Booking booking, CancelPolicy policy) {
        long hoursUntilStart = Duration.between(
                OffsetDateTime.now(ZoneOffset.UTC),
                booking.getStartsAt()).toHours();
        if (PAYMENT_METHOD_PIX.equals(booking.getPaymentMethod())) {
            if (hoursUntilStart >= policy.getPixFullRefundHours()) {
                return refundOutcome(booking, 100, "pix_full_refund",
                        "Reserva cancelada. Reembolso de 90% aplicado.");
            }
            if (hoursUntilStart >= policy.getPixPartialRefundHours()) {
                int refundPercent = policy.getPixPartialRefundPercent();
                return refundOutcome(booking, refundPercent, "pix_partial_refund",
                        "Reserva cancelada. Reembolso parcial de " + refundPercent + "%.");
            }
            return refundOutcome(booking, 0, "pix_no_refund",
                    "Reserva cancelada sem reembolso pela politica do local.");
        }
        if (hoursUntilStart >= policy.getLocalCancelHours()) {
            return refundOutcome(booking, 0, "local_cancel_allowed",
                    "Reserva cancelada dentro do prazo permitido.");
        }
        return refundOutcome(booking, 0, "local_late_cancel",
                "Reserva cancelada fora do prazo da politica do local. Você não poderá agendar outro horário neste local sem pagamento antecipado.");
    }

    private static CancelOutcome refundOutcome(
            Booking booking,
            int refundPercent,
            String paymentImpact,
            String message) {
        int refundAmountCents = Math.floorDiv(booking.getAmountCents() * refundPercent, 100);
        return new CancelOutcome(refundPercent, refundAmountCents, paymentImpact, message);
    }

    private Court courtOrThrow(UUID courtId) {
        return courtRepository.findById(courtId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Court not found"));
    }

    private Venue venueOrThrow(UUID venueId) {
        return venueRepository.findById(venueId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Venue not found"));
    }

    private record CancelOutcome(
            int refundPercent,
            int refundAmountCents,
            String paymentImpact,
            String message) {
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
                b.getNote(),
                b.getExpiresAt());
    }
}
