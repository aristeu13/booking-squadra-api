package com.bookingsquadra.repository;

import com.bookingsquadra.entity.VenuePayout;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface VenuePayoutRepository extends JpaRepository<VenuePayout, UUID> {

    Optional<VenuePayout> findByBookingId(UUID bookingId);

    List<VenuePayout> findByStatusAndScheduledForBefore(String status, OffsetDateTime cutoff);

    List<VenuePayout> findByStatusOrderByScheduledForAsc(String status);
}
