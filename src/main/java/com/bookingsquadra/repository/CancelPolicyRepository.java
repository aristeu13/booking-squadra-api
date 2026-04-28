package com.bookingsquadra.repository;

import com.bookingsquadra.entity.CancelPolicy;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface CancelPolicyRepository extends JpaRepository<CancelPolicy, UUID> {

    Optional<CancelPolicy> findByVenueId(UUID venueId);
}
