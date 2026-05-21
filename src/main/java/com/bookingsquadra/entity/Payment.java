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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "payments", schema = "public")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Payment {

    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_RECEIVED = "RECEIVED";
    public static final String STATUS_OVERDUE = "OVERDUE";
    public static final String STATUS_DELETED = "DELETED";
    public static final String STATUS_REFUND_REQUESTED = "REFUND_REQUESTED";
    public static final String STATUS_REFUND_DENIED = "REFUND_DENIED";
    public static final String STATUS_REFUNDED = "REFUNDED";

    public static final String BILLING_TYPE_PIX = "PIX";

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(name = "booking_id", nullable = false, unique = true, columnDefinition = "uuid")
    private UUID bookingId;

    @Column(name = "asaas_payment_id", nullable = false, unique = true)
    private String asaasPaymentId;

    @Column(name = "asaas_customer_id", nullable = false)
    private String asaasCustomerId;

    @Column(name = "billing_type", nullable = false)
    private String billingType;

    @Column(name = "amount_cents", nullable = false)
    private Integer amountCents;

    @Column(nullable = false)
    private String status;

    @Column(name = "invoice_url")
    private String invoiceUrl;

    @Column(name = "pix_payload")
    private String pixPayload;

    @Column(name = "pix_qr_image")
    private String pixQrImage;

    @Column(name = "pix_expires_at")
    private OffsetDateTime pixExpiresAt;

    @Column(name = "due_date", nullable = false)
    private LocalDate dueDate;

    @Column(name = "expires_at", nullable = false)
    private OffsetDateTime expiresAt;

    @Column(name = "refunded_at")
    private OffsetDateTime refundedAt;

    @Column(name = "refund_amount_cents")
    private Integer refundAmountCents;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "raw_payload", columnDefinition = "jsonb")
    private Map<String, Object> rawPayload;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    private OffsetDateTime updatedAt;
}
