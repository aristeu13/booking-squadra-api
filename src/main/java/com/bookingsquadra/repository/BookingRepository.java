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
}
