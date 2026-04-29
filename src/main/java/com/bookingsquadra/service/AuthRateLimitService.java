package com.bookingsquadra.service;

import com.bookingsquadra.entity.AuthRateLimitEvent;
import com.bookingsquadra.exception.TooManyRequestsException;
import com.bookingsquadra.repository.AuthRateLimitEventRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.net.InetAddress;
import java.time.OffsetDateTime;

@Service
public class AuthRateLimitService {

    private static final int OTP_REQUESTS_PER_EMAIL_PER_MINUTE = 1;
    private static final int OTP_REQUESTS_PER_EMAIL_PER_HOUR = 5;
    private static final int OTP_REQUESTS_PER_EMAIL_PER_DAY = 10;
    private static final int OTP_REQUESTS_PER_IP_PER_MINUTE = 10;
    private static final int OTP_REQUESTS_PER_IP_PER_HOUR = 50;
    private static final int OTP_REQUESTS_PER_EMAIL_IP_PER_TEN_MINUTES = 3;
    private static final int OTP_VERIFY_FAILURES_PER_EMAIL_IP_PER_TEN_MINUTES = 5;
    private static final int EVENT_RETENTION_HOURS = 48;

    private static final String GENERIC_RATE_LIMIT_MESSAGE = "Too many requests. Please try again later.";

    private final AuthRateLimitEventRepository repository;

    public AuthRateLimitService(AuthRateLimitEventRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public void checkAndRecordOtpRequest(String normalizedEmail, InetAddress clientIp, OffsetDateTime now) {
        cleanupOldEvents(now);

        assertBelowLimit(repository.countByActionAndEmailAndCreatedAtAfter(
                        AuthRateLimitEvent.ACTION_OTP_REQUEST, normalizedEmail, now.minusMinutes(1)),
                OTP_REQUESTS_PER_EMAIL_PER_MINUTE);
        assertBelowLimit(repository.countByActionAndEmailAndCreatedAtAfter(
                        AuthRateLimitEvent.ACTION_OTP_REQUEST, normalizedEmail, now.minusHours(1)),
                OTP_REQUESTS_PER_EMAIL_PER_HOUR);
        assertBelowLimit(repository.countByActionAndEmailAndCreatedAtAfter(
                        AuthRateLimitEvent.ACTION_OTP_REQUEST, normalizedEmail, now.minusDays(1)),
                OTP_REQUESTS_PER_EMAIL_PER_DAY);
        assertBelowLimit(repository.countByActionAndIpAddressAndCreatedAtAfter(
                        AuthRateLimitEvent.ACTION_OTP_REQUEST, clientIp, now.minusMinutes(1)),
                OTP_REQUESTS_PER_IP_PER_MINUTE);
        assertBelowLimit(repository.countByActionAndIpAddressAndCreatedAtAfter(
                        AuthRateLimitEvent.ACTION_OTP_REQUEST, clientIp, now.minusHours(1)),
                OTP_REQUESTS_PER_IP_PER_HOUR);
        assertBelowLimit(repository.countByActionAndEmailAndIpAddressAndCreatedAtAfter(
                        AuthRateLimitEvent.ACTION_OTP_REQUEST, normalizedEmail, clientIp, now.minusMinutes(10)),
                OTP_REQUESTS_PER_EMAIL_IP_PER_TEN_MINUTES);

        repository.save(AuthRateLimitEvent.builder()
                .action(AuthRateLimitEvent.ACTION_OTP_REQUEST)
                .email(normalizedEmail)
                .ipAddress(clientIp)
                .build());
    }

    @Transactional(readOnly = true)
    public void checkOtpRequestIpAllowed(InetAddress clientIp, OffsetDateTime now) {
        assertBelowLimit(repository.countByActionAndIpAddressAndCreatedAtAfter(
                        AuthRateLimitEvent.ACTION_OTP_REQUEST, clientIp, now.minusMinutes(1)),
                OTP_REQUESTS_PER_IP_PER_MINUTE);
        assertBelowLimit(repository.countByActionAndIpAddressAndCreatedAtAfter(
                        AuthRateLimitEvent.ACTION_OTP_REQUEST, clientIp, now.minusHours(1)),
                OTP_REQUESTS_PER_IP_PER_HOUR);
    }

    @Transactional(readOnly = true)
    public void checkOtpVerifyAllowed(String normalizedEmail, InetAddress clientIp, OffsetDateTime now) {
        assertBelowLimit(repository.countByActionAndEmailAndIpAddressAndCreatedAtAfter(
                        AuthRateLimitEvent.ACTION_OTP_VERIFY_FAILED, normalizedEmail, clientIp, now.minusMinutes(10)),
                OTP_VERIFY_FAILURES_PER_EMAIL_IP_PER_TEN_MINUTES);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordOtpVerifyFailure(String normalizedEmail, InetAddress clientIp, OffsetDateTime now) {
        cleanupOldEvents(now);
        repository.save(AuthRateLimitEvent.builder()
                .action(AuthRateLimitEvent.ACTION_OTP_VERIFY_FAILED)
                .email(normalizedEmail)
                .ipAddress(clientIp)
                .build());
    }

    private void cleanupOldEvents(OffsetDateTime now) {
        repository.deleteByCreatedAtBefore(now.minusHours(EVENT_RETENTION_HOURS));
    }

    private static void assertBelowLimit(long currentCount, int limit) {
        if (currentCount >= limit) {
            throw new TooManyRequestsException(GENERIC_RATE_LIMIT_MESSAGE);
        }
    }
}
