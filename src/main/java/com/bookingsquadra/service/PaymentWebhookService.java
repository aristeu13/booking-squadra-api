package com.bookingsquadra.service;

import com.bookingsquadra.entity.Booking;
import com.bookingsquadra.repository.BookingRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class PaymentWebhookService {

    private static final Logger log = LoggerFactory.getLogger(PaymentWebhookService.class);

    private static final String EVENT_PAYMENT_CREATED = "PAYMENT_CREATED";
    private static final String EVENT_PAYMENT_RECEIVED = "PAYMENT_RECEIVED";
    private static final String PAYMENT_METHOD_PIX = "pix";
    private static final String STATUS_CONFIRMED = "confirmed";

    private final BookingRepository bookingRepository;

    public PaymentWebhookService(BookingRepository bookingRepository) {
        this.bookingRepository = bookingRepository;
    }

    @Transactional
    public void handle(String event, Map<?, ?> payment) {
        if (event == null || payment == null) {
            log.warn("Ignoring payment webhook with missing event or payment");
            return;
        }

        switch (event) {
            case EVENT_PAYMENT_CREATED -> linkPayment(payment);
            case EVENT_PAYMENT_RECEIVED -> confirmPayment(payment);
            default -> log.info("Ignoring unsupported payment webhook event {}", event);
        }
    }

    private void linkPayment(Map<?, ?> payment) {
        String paymentId = stringValue(payment.get("id"));
        if (paymentId == null) {
            log.warn("Ignoring payment creation webhook without payment id");
            return;
        }

        findBooking(payment)
                .ifPresentOrElse(booking -> {
                    if (booking.getTransactionId() == null || booking.getTransactionId().isBlank()) {
                        booking.setTransactionId(paymentId);
                    }
                    booking.setPaymentMethod(PAYMENT_METHOD_PIX);
                    bookingRepository.save(booking);
                }, () -> log.warn("Payment creation webhook could not resolve booking for payment {}", paymentId));
    }

    private void confirmPayment(Map<?, ?> payment) {
        String paymentId = stringValue(payment.get("id"));
        if (paymentId == null) {
            log.warn("Ignoring payment received webhook without payment id");
            return;
        }

        findBooking(payment)
                .ifPresentOrElse(booking -> {
                    booking.setTransactionId(paymentId);
                    booking.setPaymentMethod(PAYMENT_METHOD_PIX);
                    booking.setStatus(STATUS_CONFIRMED);
                    bookingRepository.save(booking);
                }, () -> log.warn("Payment received webhook could not resolve booking for payment {}", paymentId));
    }

    private Optional<Booking> findBooking(Map<?, ?> payment) {
        String paymentId = stringValue(payment.get("id"));
        if (paymentId != null) {
            Optional<Booking> byTransactionId = bookingRepository.findByTransactionId(paymentId);
            if (byTransactionId.isPresent()) {
                return byTransactionId;
            }
        }

        String externalReference = stringValue(payment.get("externalReference"));
        if (externalReference == null) {
            externalReference = stringValue(payment.get("external_reference"));
        }
        if (externalReference == null) {
            return Optional.empty();
        }

        try {
            return bookingRepository.findById(UUID.fromString(externalReference));
        } catch (IllegalArgumentException e) {
            log.warn("Payment webhook external reference is not a booking UUID: {}", externalReference);
            return Optional.empty();
        }
    }

    private static String stringValue(Object value) {
        if (value == null) {
            return null;
        }
        String text = value.toString();
        return text.isBlank() ? null : text;
    }
}
