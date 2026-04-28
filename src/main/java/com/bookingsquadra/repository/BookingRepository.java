package com.bookingsquadra.repository;

import com.bookingsquadra.entity.Booking;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface BookingRepository extends JpaRepository<Booking, UUID> {

    @Query(value = """
            SELECT EXISTS (
                SELECT 1 FROM public.bookings
                WHERE court_id     = :courtId
                  AND booking_date = :date
                  AND status IN ('pending', 'confirmed')
                  AND start_time < :endTime
                  AND end_time   > :startTime
            )
            """, nativeQuery = true)
    boolean existsBookingOverlap(
            @Param("courtId") UUID courtId,
            @Param("date") LocalDate date,
            @Param("startTime") LocalTime startTime,
            @Param("endTime") LocalTime endTime
    );

    @Query(value = """
            SELECT EXISTS (
                SELECT 1
                FROM public.recurring_time_blocks rb
                JOIN public.courts c ON c.id = :courtId
                LEFT JOIN public.recurring_time_block_exceptions rbe
                       ON rbe.recurring_block_id = rb.id
                      AND rbe.exception_date     = :date
                WHERE rb.venue_id = c.venue_id
                  AND rb.start_time < :endTime
                  AND rb.end_time   > :startTime
                  AND (
                      (
                          rb.active
                          AND rb.day_of_week = EXTRACT(isodow FROM CAST(:date AS date))::int % 7
                          AND (rb.court_id IS NULL OR rb.court_id = :courtId)
                          AND COALESCE(rbe.action, 'block') <> 'release'
                      )
                      OR (
                          COALESCE(rbe.action, '') = 'block'
                          AND (rb.court_id IS NULL OR rb.court_id = :courtId)
                      )
                  )
            )
            """, nativeQuery = true)
    boolean existsRecurringBlockOverlap(
            @Param("courtId") UUID courtId,
            @Param("date") LocalDate date,
            @Param("startTime") LocalTime startTime,
            @Param("endTime") LocalTime endTime
    );

    List<Booking> findByCourtIdAndBookingDateInAndStatusIn(
            UUID courtId,
            Collection<LocalDate> bookingDates,
            Collection<String> statuses
    );
}
