package com.bookingsquadra.scheduler;

import com.bookingsquadra.service.PaymentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class BookingExpirationScheduler {

    private static final Logger log = LoggerFactory.getLogger(BookingExpirationScheduler.class);

    private final PaymentService paymentService;

    public BookingExpirationScheduler(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @Scheduled(fixedDelayString = "PT1M", initialDelayString = "PT30S")
    public void sweepExpiredBookings() {
        try {
            int cancelled = paymentService.cancelExpiredBookings();
            if (cancelled > 0) {
                log.info("Booking expiration sweep cancelled {} bookings", cancelled);
            }
        } catch (RuntimeException e) {
            log.error("Booking expiration sweep failed", e);
        }
    }
}
