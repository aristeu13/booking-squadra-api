package com.bookingsquadra.repository;

import com.bookingsquadra.entity.VenueOwner;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface VenueOwnerRepository extends JpaRepository<VenueOwner, VenueOwner.PK> {

    boolean existsByUserIdAndVenueId(UUID userId, UUID venueId);

    @Query("SELECT vo.venueId FROM VenueOwner vo WHERE vo.userId = :userId")
    List<UUID> findVenueIdsByUserId(@Param("userId") UUID userId);
}
