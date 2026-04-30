package com.bookingsquadra.service;

import com.bookingsquadra.config.JwtProperties;
import com.bookingsquadra.config.TestOtpProperties;
import com.bookingsquadra.dto.AuthTokenDto;
import com.bookingsquadra.entity.User;
import com.bookingsquadra.entity.UserOtp;
import com.bookingsquadra.entity.UserRefreshToken;
import com.bookingsquadra.repository.UserOtpRepository;
import com.bookingsquadra.repository.UserRefreshTokenRepository;
import com.bookingsquadra.repository.UserRepository;
import com.bookingsquadra.security.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;

@Service
public class AuthService {

    private static final int OTP_TTL_MINUTES = 10;
    private static final int OTP_EMAIL_COOLDOWN_SECONDS = 60;
    private static final int REFRESH_TOKEN_BYTES = 32;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final JwtUtil jwtUtil;
    private final JwtProperties jwtProperties;
    private final UserRepository userRepository;
    private final UserOtpRepository userOtpRepository;
    private final UserRefreshTokenRepository userRefreshTokenRepository;
    private final OtpEmailSender otpEmailSender;
    private final AuthRateLimitService authRateLimitService;
    private final TestOtpProperties testOtpProperties;

    public AuthService(
            JwtUtil jwtUtil,
            JwtProperties jwtProperties,
            UserRepository userRepository,
            UserOtpRepository userOtpRepository,
            UserRefreshTokenRepository userRefreshTokenRepository,
            OtpEmailSender otpEmailSender,
            AuthRateLimitService authRateLimitService,
            TestOtpProperties testOtpProperties
    ) {
        this.jwtUtil = jwtUtil;
        this.jwtProperties = jwtProperties;
        this.userRepository = userRepository;
        this.userOtpRepository = userOtpRepository;
        this.userRefreshTokenRepository = userRefreshTokenRepository;
        this.otpEmailSender = otpEmailSender;
        this.authRateLimitService = authRateLimitService;
        this.testOtpProperties = testOtpProperties;
    }

    @Transactional
    public void requestOtp(String email, HttpServletRequest request) {
        String normalizedEmail = normalizeEmail(email);
        InetAddress clientIp = resolveClientIp(request);
        OffsetDateTime now = OffsetDateTime.now();

        authRateLimitService.checkOtpRequestIpAllowed(clientIp, now);

        User user = userRepository.findFirstByEmailIgnoreCase(normalizedEmail)
                .orElseGet(() -> userRepository.save(User.builder()
                        .name("")
                        .email(normalizedEmail)
                        .hasUsedGoogleAuth(false)
                        .role(User.ROLE_USER)
                        .status(User.STATUS_ACTIVE)
                        .build()));

        if (testOtpProperties.matchesEmail(normalizedEmail)) {
            return;
        }

        if (hasRecentLoginOtp(user.getId(), now)) {
            return;
        }

        authRateLimitService.checkAndRecordOtpRequest(normalizedEmail, clientIp, now);

        String code = generateCode();
        userOtpRepository.save(UserOtp.builder()
                .userId(user.getId())
                .otpCode(code)
                .purpose(UserOtp.PURPOSE_LOGIN)
                .expiresAt(now.plusMinutes(OTP_TTL_MINUTES))
                .build());

        otpEmailSender.sendLoginOtp(normalizedEmail, code);
    }

    @Transactional
    public AuthTokenDto verifyOtp(String email, String code, HttpServletRequest request) {
        String normalizedEmail = normalizeEmail(email);
        InetAddress clientIp = resolveClientIp(request);
        OffsetDateTime now = OffsetDateTime.now();

        authRateLimitService.checkOtpVerifyAllowed(normalizedEmail, clientIp, now);

        User user = userRepository.findFirstByEmailIgnoreCase(normalizedEmail)
                .orElseThrow(() -> invalidOtp(normalizedEmail, clientIp, now, "Invalid credentials"));

        if (testOtpProperties.matchesEmail(normalizedEmail)) {
            if (testOtpProperties.matchesCode(code)) {
                return issueTokenPair(user, now);
            }
            throw invalidOtp(normalizedEmail, clientIp, now, "Invalid or expired OTP");
        }

        UserOtp otp = userOtpRepository
                .findFirstByUserIdAndPurposeAndOtpCodeAndUsedAtIsNullAndExpiresAtAfterOrderByCreatedAtDesc(
                        user.getId(),
                        UserOtp.PURPOSE_LOGIN,
                        code,
                        now)
                .orElseThrow(() -> invalidOtp(normalizedEmail, clientIp, now, "Invalid or expired OTP"));

        otp.setUsedAt(now);
        userOtpRepository.save(otp);

        return issueTokenPair(user, now);
    }

    @Transactional
    public AuthTokenDto refresh(String refreshToken) {
        OffsetDateTime now = OffsetDateTime.now();
        UserRefreshToken storedToken = userRefreshTokenRepository
                .findByTokenHashAndRevokedAtIsNullAndExpiresAtAfter(hashToken(refreshToken), now)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid refresh token"));

        User user = userRepository.findById(storedToken.getUserId())
                .filter(candidate -> User.STATUS_ACTIVE.equals(candidate.getStatus()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid refresh token"));

        storedToken.setRevokedAt(now);
        userRefreshTokenRepository.save(storedToken);

        return issueTokenPair(user, now);
    }

    @Transactional
    public void signOut(String refreshToken) {
        OffsetDateTime now = OffsetDateTime.now();
        userRefreshTokenRepository
                .findByTokenHashAndRevokedAtIsNullAndExpiresAtAfter(hashToken(refreshToken), now)
                .ifPresent(storedToken -> {
                    storedToken.setRevokedAt(now);
                    userRefreshTokenRepository.save(storedToken);
                });
    }

    private AuthTokenDto issueTokenPair(User user, OffsetDateTime now) {
        String refreshToken = generateRefreshToken();
        userRefreshTokenRepository.save(UserRefreshToken.builder()
                .userId(user.getId())
                .tokenHash(hashToken(refreshToken))
                .expiresAt(now.plus(Duration.ofMillis(jwtProperties.refreshExpirationMs())))
                .build());

        return new AuthTokenDto(
                jwtUtil.generateToken(user.getId().toString(), user.getRole()),
                refreshToken
        );
    }

    private static String generateCode() {
        return String.format("%06d", RANDOM.nextInt(1_000_000));
    }

    private static String generateRefreshToken() {
        byte[] bytes = new byte[REFRESH_TOKEN_BYTES];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String hashToken(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }

    private boolean hasRecentLoginOtp(java.util.UUID userId, OffsetDateTime now) {
        return userOtpRepository.existsByUserIdAndPurposeAndUsedAtIsNullAndExpiresAtAfterAndCreatedAtAfter(
                userId,
                UserOtp.PURPOSE_LOGIN,
                now,
                now.minusSeconds(OTP_EMAIL_COOLDOWN_SECONDS)
        );
    }

    private ResponseStatusException invalidOtp(
            String normalizedEmail,
            InetAddress clientIp,
            OffsetDateTime now,
            String message
    ) {
        authRateLimitService.recordOtpVerifyFailure(normalizedEmail, clientIp, now);
        return new ResponseStatusException(HttpStatus.UNAUTHORIZED, message);
    }

    private static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private static InetAddress resolveClientIp(HttpServletRequest request) {
        String rawIp = firstPresentHeader(request, "X-Real-IP");
        if (rawIp == null) {
            rawIp = firstForwardedForIp(request);
        }
        if (rawIp == null) {
            rawIp = request.getRemoteAddr();
        }

        try {
            return InetAddress.getByName(rawIp);
        } catch (UnknownHostException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid client IP");
        }
    }

    private static String firstPresentHeader(HttpServletRequest request, String name) {
        String value = request.getHeader(name);
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private static String firstForwardedForIp(HttpServletRequest request) {
        String forwardedFor = firstPresentHeader(request, "X-Forwarded-For");
        if (forwardedFor == null) {
            return null;
        }
        return forwardedFor.split(",", 2)[0].trim();
    }
}
