package com.bookingsquadra.repository;

import com.bookingsquadra.entity.Booking;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

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

    Page<Booking> findByUserIdAndEndsAtAfterOrderByStartsAtAsc(
            UUID userId,
            OffsetDateTime now,
            Pageable pageable
    );

    Page<Booking> findByUserIdAndEndsAtBeforeOrderByStartsAtDesc(
            UUID userId,
            OffsetDateTime now,
            Pageable pageable
    );

    Optional<Booking> findByIdAndUserId(UUID id, UUID userId);

    Optional<Booking> findByTransactionId(String transactionId);
}
