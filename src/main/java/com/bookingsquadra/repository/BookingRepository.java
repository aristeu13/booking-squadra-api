package com.bookingsquadra.repository;

import com.bookingsquadra.entity.Booking;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BookingRepository extends JpaRepository<Booking, UUID> {

    List<Booking> findByCourtIdAndStatusInAndStartsAtBeforeAndEndsAtAfter(
            UUID courtId,
            Collection<String> statuses,
            OffsetDateTime rangeEnd,
            OffsetDateTime rangeStart
    );

    @Query("""
            SELECT b FROM Booking b
            WHERE b.userId = :userId
              AND b.status <> 'cancelled'
              AND b.endsAt > :asOf
            ORDER BY b.startsAt ASC, b.id ASC
            """)
    Page<Booking> findUpcomingForUser(
            @Param("userId") UUID userId,
            @Param("asOf") OffsetDateTime asOf,
            Pageable pageable
    );

    @Query("""
            SELECT b FROM Booking b
            WHERE b.userId = :userId
              AND (b.status = 'cancelled' OR b.endsAt <= :asOf)
            ORDER BY b.endsAt DESC, b.id DESC
            """)
    Page<Booking> findPastForUser(
            @Param("userId") UUID userId,
            @Param("asOf") OffsetDateTime asOf,
            Pageable pageable
    );

    Optional<Booking> findByIdAndUserId(UUID id, UUID userId);

    Optional<Booking> findFirstByUserIdAndStatusAndStartsAtAfterOrderByCreatedAtDesc(
            UUID userId,
            String status,
            OffsetDateTime now
    );

    List<Booking> findByStatusAndExpiresAtBefore(String status, OffsetDateTime cutoff);

    @Query(value = """
            SELECT
                b.id              AS id,
                b.starts_at       AS "startsAt",
                b.ends_at         AS "endsAt",
                b.venue_timezone  AS timezone,
                b.status          AS status,
                b.booking_type    AS "bookingType",
                b.amount_cents    AS "amountCents",
                b.payment_method  AS "paymentMethod",
                p.status          AS "paymentStatus",
                c.id              AS "courtId",
                c.name            AS "courtName",
                u.id              AS "userId",
                u.name            AS "userName",
                u.email           AS "userEmail",
                u.phone           AS "userPhone"
            FROM public.bookings b
            JOIN public.courts c ON c.id = b.court_id
            LEFT JOIN public.users u   ON u.id = b.user_id
            LEFT JOIN public.payments p ON p.booking_id = b.id
            WHERE c.venue_id = :venueId
              AND b.starts_at < :rangeEnd
              AND b.ends_at   > :rangeStart
              AND (CAST(:status AS text) IS NULL OR b.status = CAST(:status AS text))
            ORDER BY b.starts_at ASC, b.id ASC
            """,
            countQuery = """
            SELECT COUNT(*)
            FROM public.bookings b
            JOIN public.courts c ON c.id = b.court_id
            WHERE c.venue_id = :venueId
              AND b.starts_at < :rangeEnd
              AND b.ends_at   > :rangeStart
              AND (CAST(:status AS text) IS NULL OR b.status = CAST(:status AS text))
            """,
            nativeQuery = true)
    Page<OwnerBookingProjection> findVenueBookingsForOwner(
            @Param("venueId") UUID venueId,
            @Param("rangeStart") OffsetDateTime rangeStart,
            @Param("rangeEnd") OffsetDateTime rangeEnd,
            @Param("status") String status,
            Pageable pageable
    );

    interface OwnerBookingProjection {
        UUID getId();
        OffsetDateTime getStartsAt();
        OffsetDateTime getEndsAt();
        String getTimezone();
        String getStatus();
        String getBookingType();
        Integer getAmountCents();
        String getPaymentMethod();
        String getPaymentStatus();
        UUID getCourtId();
        String getCourtName();
        UUID getUserId();
        String getUserName();
        String getUserEmail();
        String getUserPhone();
    }
}
