package com.bookingsquadra.repository;

import com.bookingsquadra.entity.OperatingHours;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OperatingHoursRepository extends JpaRepository<OperatingHours, UUID> {

    Optional<OperatingHours> findByVenueIdAndDayOfWeek(UUID venueId, Short dayOfWeek);

    List<OperatingHours> findByVenueId(UUID venueId);
}
