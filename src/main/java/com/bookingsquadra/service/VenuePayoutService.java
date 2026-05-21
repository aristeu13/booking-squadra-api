package com.bookingsquadra.service;

import com.bookingsquadra.client.asaas.AsaasClient;
import com.bookingsquadra.client.asaas.AsaasTransferRequest;
import com.bookingsquadra.client.asaas.AsaasTransferResponse;
import com.bookingsquadra.config.AsaasProperties;
import com.bookingsquadra.dto.MarkPayoutSettledDto;
import com.bookingsquadra.dto.VenuePayoutDto;
import com.bookingsquadra.entity.Booking;
import com.bookingsquadra.entity.CancelPolicy;
import com.bookingsquadra.entity.Court;
import com.bookingsquadra.entity.Venue;
import com.bookingsquadra.entity.VenuePayout;
import com.bookingsquadra.exception.ConflictException;
import com.bookingsquadra.repository.BookingRepository;
import com.bookingsquadra.repository.CancelPolicyRepository;
import com.bookingsquadra.repository.CourtRepository;
import com.bookingsquadra.repository.VenuePayoutRepository;
import com.bookingsquadra.repository.VenueRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class VenuePayoutService {

    private static final Logger log = LoggerFactory.getLogger(VenuePayoutService.class);
    private static final String TRANSFER_OPERATION_PIX = "PIX";
    private static final String BOOKING_STATUS_CONFIRMED = "confirmed";

    private final AsaasClient asaasClient;
    private final AsaasProperties asaasProperties;
    private final BookingRepository bookingRepository;
    private final CancelPolicyRepository cancelPolicyRepository;
    private final CourtRepository courtRepository;
    private final VenueRepository venueRepository;
    private final VenuePayoutRepository venuePayoutRepository;

    public VenuePayoutService(
            AsaasClient asaasClient,
            AsaasProperties asaasProperties,
            BookingRepository bookingRepository,
            CancelPolicyRepository cancelPolicyRepository,
            CourtRepository courtRepository,
            VenueRepository venueRepository,
            VenuePayoutRepository venuePayoutRepository
    ) {
        this.asaasClient = asaasClient;
        this.asaasProperties = asaasProperties;
        this.bookingRepository = bookingRepository;
        this.cancelPolicyRepository = cancelPolicyRepository;
        this.courtRepository = courtRepository;
        this.venueRepository = venueRepository;
        this.venuePayoutRepository = venuePayoutRepository;
    }

    @Transactional
    public void schedule(Booking booking) {
        Optional<VenuePayout> existing = venuePayoutRepository.findByBookingId(booking.getId());
        if (existing.isPresent()) {
            return;
        }
        Court court = courtRepository.findById(booking.getCourtId()).orElse(null);
        if (court == null) {
            log.warn("Cannot schedule payout for booking {}: court missing", booking.getId());
            return;
        }
        Venue venue = venueRepository.findById(court.getVenueId()).orElse(null);
        if (venue == null) {
            log.warn("Cannot schedule payout for booking {}: venue missing", booking.getId());
            return;
        }
        if (venue.getPixKey() == null || venue.getPixKey().isBlank()
                || venue.getPixKeyType() == null || venue.getPixKeyType().isBlank()) {
            log.warn("Cannot schedule payout for booking {}: venue {} has no PIX key configured",
                    booking.getId(), venue.getId());
            return;
        }
        CancelPolicy policy = cancelPolicyRepository.findByVenueId(venue.getId()).orElse(null);
        if (policy == null) {
            log.warn("Cannot schedule payout for booking {}: cancel policy missing for venue {}",
                    booking.getId(), venue.getId());
            return;
        }

        int payoutAmountCents = booking.getAmountCents() - asaasProperties.mainAccountShareCentsOrDefault();
        if (payoutAmountCents <= 0) {
            log.warn("Cannot schedule payout for booking {}: amount {} too low for platform share",
                    booking.getId(), booking.getAmountCents());
            return;
        }

        OffsetDateTime scheduledFor = booking.getStartsAt().minusHours(policy.getPixPartialRefundHours());

        VenuePayout payout = VenuePayout.builder()
                .bookingId(booking.getId())
                .venueId(venue.getId())
                .amountCents(payoutAmountCents)
                .pixKey(venue.getPixKey())
                .pixKeyType(venue.getPixKeyType())
                .scheduledFor(scheduledFor)
                .status(VenuePayout.STATUS_SCHEDULED)
                .build();
        venuePayoutRepository.save(payout);
        log.info("Scheduled payout {} for booking {} at {}", payout.getId(), booking.getId(), scheduledFor);
    }

    @Transactional
    public void cancelForBooking(UUID bookingId) {
        venuePayoutRepository.findByBookingId(bookingId).ifPresent(payout -> {
            if (!VenuePayout.STATUS_SCHEDULED.equals(payout.getStatus())) {
                return;
            }
            payout.setStatus(VenuePayout.STATUS_CANCELLED);
            venuePayoutRepository.save(payout);
            log.info("Cancelled scheduled payout {} for booking {}", payout.getId(), bookingId);
        });
    }

    @Transactional
    public int processDuePayouts() {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        List<VenuePayout> due = venuePayoutRepository
                .findByStatusAndScheduledForBefore(VenuePayout.STATUS_SCHEDULED, now);
        int sent = 0;
        for (VenuePayout payout : due) {
            if (attemptSend(payout)) {
                sent++;
            }
        }
        return sent;
    }

    @Transactional(readOnly = true)
    public List<VenuePayoutDto> listByStatus(String status) {
        if (status == null || status.isBlank()) {
            return venuePayoutRepository.findAll().stream().map(VenuePayoutService::toDto).toList();
        }
        return venuePayoutRepository.findByStatusOrderByScheduledForAsc(status.toUpperCase())
                .stream().map(VenuePayoutService::toDto).toList();
    }

    @Transactional(readOnly = true)
    public VenuePayoutDto get(UUID payoutId) {
        return toDto(loadOrThrow(payoutId));
    }

    /**
     * Re-fires the Asaas transfer using the same idempotency key.
     * If Asaas already processed the original attempt, it returns the original result and we flip to SENT;
     * otherwise the transfer actually executes now. Safe to call on FAILED or SCHEDULED rows.
     */
    @Transactional
    public VenuePayoutDto reconcile(UUID payoutId) {
        VenuePayout payout = loadOrThrow(payoutId);
        if (VenuePayout.STATUS_SENT.equals(payout.getStatus())) {
            return toDto(payout);
        }
        if (VenuePayout.STATUS_CANCELLED.equals(payout.getStatus())) {
            throw new ConflictException("payout_cancelled",
                    "Payout is cancelled; cannot reconcile a cancelled payout.");
        }
        attemptSend(payout);
        return toDto(payout);
    }

    /**
     * Records that the payout was settled out-of-band (e.g., manual PIX from the bank app).
     * Idempotent on already-SENT rows; refuses on CANCELLED rows.
     */
    @Transactional
    public VenuePayoutDto markSettled(UUID payoutId, MarkPayoutSettledDto body) {
        VenuePayout payout = loadOrThrow(payoutId);
        if (VenuePayout.STATUS_SENT.equals(payout.getStatus())) {
            return toDto(payout);
        }
        if (VenuePayout.STATUS_CANCELLED.equals(payout.getStatus())) {
            throw new ConflictException("payout_cancelled",
                    "Payout is cancelled; cannot mark a cancelled payout as settled.");
        }
        payout.setStatus(VenuePayout.STATUS_SENT);
        payout.setSentAt(OffsetDateTime.now(ZoneOffset.UTC));
        if (body != null) {
            if (body.manualTransferReference() != null && !body.manualTransferReference().isBlank()) {
                payout.setAsaasTransferId("MANUAL:" + body.manualTransferReference().trim());
            }
            String note = body == null ? null : body.note();
            payout.setFailureReason(note == null || note.isBlank() ? null : "Marked settled: " + note);
        } else {
            payout.setFailureReason(null);
        }
        venuePayoutRepository.save(payout);
        log.info("Payout {} (booking {}) marked settled out-of-band", payout.getId(), payout.getBookingId());
        return toDto(payout);
    }

    private boolean attemptSend(VenuePayout payout) {
        Booking booking = bookingRepository.findById(payout.getBookingId()).orElse(null);
        if (booking == null || !BOOKING_STATUS_CONFIRMED.equals(booking.getStatus())) {
            String reason = booking == null
                    ? "Booking record missing at dispatch"
                    : "Booking status " + booking.getStatus() + " at dispatch";
            log.warn("Skipping payout {} for booking {}: {}",
                    payout.getId(), payout.getBookingId(), reason);
            payout.setStatus(VenuePayout.STATUS_CANCELLED);
            payout.setFailureReason(reason);
            venuePayoutRepository.save(payout);
            return false;
        }

        BigDecimal value = BigDecimal.valueOf(payout.getAmountCents(), 2);
        AsaasTransferRequest request = new AsaasTransferRequest(
                value,
                payout.getPixKey(),
                payout.getPixKeyType(),
                TRANSFER_OPERATION_PIX,
                "Repasse reserva " + payout.getBookingId(),
                payout.getBookingId().toString()
        );
        String idempotencyKey = "payout_" + payout.getId();
        try {
            AsaasTransferResponse response = asaasClient.createTransfer(request, idempotencyKey);
            payout.setStatus(VenuePayout.STATUS_SENT);
            payout.setAsaasTransferId(response == null ? null : response.id());
            payout.setSentAt(OffsetDateTime.now(ZoneOffset.UTC));
            payout.setFailureReason(null);
            venuePayoutRepository.save(payout);
            log.info("Sent payout {} (booking {}) transfer={}",
                    payout.getId(), payout.getBookingId(),
                    response == null ? null : response.id());
            return true;
        } catch (RuntimeException e) {
            payout.setStatus(VenuePayout.STATUS_FAILED);
            payout.setFailureReason(truncate(e.getMessage()));
            venuePayoutRepository.save(payout);
            log.error("Payout {} (booking {}) failed: {}",
                    payout.getId(), payout.getBookingId(), e.toString());
            return false;
        }
    }

    private VenuePayout loadOrThrow(UUID payoutId) {
        return venuePayoutRepository.findById(payoutId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Payout not found"));
    }

    private static VenuePayoutDto toDto(VenuePayout p) {
        return new VenuePayoutDto(
                p.getId(),
                p.getBookingId(),
                p.getVenueId(),
                p.getAmountCents(),
                p.getPixKey(),
                p.getPixKeyType(),
                p.getScheduledFor(),
                p.getStatus(),
                p.getAsaasTransferId(),
                p.getSentAt(),
                p.getFailureReason(),
                p.getCreatedAt(),
                p.getUpdatedAt()
        );
    }

    private static String truncate(String value) {
        if (value == null) {
            return null;
        }
        return value.length() > 500 ? value.substring(0, 500) : value;
    }
}
