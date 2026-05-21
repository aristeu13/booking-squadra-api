package com.bookingsquadra.scheduler;

import com.bookingsquadra.service.VenuePayoutService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class VenuePayoutScheduler {

    private static final Logger log = LoggerFactory.getLogger(VenuePayoutScheduler.class);

    private final VenuePayoutService venuePayoutService;

    public VenuePayoutScheduler(VenuePayoutService venuePayoutService) {
        this.venuePayoutService = venuePayoutService;
    }

    @Scheduled(fixedDelayString = "PT2M", initialDelayString = "PT1M")
    public void dispatchDuePayouts() {
        try {
            int sent = venuePayoutService.processDuePayouts();
            if (sent > 0) {
                log.info("Venue payout sweep dispatched {} payouts", sent);
            }
        } catch (RuntimeException e) {
            log.error("Venue payout sweep failed", e);
        }
    }
}
