package com.ratelimiter.service;

import com.ratelimiter.core.RateLimiter;
import org.springframework.stereotype.Service;

/**
 * Public facade exposing the rate limiting capability of this service to callers.
 */
@Service
public class RateLimiterService {

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
        return rateLimiter.tryAcquire(clientId);
    }
}
