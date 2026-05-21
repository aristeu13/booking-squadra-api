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

    private static final int OTP_REQUESTS_PER_IDENTIFIER_PER_MINUTE = 1;
    private static final int OTP_REQUESTS_PER_IDENTIFIER_PER_HOUR = 5;
    private static final int OTP_REQUESTS_PER_IDENTIFIER_PER_DAY = 10;
    private static final int OTP_REQUESTS_PER_IP_PER_MINUTE = 10;
    private static final int OTP_REQUESTS_PER_IP_PER_HOUR = 50;
    private static final int OTP_REQUESTS_PER_IDENTIFIER_IP_PER_TEN_MINUTES = 3;
    private static final int OTP_VERIFY_FAILURES_PER_IDENTIFIER_IP_PER_TEN_MINUTES = 5;
    private static final int EVENT_RETENTION_HOURS = 48;

    private static final String GENERIC_RATE_LIMIT_MESSAGE = "Too many requests. Please try again later.";

    private final AuthRateLimitEventRepository repository;

    public AuthRateLimitService(AuthRateLimitEventRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public void checkAndRecordOtpRequest(String identifier, InetAddress clientIp, OffsetDateTime now) {
        cleanupOldEvents(now);

        assertBelowLimit(repository.countByActionAndIdentifierAndCreatedAtAfter(
                        AuthRateLimitEvent.ACTION_OTP_REQUEST, identifier, now.minusMinutes(1)),
                OTP_REQUESTS_PER_IDENTIFIER_PER_MINUTE);
        assertBelowLimit(repository.countByActionAndIdentifierAndCreatedAtAfter(
                        AuthRateLimitEvent.ACTION_OTP_REQUEST, identifier, now.minusHours(1)),
                OTP_REQUESTS_PER_IDENTIFIER_PER_HOUR);
        assertBelowLimit(repository.countByActionAndIdentifierAndCreatedAtAfter(
                        AuthRateLimitEvent.ACTION_OTP_REQUEST, identifier, now.minusDays(1)),
                OTP_REQUESTS_PER_IDENTIFIER_PER_DAY);
        assertBelowLimit(repository.countByActionAndIpAddressAndCreatedAtAfter(
                        AuthRateLimitEvent.ACTION_OTP_REQUEST, clientIp, now.minusMinutes(1)),
                OTP_REQUESTS_PER_IP_PER_MINUTE);
        assertBelowLimit(repository.countByActionAndIpAddressAndCreatedAtAfter(
                        AuthRateLimitEvent.ACTION_OTP_REQUEST, clientIp, now.minusHours(1)),
                OTP_REQUESTS_PER_IP_PER_HOUR);
        assertBelowLimit(repository.countByActionAndIdentifierAndIpAddressAndCreatedAtAfter(
                        AuthRateLimitEvent.ACTION_OTP_REQUEST, identifier, clientIp, now.minusMinutes(10)),
                OTP_REQUESTS_PER_IDENTIFIER_IP_PER_TEN_MINUTES);

        repository.save(AuthRateLimitEvent.builder()
                .action(AuthRateLimitEvent.ACTION_OTP_REQUEST)
                .identifier(identifier)
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
    public void checkOtpVerifyAllowed(String identifier, InetAddress clientIp, OffsetDateTime now) {
        assertBelowLimit(repository.countByActionAndIdentifierAndIpAddressAndCreatedAtAfter(
                        AuthRateLimitEvent.ACTION_OTP_VERIFY_FAILED, identifier, clientIp, now.minusMinutes(10)),
                OTP_VERIFY_FAILURES_PER_IDENTIFIER_IP_PER_TEN_MINUTES);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordOtpVerifyFailure(String identifier, InetAddress clientIp, OffsetDateTime now) {
        cleanupOldEvents(now);
        repository.save(AuthRateLimitEvent.builder()
                .action(AuthRateLimitEvent.ACTION_OTP_VERIFY_FAILED)
                .identifier(identifier)
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
