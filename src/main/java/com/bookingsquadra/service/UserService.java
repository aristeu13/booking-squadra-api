package com.bookingsquadra.service;

import com.bookingsquadra.dto.ProfileDto;
import com.bookingsquadra.dto.UpdateProfileDto;
import com.bookingsquadra.entity.User;
import com.bookingsquadra.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@Service
public class UserService {

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public ProfileDto getCurrent() {
        return toDto(findCurrentOrThrow());
    }

    @Transactional
    public ProfileDto updateCurrent(UpdateProfileDto body) {
        User user = findCurrentOrThrow();
        if (body.name() != null) {
            user.setName(body.name());
        }
        if (body.phone() != null) {
            user.setPhone(body.phone());
        }
        return toDto(userRepository.save(user));
    }

    @Transactional
    public void deleteCurrent() {
        User user = findCurrentOrThrow();
        user.setName("Deleted User");
        user.setEmail(null);
        user.setPhone(null);
        user.setHasUsedGoogleAuth(false);
        userRepository.save(user);
    }

    public User findCurrentOrThrow() {
        return userRepository.findById(currentUserId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
    }

    private static UUID currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }
        try {
            return UUID.fromString(auth.getName());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid token subject");
        }
    }

    private static ProfileDto toDto(User u) {
        return new ProfileDto(u.getId(), u.getName(), u.getEmail(), u.getPhone(), u.getHasUsedGoogleAuth());
    }
}
