package com.bookingsquadra.service;

import com.bookingsquadra.config.JwtProperties;
import com.bookingsquadra.config.PhoneAuthProperties;
import com.bookingsquadra.config.TestOtpProperties;
import com.bookingsquadra.dto.AuthTokenDto;
import com.bookingsquadra.dto.OtpRequestDto;
import com.bookingsquadra.dto.OtpVerifyDto;
import com.bookingsquadra.entity.User;
import com.bookingsquadra.entity.UserOtp;
import com.bookingsquadra.entity.UserRefreshToken;
import com.bookingsquadra.repository.UserOtpRepository;
import com.bookingsquadra.repository.UserRefreshTokenRepository;
import com.bookingsquadra.repository.UserRepository;
import com.bookingsquadra.security.JwtUtil;
import com.bookingsquadra.util.BrazilPhoneNormalizer;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;
import java.util.UUID;

@Service
public class AuthService {

    private static final int OTP_TTL_MINUTES = 10;
    private static final int OTP_RESEND_COOLDOWN_SECONDS = 60;
    private static final int REFRESH_TOKEN_BYTES = 32;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final JwtUtil jwtUtil;
    private final JwtProperties jwtProperties;
    private final UserRepository userRepository;
    private final UserOtpRepository userOtpRepository;
    private final UserRefreshTokenRepository userRefreshTokenRepository;
    private final OtpEmailSender otpEmailSender;
    private final OtpWhatsAppSender otpWhatsAppSender;
    private final AuthRateLimitService authRateLimitService;
    private final TestOtpProperties testOtpProperties;
    private final PhoneAuthProperties phoneAuthProperties;
    private final GoogleIdTokenVerifier googleIdTokenVerifier;

    public AuthService(
            JwtUtil jwtUtil,
            JwtProperties jwtProperties,
            UserRepository userRepository,
            UserOtpRepository userOtpRepository,
            UserRefreshTokenRepository userRefreshTokenRepository,
            OtpEmailSender otpEmailSender,
            OtpWhatsAppSender otpWhatsAppSender,
            AuthRateLimitService authRateLimitService,
            TestOtpProperties testOtpProperties,
            PhoneAuthProperties phoneAuthProperties,
            GoogleIdTokenVerifier googleIdTokenVerifier
    ) {
        this.jwtUtil = jwtUtil;
        this.jwtProperties = jwtProperties;
        this.userRepository = userRepository;
        this.userOtpRepository = userOtpRepository;
        this.userRefreshTokenRepository = userRefreshTokenRepository;
        this.otpEmailSender = otpEmailSender;
        this.otpWhatsAppSender = otpWhatsAppSender;
        this.authRateLimitService = authRateLimitService;
        this.testOtpProperties = testOtpProperties;
        this.phoneAuthProperties = phoneAuthProperties;
        this.googleIdTokenVerifier = googleIdTokenVerifier;
    }

    @Transactional
    public void requestOtp(OtpRequestDto body, HttpServletRequest request) {
        if (hasText(body.email())) {
            requestEmailOtp(body.email(), request);
        } else {
            requestPhoneOtp(body.phone(), request);
        }
    }

    @Transactional
    public AuthTokenDto verifyOtp(OtpVerifyDto body, HttpServletRequest request) {
        if (hasText(body.email())) {
            return verifyEmailOtp(body.email(), body.code(), request);
        }
        return verifyPhoneOtp(body.phone(), body.code(), request);
    }

    private void requestEmailOtp(String rawEmail, HttpServletRequest request) {
        String normalizedEmail = normalizeEmail(rawEmail);
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
        persistLoginOtp(user.getId(), code, now);
        otpEmailSender.sendLoginOtp(normalizedEmail, code);
    }

    private void requestPhoneOtp(String rawPhone, HttpServletRequest request) {
        if (!phoneAuthProperties.enabled()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Phone login is not enabled");
        }

        String e164 = BrazilPhoneNormalizer.normalizeOrThrow(rawPhone);
        InetAddress clientIp = resolveClientIp(request);
        OffsetDateTime now = OffsetDateTime.now();

        authRateLimitService.checkOtpRequestIpAllowed(clientIp, now);

        User user = userRepository.findFirstByPhoneE164(e164)
                .orElseGet(() -> userRepository.save(User.builder()
                        .name("")
                        .phoneE164(e164)
                        .hasUsedGoogleAuth(false)
                        .role(User.ROLE_USER)
                        .status(User.STATUS_ACTIVE)
                        .build()));

        if (testOtpProperties.matchesPhone(e164)) {
            return;
        }

        if (hasRecentLoginOtp(user.getId(), now)) {
            return;
        }

        authRateLimitService.checkAndRecordOtpRequest(e164, clientIp, now);

        String code = generateCode();
        persistLoginOtp(user.getId(), code, now);
        otpWhatsAppSender.sendLoginOtp(e164, code);
    }

    private AuthTokenDto verifyEmailOtp(String rawEmail, String code, HttpServletRequest request) {
        String normalizedEmail = normalizeEmail(rawEmail);
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

        return consumeOtpAndIssueTokens(user, code, normalizedEmail, clientIp, now);
    }

    private AuthTokenDto verifyPhoneOtp(String rawPhone, String code, HttpServletRequest request) {
        if (!phoneAuthProperties.enabled()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Phone login is not enabled");
        }

        String e164 = BrazilPhoneNormalizer.normalizeOrThrow(rawPhone);
        InetAddress clientIp = resolveClientIp(request);
        OffsetDateTime now = OffsetDateTime.now();

        authRateLimitService.checkOtpVerifyAllowed(e164, clientIp, now);

        User user = userRepository.findFirstByPhoneE164(e164)
                .orElseThrow(() -> invalidOtp(e164, clientIp, now, "Invalid credentials"));

        if (testOtpProperties.matchesPhone(e164)) {
            if (testOtpProperties.matchesCode(code)) {
                return issueTokenPair(user, now);
            }
            throw invalidOtp(e164, clientIp, now, "Invalid or expired OTP");
        }

        return consumeOtpAndIssueTokens(user, code, e164, clientIp, now);
    }

    private AuthTokenDto consumeOtpAndIssueTokens(
            User user,
            String code,
            String identifier,
            InetAddress clientIp,
            OffsetDateTime now
    ) {
        UserOtp otp = userOtpRepository
                .findFirstByUserIdAndPurposeAndOtpCodeAndUsedAtIsNullAndExpiresAtAfterOrderByCreatedAtDesc(
                        user.getId(),
                        UserOtp.PURPOSE_LOGIN,
                        code,
                        now)
                .orElseThrow(() -> invalidOtp(identifier, clientIp, now, "Invalid or expired OTP"));

        otp.setUsedAt(now);
        userOtpRepository.save(otp);

        return issueTokenPair(user, now);
    }

    @Transactional
    public AuthTokenDto verifyGoogle(String idToken) {
        GoogleIdToken.Payload payload = verifyGooglePayload(idToken);
        String googleId = requireText(payload.getSubject(), "Invalid Google token");
        String normalizedEmail = normalizeEmail(requireText(payload.getEmail(), "Google email is required"));
        if (!Boolean.TRUE.equals(payload.getEmailVerified())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Google email must be verified");
        }

        String fullName = normalizeNullable(payload.get("name"));
        User user = resolveGoogleUser(googleId, normalizedEmail, fullName);
        return issueTokenPair(user, OffsetDateTime.now());
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

    private void persistLoginOtp(UUID userId, String code, OffsetDateTime now) {
        userOtpRepository.save(UserOtp.builder()
                .userId(userId)
                .otpCode(code)
                .purpose(UserOtp.PURPOSE_LOGIN)
                .expiresAt(now.plusMinutes(OTP_TTL_MINUTES))
                .build());
    }

    private GoogleIdToken.Payload verifyGooglePayload(String idToken) {
        try {
            GoogleIdToken verifiedToken = googleIdTokenVerifier.verify(idToken);
            if (verifiedToken == null) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid Google token");
            }
            return verifiedToken.getPayload();
        } catch (GeneralSecurityException | IOException e) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid Google token", e);
        }
    }

    private User resolveGoogleUser(String googleId, String normalizedEmail, String fullName) {
        User googleUser = userRepository.findByGoogleId(googleId).orElse(null);
        User emailUser = userRepository.findFirstByEmailIgnoreCase(normalizedEmail).orElse(null);

        if (googleUser != null) {
            ensureActive(googleUser);
            if (emailUser != null && !emailUser.getId().equals(googleUser.getId())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Google account conflicts with another user");
            }
            googleUser.setEmail(normalizedEmail);
            applyGoogleProfile(googleUser, googleId, fullName);
            return userRepository.save(googleUser);
        }

        if (emailUser != null) {
            ensureActive(emailUser);
            if (hasText(emailUser.getGoogleId()) && !googleId.equals(emailUser.getGoogleId())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Email is linked to another Google account");
            }
            applyGoogleProfile(emailUser, googleId, fullName);
            return userRepository.save(emailUser);
        }

        return userRepository.save(User.builder()
                .name(fullName == null ? "" : fullName)
                .email(normalizedEmail)
                .googleId(googleId)
                .hasUsedGoogleAuth(true)
                .role(User.ROLE_USER)
                .status(User.STATUS_ACTIVE)
                .build());
    }

    private static void applyGoogleProfile(User user, String googleId, String fullName) {
        user.setGoogleId(googleId);
        user.setHasUsedGoogleAuth(true);
        if (!hasText(user.getName()) && fullName != null) {
            user.setName(fullName);
        }
    }

    private static void ensureActive(User user) {
        if (!User.STATUS_ACTIVE.equals(user.getStatus())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Account is not active");
        }
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

    private boolean hasRecentLoginOtp(UUID userId, OffsetDateTime now) {
        return userOtpRepository.existsByUserIdAndPurposeAndUsedAtIsNullAndExpiresAtAfterAndCreatedAtAfter(
                userId,
                UserOtp.PURPOSE_LOGIN,
                now,
                now.minusSeconds(OTP_RESEND_COOLDOWN_SECONDS)
        );
    }

    private ResponseStatusException invalidOtp(
            String identifier,
            InetAddress clientIp,
            OffsetDateTime now,
            String message
    ) {
        authRateLimitService.recordOtpVerifyFailure(identifier, clientIp, now);
        return new ResponseStatusException(HttpStatus.UNAUTHORIZED, message);
    }

    private static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private static String requireText(String value, String message) {
        if (!hasText(value)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, message);
        }
        return value.trim();
    }

    private static String normalizeNullable(Object value) {
        if (!(value instanceof String text)) {
            return null;
        }
        if (!hasText(text)) {
            return null;
        }
        return text.trim();
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
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
