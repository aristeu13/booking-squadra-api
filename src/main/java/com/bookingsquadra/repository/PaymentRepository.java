package com.bookingsquadra.repository;

import com.bookingsquadra.entity.Payment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PaymentRepository extends JpaRepository<Payment, UUID> {

    Optional<Payment> findByBookingId(UUID bookingId);

    Optional<Payment> findByAsaasPaymentId(String asaasPaymentId);

    List<Payment> findByStatusAndExpiresAtBefore(String status, OffsetDateTime cutoff);
}
