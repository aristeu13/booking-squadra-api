package com.bookingsquadra.service;

import com.bookingsquadra.dto.AuthTokenDto;
import com.bookingsquadra.entity.User;
import com.bookingsquadra.entity.UserOtp;
import com.bookingsquadra.repository.UserOtpRepository;
import com.bookingsquadra.repository.UserRepository;
import com.bookingsquadra.security.JwtUtil;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.security.SecureRandom;
import java.time.OffsetDateTime;

@Service
public class AuthService {

    private static final int OTP_TTL_MINUTES = 10;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final JwtUtil jwtUtil;
    private final UserRepository userRepository;
    private final UserOtpRepository userOtpRepository;
    private final OtpEmailSender otpEmailSender;

    public AuthService(
            JwtUtil jwtUtil,
            UserRepository userRepository,
            UserOtpRepository userOtpRepository,
            OtpEmailSender otpEmailSender
    ) {
        this.jwtUtil = jwtUtil;
        this.userRepository = userRepository;
        this.userOtpRepository = userOtpRepository;
        this.otpEmailSender = otpEmailSender;
    }

    @Transactional
    public void requestOtp(String email) {
        User user = userRepository.findByEmail(email)
                .orElseGet(() -> userRepository.save(User.builder()
                        .name("")
                        .email(email)
                        .hasUsedGoogleAuth(false)
                        .role(User.ROLE_USER)
                        .status(User.STATUS_ACTIVE)
                        .build()));

        String code = generateCode();
        userOtpRepository.save(UserOtp.builder()
                .userId(user.getId())
                .otpCode(code)
                .purpose(UserOtp.PURPOSE_LOGIN)
                .expiresAt(OffsetDateTime.now().plusMinutes(OTP_TTL_MINUTES))
                .build());

        otpEmailSender.sendLoginOtp(email, code);
    }

    @Transactional
    public AuthTokenDto verifyOtp(String email, String code) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials"));

        UserOtp otp = userOtpRepository
                .findFirstByUserIdAndPurposeAndOtpCodeAndUsedAtIsNullAndExpiresAtAfterOrderByCreatedAtDesc(
                        user.getId(),
                        UserOtp.PURPOSE_LOGIN,
                        code,
                        OffsetDateTime.now())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid or expired OTP"));

        otp.setUsedAt(OffsetDateTime.now());
        userOtpRepository.save(otp);

        return new AuthTokenDto(jwtUtil.generateToken(user.getId().toString(), user.getRole()));
    }

    public void signOut() {
        // Stateless JWT: sign-out is a client-side concern (drop the token).
    }

    private static String generateCode() {
        return String.format("%06d", RANDOM.nextInt(1_000_000));
    }
}
