package com.bookingsquadra.repository;

import com.bookingsquadra.entity.RecurringTimeBlockException;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface RecurringTimeBlockExceptionRepository
        extends JpaRepository<RecurringTimeBlockException, UUID> {

    List<RecurringTimeBlockException> findByRecurringBlockIdInAndExceptionDate(
            Collection<UUID> recurringBlockIds, LocalDate exceptionDate);
}
