package com.bookingsquadra.repository;

import com.bookingsquadra.entity.Court;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface CourtRepository extends JpaRepository<Court, UUID> {
}
