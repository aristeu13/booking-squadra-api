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
@Table(name = "cancel_policies", schema = "public")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CancelPolicy {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(name = "venue_id", nullable = false, unique = true, columnDefinition = "uuid")
    private UUID venueId;

    @Column(name = "pix_full_refund_hours", nullable = false)
    private Short pixFullRefundHours;

    @Column(name = "pix_partial_refund_hours", nullable = false)
    private Short pixPartialRefundHours;

    @Column(name = "pix_partial_refund_percent", nullable = false)
    private Short pixPartialRefundPercent;

    @Column(name = "local_cancel_hours", nullable = false)
    private Short localCancelHours;

    @Column(name = "no_show_pix_threshold", nullable = false)
    private Short noShowPixThreshold;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    private OffsetDateTime updatedAt;
}
