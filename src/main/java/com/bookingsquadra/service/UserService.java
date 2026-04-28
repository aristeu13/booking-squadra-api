package com.bookingsquadra.service;

import com.bookingsquadra.dto.ProfileDto;
import com.bookingsquadra.dto.UpdateProfileDto;
import com.bookingsquadra.entity.User;
import com.bookingsquadra.entity.UserOtp;
import com.bookingsquadra.repository.UserOtpRepository;
import com.bookingsquadra.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.UUID;

@Service
public class UserService {

    private static final int OTP_TTL_MINUTES = 10;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserRepository userRepository;
    private final UserOtpRepository userOtpRepository;
    private final OtpEmailSender otpEmailSender;

    public UserService(
            UserRepository userRepository,
            UserOtpRepository userOtpRepository,
            OtpEmailSender otpEmailSender
    ) {
        this.userRepository = userRepository;
        this.userOtpRepository = userOtpRepository;
        this.otpEmailSender = otpEmailSender;
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
    public void requestDeleteAccountOtp() {
        User user = findCurrentOrThrow();
        String code = String.format("%06d", RANDOM.nextInt(1_000_000));
        userOtpRepository.save(UserOtp.builder()
                .userId(user.getId())
                .otpCode(code)
                .purpose(UserOtp.PURPOSE_DELETE_ACCOUNT)
                .expiresAt(OffsetDateTime.now().plusMinutes(OTP_TTL_MINUTES))
                .build());
        otpEmailSender.sendDeleteAccountOtp(user.getEmail(), code);
    }

    @Transactional
    public void deleteCurrent(String otpCode) {
        User user = findCurrentOrThrow();

        UserOtp otp = userOtpRepository
                .findFirstByUserIdAndPurposeAndOtpCodeAndUsedAtIsNullAndExpiresAtAfterOrderByCreatedAtDesc(
                        user.getId(),
                        UserOtp.PURPOSE_DELETE_ACCOUNT,
                        otpCode,
                        OffsetDateTime.now())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid or expired OTP"));

        otp.setUsedAt(OffsetDateTime.now());
        userOtpRepository.save(otp);

        user.setName("Deleted User");
        user.setEmail("deleted-" + user.getId() + "@deleted.invalid");
        user.setPhone(null);
        user.setHasUsedGoogleAuth(false);
        user.setStatus(User.STATUS_DELETED);
        userRepository.save(user);
    }

    public User findCurrentOrThrow() {
        User user = userRepository.findById(currentUserId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        if (User.STATUS_DELETED.equals(user.getStatus())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Account deleted");
        }
        return user;
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
