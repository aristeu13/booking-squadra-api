package com.bookingsquadra.service;

import com.bookingsquadra.config.PhoneAuthProperties;
import com.bookingsquadra.config.TestOtpProperties;
import com.bookingsquadra.dto.IdentifierChangeConfirmDto;
import com.bookingsquadra.dto.IdentifierChangeStartResponseDto;
import com.bookingsquadra.dto.ProfileDto;
import com.bookingsquadra.entity.User;
import com.bookingsquadra.entity.UserOtp;
import com.bookingsquadra.repository.BookingRepository;
import com.bookingsquadra.repository.UserOtpRepository;
import com.bookingsquadra.repository.UserRefreshTokenRepository;
import com.bookingsquadra.repository.UserRepository;
import com.bookingsquadra.util.BrazilPhoneNormalizer;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

@Service
public class IdentifierChangeService {

    private static final Logger log = LoggerFactory.getLogger(IdentifierChangeService.class);

    private static final int OTP_TTL_MINUTES = 10;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserRepository userRepository;
    private final UserOtpRepository userOtpRepository;
    private final UserRefreshTokenRepository userRefreshTokenRepository;
    private final BookingRepository bookingRepository;
    private final OtpEmailSender otpEmailSender;
    private final OtpWhatsAppSender otpWhatsAppSender;
    private final AuthRateLimitService authRateLimitService;
    private final TestOtpProperties testOtpProperties;
    private final PhoneAuthProperties phoneAuthProperties;
    private final UserService userService;

    public IdentifierChangeService(
            UserRepository userRepository,
            UserOtpRepository userOtpRepository,
            UserRefreshTokenRepository userRefreshTokenRepository,
            BookingRepository bookingRepository,
            OtpEmailSender otpEmailSender,
            OtpWhatsAppSender otpWhatsAppSender,
            AuthRateLimitService authRateLimitService,
            TestOtpProperties testOtpProperties,
            PhoneAuthProperties phoneAuthProperties,
            UserService userService
    ) {
        this.userRepository = userRepository;
        this.userOtpRepository = userOtpRepository;
        this.userRefreshTokenRepository = userRefreshTokenRepository;
        this.bookingRepository = bookingRepository;
        this.otpEmailSender = otpEmailSender;
        this.otpWhatsAppSender = otpWhatsAppSender;
        this.authRateLimitService = authRateLimitService;
        this.testOtpProperties = testOtpProperties;
        this.phoneAuthProperties = phoneAuthProperties;
        this.userService = userService;
    }

    @Transactional
    public IdentifierChangeStartResponseDto startPhoneChange(String rawPhone, HttpServletRequest request) {
        if (!phoneAuthProperties.enabled()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Phone login is not enabled");
        }

        String target = BrazilPhoneNormalizer.normalizeOrThrow(rawPhone);
        User me = userService.findCurrentOrThrow();

        if (target.equals(me.getPhoneE164())) {
            return new IdentifierChangeStartResponseDto(false);
        }

        boolean requiresMergeConfirmation = lookupConflict(target, me, true);

        sendChangeOtp(me, target, UserOtp.PURPOSE_PHONE_CHANGE, request, /*isPhone*/ true);
        return new IdentifierChangeStartResponseDto(requiresMergeConfirmation);
    }

    @Transactional
    public IdentifierChangeStartResponseDto startEmailChange(String rawEmail, HttpServletRequest request) {
        String target = normalizeEmail(rawEmail);
        User me = userService.findCurrentOrThrow();

        if (target.equalsIgnoreCase(me.getEmail())) {
            return new IdentifierChangeStartResponseDto(false);
        }

        boolean requiresMergeConfirmation = lookupConflict(target, me, false);

        sendChangeOtp(me, target, UserOtp.PURPOSE_EMAIL_CHANGE, request, /*isPhone*/ false);
        return new IdentifierChangeStartResponseDto(requiresMergeConfirmation);
    }

    @Transactional
    public ProfileDto confirmPhoneChange(IdentifierChangeConfirmDto body) {
        return confirmChange(UserOtp.PURPOSE_PHONE_CHANGE, body, /*isPhone*/ true);
    }

    @Transactional
    public ProfileDto confirmEmailChange(IdentifierChangeConfirmDto body) {
        return confirmChange(UserOtp.PURPOSE_EMAIL_CHANGE, body, /*isPhone*/ false);
    }

    private ProfileDto confirmChange(String purpose, IdentifierChangeConfirmDto body, boolean isPhone) {
        User me = userService.findCurrentOrThrow();
        OffsetDateTime now = OffsetDateTime.now();

        UserOtp otp = findValidatedPendingOtp(me.getId(), purpose, body.code(), isPhone, now)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid or expired OTP"));

        String target = otp.getTargetIdentifier();

        Optional<User> other = lookupOther(target, me, isPhone);
        boolean activeMergeNeeded = other.filter(u -> !isGhost(u)).isPresent();
        if (activeMergeNeeded && !body.mergeAcknowledged()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "merge_acknowledgement_required");
        }

        otp.setUsedAt(now);
        userOtpRepository.save(otp);

        other.ifPresent(secondary -> mergeInto(me, secondary, now));

        if (isPhone) {
            me.setPhoneE164(target);
        } else {
            me.setEmail(target);
        }
        return profileDtoOf(userRepository.save(me));
    }

    private void sendChangeOtp(User me, String target, String purpose, HttpServletRequest request, boolean isPhone) {
        InetAddress clientIp = resolveClientIp(request);
        OffsetDateTime now = OffsetDateTime.now();

        authRateLimitService.checkOtpRequestIpAllowed(clientIp, now);
        authRateLimitService.checkAndRecordOtpRequest(target, clientIp, now);

        String code = generateCode();
        userOtpRepository.save(UserOtp.builder()
                .userId(me.getId())
                .otpCode(code)
                .purpose(purpose)
                .targetIdentifier(target)
                .expiresAt(now.plusMinutes(OTP_TTL_MINUTES))
                .build());

        boolean testBypass = isPhone
                ? testOtpProperties.matchesPhone(target)
                : testOtpProperties.matchesEmail(target);
        if (testBypass) {
            return;
        }

        if (isPhone) {
            otpWhatsAppSender.sendLoginOtp(target, code);
        } else {
            otpEmailSender.sendLoginOtp(target, code);
        }
    }

    private Optional<UserOtp> findValidatedPendingOtp(
            UUID userId,
            String purpose,
            String userCode,
            boolean isPhone,
            OffsetDateTime now
    ) {
        if (testOtpProperties.matchesCode(userCode)) {
            Optional<UserOtp> latest = userOtpRepository
                    .findFirstByUserIdAndPurposeAndUsedAtIsNullAndExpiresAtAfterOrderByCreatedAtDesc(
                            userId, purpose, now);
            if (latest.isPresent()) {
                String target = latest.get().getTargetIdentifier();
                boolean isTestTarget = isPhone
                        ? testOtpProperties.matchesPhone(target)
                        : testOtpProperties.matchesEmail(target);
                if (isTestTarget) {
                    return latest;
                }
            }
        }

        return userOtpRepository
                .findFirstByUserIdAndPurposeAndOtpCodeAndUsedAtIsNullAndExpiresAtAfterOrderByCreatedAtDesc(
                        userId, purpose, userCode, now);
    }

    private boolean lookupConflict(String target, User me, boolean isPhone) {
        return lookupOther(target, me, isPhone).filter(u -> !isGhost(u)).isPresent();
    }

    private Optional<User> lookupOther(String target, User me, boolean isPhone) {
        Optional<User> match = isPhone
                ? userRepository.findFirstByPhoneE164(target)
                : userRepository.findFirstByEmailIgnoreCase(target);
        return match.filter(u -> !u.getId().equals(me.getId()))
                .filter(u -> User.STATUS_ACTIVE.equals(u.getStatus()));
    }

    private void mergeInto(User primary, User secondary, OffsetDateTime now) {
        log.info("Merging user {} into {}", secondary.getId(), primary.getId());

        bookingRepository.reassignUserId(secondary.getId(), primary.getId());
        userRefreshTokenRepository.revokeAllForUser(secondary.getId(), now);

        if (primary.getAsaasCustomerId() == null && secondary.getAsaasCustomerId() != null) {
            primary.setAsaasCustomerId(secondary.getAsaasCustomerId());
        } else if (secondary.getAsaasCustomerId() != null) {
            log.warn("Merge {} <- {}: keeping primary Asaas customer {} and discarding secondary {} (reconcile manually)",
                    primary.getId(), secondary.getId(),
                    primary.getAsaasCustomerId(), secondary.getAsaasCustomerId());
        }

        secondary.setStatus(User.STATUS_DELETED);
        secondary.setEmail(null);
        secondary.setPhoneE164(null);
        secondary.setGoogleId(null);
        secondary.setAsaasCustomerId(null);
        userRepository.save(secondary);
    }

    private boolean isGhost(User other) {
        return !hasText(other.getEmail()) && !hasText(other.getGoogleId());
    }

    private ProfileDto profileDtoOf(User u) {
        return new ProfileDto(
                u.getId(),
                u.getName(),
                u.getEmail(),
                u.getPhoneE164(),
                u.getCpf(),
                u.getHasUsedGoogleAuth(),
                u.getRole());
    }

    private static String generateCode() {
        return String.format("%06d", RANDOM.nextInt(1_000_000));
    }

    private static String normalizeEmail(String email) {
        if (email == null || email.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_CONTENT, "email is required");
        }
        return email.trim().toLowerCase(Locale.ROOT);
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
