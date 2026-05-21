package com.bookingsquadra.service;

import com.bookingsquadra.dto.email.TemplateEmailRequest;
import com.bookingsquadra.entity.Booking;
import com.bookingsquadra.entity.Payment;
import com.bookingsquadra.entity.ProcessedWebhookEvent;
import com.bookingsquadra.repository.BookingRepository;
import com.bookingsquadra.repository.PaymentRepository;
import com.bookingsquadra.repository.ProcessedWebhookEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Service
public class PaymentWebhookService {

    private static final Logger log = LoggerFactory.getLogger(PaymentWebhookService.class);

    private static final String BOOKING_STATUS_PENDING = "pending";
    private static final String BOOKING_STATUS_CONFIRMED = "confirmed";
    private static final String BOOKING_STATUS_CANCELLED = "cancelled";
    private static final String PAYMENT_METHOD_PIX = "pix";

    private final BookingRepository bookingRepository;
    private final PaymentRepository paymentRepository;
    private final ProcessedWebhookEventRepository processedWebhookEventRepository;
    private final BookingNotificationDataLoader bookingNotificationDataLoader;
    private final BookingEmailPayloadMapper bookingEmailPayloadMapper;
    private final TransactionalEmailService transactionalEmailService;
    private final VenuePayoutService venuePayoutService;

    public PaymentWebhookService(
            BookingRepository bookingRepository,
            PaymentRepository paymentRepository,
            ProcessedWebhookEventRepository processedWebhookEventRepository,
            BookingNotificationDataLoader bookingNotificationDataLoader,
            BookingEmailPayloadMapper bookingEmailPayloadMapper,
            TransactionalEmailService transactionalEmailService,
            VenuePayoutService venuePayoutService
    ) {
        this.bookingRepository = bookingRepository;
        this.paymentRepository = paymentRepository;
        this.processedWebhookEventRepository = processedWebhookEventRepository;
        this.bookingNotificationDataLoader = bookingNotificationDataLoader;
        this.bookingEmailPayloadMapper = bookingEmailPayloadMapper;
        this.transactionalEmailService = transactionalEmailService;
        this.venuePayoutService = venuePayoutService;
    }

    @Transactional
    public void handle(String eventId, String event, Map<?, ?> payload) {
        if (event == null || payload == null) {
            log.warn("Ignoring payment webhook with missing event or payment");
            return;
        }
        if (eventId != null && processedWebhookEventRepository.existsById(eventId)) {
            log.info("Skipping already-processed webhook event {}", eventId);
            return;
        }

        Outcome outcome = switch (event) {
            case "PAYMENT_CREATED" -> Outcome.noop();
            case "PAYMENT_RECEIVED", "PAYMENT_CONFIRMED" ->
                    Outcome.of(Payment.STATUS_RECEIVED, BOOKING_STATUS_CONFIRMED);
            case "PAYMENT_OVERDUE" ->
                    Outcome.of(Payment.STATUS_OVERDUE, BOOKING_STATUS_CANCELLED);
            case "PAYMENT_DELETED" ->
                    Outcome.of(Payment.STATUS_DELETED, BOOKING_STATUS_CANCELLED);
            case "PAYMENT_REFUND_IN_PROGRESS" ->
                    Outcome.of(Payment.STATUS_REFUND_REQUESTED, null);
            case "PAYMENT_REFUNDED" ->
                    Outcome.of(Payment.STATUS_REFUNDED, BOOKING_STATUS_CANCELLED);
            case "PAYMENT_REFUND_DENIED" ->
                    Outcome.of(Payment.STATUS_REFUND_DENIED, null);
            default -> {
                log.info("Ignoring unsupported payment webhook event {}", event);
                yield Outcome.skipped();
            }
        };

        if (outcome.skip) {
            markProcessed(eventId);
            return;
        }

        Optional<Payment> paymentOpt = findPayment(payload);
        if (paymentOpt.isEmpty()) {
            log.warn("Payment webhook {} could not resolve payment from payload {}", event, payload.get("id"));
            return;
        }

        Payment payment = paymentOpt.get();
        String previousPaymentStatus = payment.getStatus();

        boolean payloadCaptured = capturePayload(payment, event, payload);
        boolean paymentChanged = false;

        if (outcome.paymentStatus != null && !outcome.paymentStatus.equals(payment.getStatus())) {
            payment.setStatus(outcome.paymentStatus);
            paymentChanged = true;
        }
        if (Payment.STATUS_REFUNDED.equals(payment.getStatus()) && payment.getRefundedAt() == null) {
            payment.setRefundedAt(OffsetDateTime.now(ZoneOffset.UTC));
            paymentChanged = true;
        }
        if (paymentChanged || payloadCaptured) {
            paymentRepository.save(payment);
        }

        List<TemplateEmailRequest> emailQueue = new ArrayList<>();

        if (outcome.bookingStatus == null) {
            bookingRepository.findById(payment.getBookingId()).ifPresent(booking ->
                    appendPaymentOnlyEmails(emailQueue, event, payment, previousPaymentStatus, booking));
            markProcessed(eventId);
            flushEmailQueue(emailQueue);
            return;
        }

        bookingRepository.findById(payment.getBookingId()).ifPresent(booking -> {
            String previousBookingStatus = booking.getStatus();
            boolean bookingChanged = false;
            if (!Objects.equals(booking.getStatus(), outcome.bookingStatus)) {
                booking.setStatus(outcome.bookingStatus);
                bookingChanged = true;
            }
            if (BOOKING_STATUS_CONFIRMED.equals(outcome.bookingStatus)
                    && !PAYMENT_METHOD_PIX.equals(booking.getPaymentMethod())) {
                booking.setPaymentMethod(PAYMENT_METHOD_PIX);
                bookingChanged = true;
            }
            if (BOOKING_STATUS_CANCELLED.equals(outcome.bookingStatus)
                    && booking.getCancelledAt() == null) {
                booking.setCancelledAt(OffsetDateTime.now(ZoneOffset.UTC));
                booking.setCancelReason(cancelReasonForEvent(event));
                bookingChanged = true;
            }
            if (bookingChanged) {
                bookingRepository.save(booking);
            }

            if (BOOKING_STATUS_CONFIRMED.equals(outcome.bookingStatus)
                    && BOOKING_STATUS_PENDING.equals(previousBookingStatus)) {
                venuePayoutService.schedule(booking);
            }
            if (BOOKING_STATUS_CANCELLED.equals(outcome.bookingStatus)) {
                venuePayoutService.cancelForBooking(booking.getId());
            }

            appendBookingScopedEmails(emailQueue, event, outcome, payment, previousPaymentStatus, booking, previousBookingStatus);
        });

        markProcessed(eventId);
        flushEmailQueue(emailQueue);
    }

    private void flushEmailQueue(List<TemplateEmailRequest> emailQueue) {
        for (TemplateEmailRequest request : emailQueue) {
            transactionalEmailService.scheduleAfterCommit(request);
        }
    }

    private void appendPaymentOnlyEmails(
            List<TemplateEmailRequest> out,
            String event,
            Payment payment,
            String previousPaymentStatus,
            Booking booking
    ) {
        maybeAppendRefundInProgress(out, event, payment, previousPaymentStatus, booking);
        maybeAppendRefundDenied(out, event, payment, previousPaymentStatus, booking);
    }

    private void appendBookingScopedEmails(
            List<TemplateEmailRequest> out,
            String event,
            Outcome outcome,
            Payment payment,
            String previousPaymentStatus,
            Booking booking,
            String previousBookingStatus
    ) {
        maybeAppendPaymentConfirmed(out, event, outcome, booking, previousBookingStatus);
        maybeAppendPrereservationCancelled(out, event, outcome, booking, previousBookingStatus);
        maybeAppendRefunded(out, event, payment, previousPaymentStatus, booking);
    }

    private void maybeAppendPaymentConfirmed(
            List<TemplateEmailRequest> out,
            String event,
            Outcome outcome,
            Booking booking,
            String previousBookingStatus
    ) {
        if (!"PAYMENT_RECEIVED".equals(event) && !"PAYMENT_CONFIRMED".equals(event)) {
            return;
        }
        if (!BOOKING_STATUS_CONFIRMED.equals(outcome.bookingStatus)) {
            return;
        }
        if (!BOOKING_STATUS_PENDING.equals(previousBookingStatus)) {
            return;
        }
        bookingNotificationDataLoader.load(booking)
                .ifPresent(d -> out.add(bookingEmailPayloadMapper.paymentConfirmed(d)));
    }

    private void maybeAppendPrereservationCancelled(
            List<TemplateEmailRequest> out,
            String event,
            Outcome outcome,
            Booking booking,
            String previousBookingStatus
    ) {
        if (!"PAYMENT_DELETED".equals(event) && !"PAYMENT_OVERDUE".equals(event)) {
            return;
        }
        if (!BOOKING_STATUS_CANCELLED.equals(outcome.bookingStatus)) {
            return;
        }
        if (!BOOKING_STATUS_PENDING.equals(previousBookingStatus)) {
            return;
        }
        if (!isPixBooking(booking)) {
            return;
        }
        bookingNotificationDataLoader.load(booking)
                .ifPresent(d -> out.add(bookingEmailPayloadMapper.prereservationCancelled(d)));
    }

    private void maybeAppendRefunded(
            List<TemplateEmailRequest> out,
            String event,
            Payment payment,
            String previousPaymentStatus,
            Booking booking
    ) {
        if (!"PAYMENT_REFUNDED".equals(event)) {
            return;
        }
        if (!isPixBooking(booking)) {
            return;
        }
        if (Payment.STATUS_REFUNDED.equals(previousPaymentStatus)) {
            return;
        }
        if (!Payment.STATUS_REFUNDED.equals(payment.getStatus())) {
            return;
        }
        int cents = BookingEmailPayloadMapper.resolveRefundDisplayCents(payment);
        bookingNotificationDataLoader.load(booking)
                .ifPresent(d -> out.add(bookingEmailPayloadMapper.refunded(d, cents)));
    }

    private void maybeAppendRefundInProgress(
            List<TemplateEmailRequest> out,
            String event,
            Payment payment,
            String previousPaymentStatus,
            Booking booking
    ) {
        if (!"PAYMENT_REFUND_IN_PROGRESS".equals(event)) {
            return;
        }
        if (!isPixBooking(booking)) {
            return;
        }
        if (Payment.STATUS_REFUND_REQUESTED.equals(previousPaymentStatus)) {
            return;
        }
        if (!Payment.STATUS_REFUND_REQUESTED.equals(payment.getStatus())) {
            return;
        }
        int cents = BookingEmailPayloadMapper.resolveRefundDisplayCents(payment);
        bookingNotificationDataLoader.load(booking)
                .ifPresent(d -> out.add(bookingEmailPayloadMapper.refundInProgress(d, cents)));
    }

    private void maybeAppendRefundDenied(
            List<TemplateEmailRequest> out,
            String event,
            Payment payment,
            String previousPaymentStatus,
            Booking booking
    ) {
        if (!"PAYMENT_REFUND_DENIED".equals(event)) {
            return;
        }
        if (!isPixBooking(booking)) {
            return;
        }
        if (Payment.STATUS_REFUND_DENIED.equals(previousPaymentStatus)) {
            return;
        }
        if (!Payment.STATUS_REFUND_DENIED.equals(payment.getStatus())) {
            return;
        }
        bookingNotificationDataLoader.load(booking)
                .ifPresent(d -> out.add(bookingEmailPayloadMapper.refundDenied(d)));
    }

    private static boolean isPixBooking(Booking booking) {
        return booking.getPaymentMethod() != null
                && PAYMENT_METHOD_PIX.equalsIgnoreCase(booking.getPaymentMethod());
    }

    private void markProcessed(String eventId) {
        if (eventId == null) {
            return;
        }
        processedWebhookEventRepository.save(ProcessedWebhookEvent.builder()
                .eventId(eventId)
                .processedAt(OffsetDateTime.now(ZoneOffset.UTC))
                .build());
    }

    private Optional<Payment> findPayment(Map<?, ?> payload) {
        String paymentId = stringValue(payload.get("id"));
        if (paymentId != null) {
            Optional<Payment> byPaymentId = paymentRepository.findByAsaasPaymentId(paymentId);
            if (byPaymentId.isPresent()) {
                return byPaymentId;
            }
        }
        String externalReference = stringValue(payload.get("externalReference"));
        if (externalReference == null) {
            return Optional.empty();
        }
        try {
            UUID bookingId = UUID.fromString(externalReference);
            return paymentRepository.findByBookingId(bookingId);
        } catch (IllegalArgumentException e) {
            log.warn("Payment webhook external reference is not a booking UUID: {}", externalReference);
            return Optional.empty();
        }
    }

    private static boolean capturePayload(Payment payment, String event, Map<?, ?> payload) {
        Map<String, Object> snapshot = new HashMap<>();
        snapshot.put("event", event);
        snapshot.put("payment", payload);
        if (Objects.equals(payment.getRawPayload(), snapshot)) {
            return false;
        }
        payment.setRawPayload(snapshot);
        return true;
    }

    private static String cancelReasonForEvent(String event) {
        return switch (event) {
            case "PAYMENT_OVERDUE" -> "payment_overdue";
            case "PAYMENT_DELETED" -> "payment_deleted";
            case "PAYMENT_REFUNDED" -> "payment_refunded";
            default -> null;
        };
    }

    private static String stringValue(Object value) {
        if (value == null) {
            return null;
        }
        String text = value.toString();
        return text.isBlank() ? null : text;
    }

    private record Outcome(boolean skip, String paymentStatus, String bookingStatus) {
        static Outcome of(String paymentStatus, String bookingStatus) {
            return new Outcome(false, paymentStatus, bookingStatus);
        }

        static Outcome noop() {
            return new Outcome(false, null, null);
        }

        static Outcome skipped() {
            return new Outcome(true, null, null);
        }
    }
}
