package com.ratelimiter.service;

import com.ratelimiter.core.RateLimiter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Public facade exposing the rate limiting capability of this service to callers.
 */
@Service
public class RateLimiterService {

    private static final Logger log = LoggerFactory.getLogger(RateLimiterService.class);

    private final RateLimiter rateLimiter;

    public RateLimiterService(RateLimiter rateLimiter) {
        this.rateLimiter = rateLimiter;
    }

    /**
     * @param clientId identifier of the client making the request, must not be null or blank
     * @return true if the request is allowed under the client's current rate limit policy
     */
    public boolean allowRequest(String clientId) {
        if (clientId == null || clientId.isBlank()) {
            throw new IllegalArgumentException("clientId must not be null or blank");
        }
        boolean allowed = rateLimiter.tryAcquire(clientId);
        log.debug("Rate limiter {} request for client {}", allowed ? "allowed" : "rejected", clientId);
        return allowed;
    }
}
