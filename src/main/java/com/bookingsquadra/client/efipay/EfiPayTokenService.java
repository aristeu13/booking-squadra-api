package com.bookingsquadra.client.efipay;

import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Caches the client_credentials access token returned by {@link EfiPayAuthClient} so we do not
 * round-trip to {@code /oauth/token} on every API call. Refreshes ~60s before the advertised
 * expiry to avoid handing out a token that races the server-side TTL.
 */
@Service
public class EfiPayTokenService {

    private static final Logger log = LoggerFactory.getLogger(EfiPayTokenService.class);
    private static final long DEFAULT_TTL_SECONDS = 3600L;
    private static final long REFRESH_SKEW_SECONDS = 60L;

    private final EfiPayAuthClient authClient;

    private volatile CachedToken cache;

    public EfiPayTokenService(EfiPayAuthClient authClient) {
        this.authClient = authClient;
    }

    public String getAccessToken() {
        CachedToken current = cache;
        if (current != null && Instant.now().isBefore(current.expiresAt())) {
            return current.accessToken();
        }
        return refresh();
    }

    /**
     * Discards the cached token so the next {@link #getAccessToken()} forces a /oauth/token round-trip.
     * Use this when EfiPay rejects a request with 401, which means our cached token was revoked or
     * never valid (e.g. clock skew, rotated client secret) before its computed expiry.
     */
    public synchronized void invalidate() {
        cache = null;
    }

    private synchronized String refresh() {
        CachedToken current = cache;
        Instant now = Instant.now();
        if (current != null && now.isBefore(current.expiresAt())) {
            return current.accessToken();
        }
        EfiPayTokenResponse response = authClient.requestClientCredentialsToken();
        long ttl = response.expiresIn() != null && response.expiresIn() > 0
                ? response.expiresIn()
                : DEFAULT_TTL_SECONDS;
        long effectiveTtl = Math.max(REFRESH_SKEW_SECONDS, ttl - REFRESH_SKEW_SECONDS);
        Instant expiresAt = now.plusSeconds(effectiveTtl);
        cache = new CachedToken(response.accessToken(), expiresAt);
        log.info("EfiPay access token refreshed; valid for ~{}s", effectiveTtl);
        return response.accessToken();
    }

    private record CachedToken(String accessToken, Instant expiresAt) {
    }
}
