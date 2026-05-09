package com.bookingsquadra.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "venue_owners", schema = "public")
@IdClass(VenueOwner.PK.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VenueOwner {

    @Id
    @Column(name = "user_id", nullable = false, columnDefinition = "uuid")
    private UUID userId;

    @Id
    @Column(name = "venue_id", nullable = false, columnDefinition = "uuid")
    private UUID venueId;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode
    public static class PK implements Serializable {
        private UUID userId;
        private UUID venueId;
    }
}
