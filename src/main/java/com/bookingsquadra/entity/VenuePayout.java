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
@Table(name = "venue_payouts", schema = "public")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VenuePayout {

    public static final String STATUS_SCHEDULED = "SCHEDULED";
    public static final String STATUS_SENT = "SENT";
    public static final String STATUS_FAILED = "FAILED";
    public static final String STATUS_CANCELLED = "CANCELLED";

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(name = "booking_id", nullable = false, unique = true, columnDefinition = "uuid")
    private UUID bookingId;

    @Column(name = "venue_id", nullable = false, columnDefinition = "uuid")
    private UUID venueId;

    @Column(name = "amount_cents", nullable = false)
    private Integer amountCents;

    @Column(name = "pix_key", nullable = false)
    private String pixKey;

    @Column(name = "pix_key_type", nullable = false)
    private String pixKeyType;

    @Column(name = "scheduled_for", nullable = false)
    private OffsetDateTime scheduledFor;

    @Column(nullable = false)
    private String status;

    @Column(name = "asaas_transfer_id")
    private String asaasTransferId;

    @Column(name = "sent_at")
    private OffsetDateTime sentAt;

    @Column(name = "failure_reason")
    private String failureReason;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    private OffsetDateTime updatedAt;
}
