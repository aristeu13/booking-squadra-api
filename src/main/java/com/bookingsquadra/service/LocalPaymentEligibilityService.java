package com.bookingsquadra.service;

import com.bookingsquadra.dto.LocalPaymentEligibilityDto;
import com.bookingsquadra.entity.CancelPolicy;
import com.bookingsquadra.repository.BookingRepository;
import com.bookingsquadra.repository.CancelPolicyRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Decides whether a user has earned the right to pay locally (cash on arrival) at a venue.
 *
 * <p>Rule: a user is eligible once they have at least
 * {@code cancel_policies.no_show_pix_threshold} past PIX-prepaid bookings at the venue since the
 * most recent "reset event". A reset event is either a no-show (owner-marked) or a late
 * cancellation on a local-payment booking. The reset starts the count over from zero.
 */
@Service
public class LocalPaymentEligibilityService {

    private final CancelPolicyRepository cancelPolicyRepository;
    private final BookingRepository bookingRepository;

    public LocalPaymentEligibilityService(
            CancelPolicyRepository cancelPolicyRepository,
            BookingRepository bookingRepository
    ) {
        this.cancelPolicyRepository = cancelPolicyRepository;
        this.bookingRepository = bookingRepository;
    }

    @Transactional(readOnly = true)
    public LocalPaymentEligibilityDto evaluate(UUID userId, UUID venueId) {
        CancelPolicy policy = cancelPolicyRepository.findByVenueId(venueId).orElse(null);
        if (policy == null) {
            return new LocalPaymentEligibilityDto(venueId, 0, 0L, false);
        }
        short threshold = policy.getNoShowPixThreshold();
        long count = bookingRepository.countSuccessfulPrePaidSinceReset(
                userId, venueId, policy.getLocalCancelHours());
        boolean eligible = count >= threshold;
        return new LocalPaymentEligibilityDto(venueId, threshold, count, eligible);
    }
}
