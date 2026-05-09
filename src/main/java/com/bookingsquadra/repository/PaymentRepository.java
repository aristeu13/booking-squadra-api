package com.bookingsquadra.repository;

import com.bookingsquadra.entity.Payment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PaymentRepository extends JpaRepository<Payment, UUID> {

    Optional<Payment> findByBookingId(UUID bookingId);

    Optional<Payment> findByAsaasPaymentId(String asaasPaymentId);

    List<Payment> findByStatusAndExpiresAtBefore(String status, OffsetDateTime cutoff);

    @Query(value = """
            SELECT
                COUNT(*)                                           AS "paidCount",
                COALESCE(SUM(p.amount_cents), 0)                   AS "grossCents",
                COALESCE(SUM(COALESCE(p.refund_amount_cents, 0)),0) AS "refundedCents"
            FROM public.payments p
            JOIN public.bookings b ON b.id = p.booking_id
            JOIN public.courts   c ON c.id = b.court_id
            WHERE c.venue_id = :venueId
              AND p.status IN ('RECEIVED','REFUND_REQUESTED','REFUND_DENIED','REFUNDED')
              AND b.starts_at >= :rangeStart
              AND b.starts_at <  :rangeEnd
            """, nativeQuery = true)
    RevenueAggregateProjection aggregateRevenueForVenue(
            @Param("venueId") UUID venueId,
            @Param("rangeStart") OffsetDateTime rangeStart,
            @Param("rangeEnd") OffsetDateTime rangeEnd
    );

    interface RevenueAggregateProjection {
        long getPaidCount();
        long getGrossCents();
        long getRefundedCents();
    }
}
