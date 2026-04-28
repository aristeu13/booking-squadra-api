package com.bookingsquadra.repository;

import com.bookingsquadra.entity.RecurringTimeBlock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface RecurringTimeBlockRepository extends JpaRepository<RecurringTimeBlock, UUID> {

    List<RecurringTimeBlock> findByVenueIdAndDayOfWeekAndActiveTrue(UUID venueId, Short dayOfWeek);

    // Mirrors the booking-time check in BookingRepository.existsRecurringBlockOverlap:
    //   - block applies if active + day_of_week matches AND no 'release' exception, OR
    //   - block applies if there is an explicit 'block' exception for the date.
    @Query(value = """
            SELECT DISTINCT rb.*
            FROM public.recurring_time_blocks rb
            LEFT JOIN public.recurring_time_block_exceptions rbe
                   ON rbe.recurring_block_id = rb.id
                  AND rbe.exception_date     = CAST(:date AS date)
            WHERE rb.venue_id = :venueId
              AND (rb.court_id IS NULL OR rb.court_id = :courtId)
              AND (
                  (
                      rb.active
                      AND rb.day_of_week = :dow
                      AND COALESCE(rbe.action, 'block') <> 'release'
                  )
                  OR COALESCE(rbe.action, '') = 'block'
              )
            """, nativeQuery = true)
    List<RecurringTimeBlock> findApplicableForDate(
            @Param("venueId") UUID venueId,
            @Param("courtId") UUID courtId,
            @Param("dow") short dow,
            @Param("date") LocalDate date
    );
}
