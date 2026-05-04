package com.bookingsquadra.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "bookings", schema = "public")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Booking {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(name = "booking_type", nullable = false)
    private String bookingType;

    @Column(name = "user_id", columnDefinition = "uuid")
    private UUID userId;

    @Column(name = "court_id", nullable = false, columnDefinition = "uuid")
    private UUID courtId;

    @Column(name = "starts_at", nullable = false)
    private OffsetDateTime startsAt;

    @Column(name = "ends_at", nullable = false)
    private OffsetDateTime endsAt;

    @Column(name = "venue_timezone", nullable = false)
    private String venueTimezone;

    @Column(nullable = false)
    private String status;

    @Column(name = "payment_method")
    private String paymentMethod;

    @Column(name = "amount_cents", nullable = false)
    private Integer amountCents;

    private String note;

    @Column(name = "cancelled_at")
    private OffsetDateTime cancelledAt;

    @Column(name = "cancel_reason")
    private String cancelReason;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    private OffsetDateTime updatedAt;
}
