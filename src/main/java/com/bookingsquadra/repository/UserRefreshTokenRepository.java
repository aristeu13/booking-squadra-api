package com.bookingsquadra.repository;

import com.bookingsquadra.entity.UserRefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

public interface UserRefreshTokenRepository extends JpaRepository<UserRefreshToken, UUID> {

    Optional<UserRefreshToken> findByTokenHashAndRevokedAtIsNullAndExpiresAtAfter(
            String tokenHash,
            OffsetDateTime now
    );

    @Modifying
    @Query("""
            UPDATE UserRefreshToken t
               SET t.revokedAt = :revokedAt
             WHERE t.userId = :userId
               AND t.revokedAt IS NULL
            """)
    int revokeAllForUser(@Param("userId") UUID userId, @Param("revokedAt") OffsetDateTime revokedAt);
}
