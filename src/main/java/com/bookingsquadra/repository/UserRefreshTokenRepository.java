package com.bookingsquadra.repository;

import com.bookingsquadra.entity.UserRefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

public interface UserRefreshTokenRepository extends JpaRepository<UserRefreshToken, UUID> {

    Optional<UserRefreshToken> findByTokenHashAndRevokedAtIsNullAndExpiresAtAfter(
            String tokenHash,
            OffsetDateTime now
    );
}
