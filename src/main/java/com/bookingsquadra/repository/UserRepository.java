package com.bookingsquadra.repository;

import com.bookingsquadra.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {
    Optional<User> findByEmail(String email);

    Optional<User> findFirstByEmailIgnoreCase(String email);

    Optional<User> findFirstByPhoneE164(String phoneE164);

    Optional<User> findByGoogleId(String googleId);
}
