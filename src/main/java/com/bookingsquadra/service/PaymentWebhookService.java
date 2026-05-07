package com.bookingsquadra.service;

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
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Service
public class PaymentWebhookService {

    private static final Logger log = LoggerFactory.getLogger(PaymentWebhookService.class);

    private static final String BOOKING_STATUS_CONFIRMED = "confirmed";
    private static final String BOOKING_STATUS_CANCELLED = "cancelled";
    private static final String PAYMENT_METHOD_PIX = "pix";

    private final BookingRepository bookingRepository;
    private final PaymentRepository paymentRepository;
    private final ProcessedWebhookEventRepository processedWebhookEventRepository;

    public PaymentWebhookService(
            BookingRepository bookingRepository,
            PaymentRepository paymentRepository,
            ProcessedWebhookEventRepository processedWebhookEventRepository
    ) {
        this.bookingRepository = bookingRepository;
        this.paymentRepository = paymentRepository;
        this.processedWebhookEventRepository = processedWebhookEventRepository;
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

        if (outcome.bookingStatus == null) {
            markProcessed(eventId);
            return;
        }
        bookingRepository.findById(payment.getBookingId()).ifPresent(booking -> {
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
        });
        markProcessed(eventId);
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
