package com.bookingsquadra.repository;

import com.bookingsquadra.entity.UserOtp;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

public interface UserOtpRepository extends JpaRepository<UserOtp, UUID> {

    Optional<UserOtp> findFirstByUserIdAndPurposeAndOtpCodeAndUsedAtIsNullAndExpiresAtAfterOrderByCreatedAtDesc(
            UUID userId,
            String purpose,
            String otpCode,
            OffsetDateTime now
    );

    Optional<UserOtp> findFirstByUserIdAndPurposeAndUsedAtIsNullAndExpiresAtAfterOrderByCreatedAtDesc(
            UUID userId,
            String purpose,
            OffsetDateTime now
    );

    boolean existsByUserIdAndPurposeAndUsedAtIsNullAndExpiresAtAfterAndCreatedAtAfter(
            UUID userId,
            String purpose,
            OffsetDateTime expiresAt,
            OffsetDateTime createdAt
    );
}
