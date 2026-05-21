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
@Table(name = "account_merge_events", schema = "public")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AccountMergeEvent {

    public static final String TARGET_KIND_PHONE = "phone";
    public static final String TARGET_KIND_EMAIL = "email";

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(name = "primary_user_id", columnDefinition = "uuid")
    private UUID primaryUserId;

    @Column(name = "secondary_user_id", columnDefinition = "uuid")
    private UUID secondaryUserId;

    @Column(name = "target_identifier", nullable = false)
    private String targetIdentifier;

    @Column(name = "target_kind", nullable = false)
    private String targetKind;

    @Column(name = "merge_acknowledged", nullable = false)
    private Boolean mergeAcknowledged;

    @Column(name = "bookings_moved", nullable = false)
    private Integer bookingsMoved;

    @Column(name = "refresh_tokens_revoked", nullable = false)
    private Integer refreshTokensRevoked;

    @Column(name = "secondary_email")
    private String secondaryEmail;

    @Column(name = "secondary_phone_e164")
    private String secondaryPhoneE164;

    @Column(name = "secondary_google_id")
    private String secondaryGoogleId;

    @Column(name = "secondary_asaas_customer_id")
    private String secondaryAsaasCustomerId;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private OffsetDateTime createdAt;
}
