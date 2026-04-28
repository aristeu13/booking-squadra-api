package com.bookingsquadra.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(
        name = "recurring_time_block_exceptions",
        schema = "public",
        uniqueConstraints = @UniqueConstraint(columnNames = {"recurring_block_id", "exception_date"})
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RecurringTimeBlockException {

    public static final String ACTION_RELEASE = "release";
    public static final String ACTION_BLOCK = "block";

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(name = "recurring_block_id", nullable = false, columnDefinition = "uuid")
    private UUID recurringBlockId;

    @Column(name = "exception_date", nullable = false)
    private LocalDate exceptionDate;

    @Column(nullable = false)
    private String action;

    private String note;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    private OffsetDateTime updatedAt;
}
