package com.bookingsquadra.repository;

import com.bookingsquadra.entity.AuthRateLimitEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.net.InetAddress;
import java.time.OffsetDateTime;
import java.util.UUID;

public interface AuthRateLimitEventRepository extends JpaRepository<AuthRateLimitEvent, UUID> {

    long countByActionAndEmailAndCreatedAtAfter(String action, String email, OffsetDateTime createdAt);

    long countByActionAndIpAddressAndCreatedAtAfter(String action, InetAddress ipAddress, OffsetDateTime createdAt);

    long countByActionAndEmailAndIpAddressAndCreatedAtAfter(
            String action,
            String email,
            InetAddress ipAddress,
            OffsetDateTime createdAt
    );

    long deleteByCreatedAtBefore(OffsetDateTime createdAt);
}
