package com.ratelimiter.domain;

import java.time.Duration;
import java.util.Objects;

/**
 * Defines how many requests a client may make within a given time window,
 * e.g. 100 requests per minute or 10 requests per second.
 */
public record RateLimitPolicy(int maximumRequests, Duration windowDuration) {

    public RateLimitPolicy {
        if (maximumRequests <= 0) {
            throw new IllegalArgumentException("maximumRequests must be greater than zero");
        }
        Objects.requireNonNull(windowDuration, "windowDuration must not be null");
        if (windowDuration.isZero() || windowDuration.isNegative()) {
            throw new IllegalArgumentException("windowDuration must be a positive duration");
        }
    }

    /** Average number of tokens replenished per nanosecond under this policy. */
    public double refillTokensPerNanosecond() {
        return (double) maximumRequests / windowDuration.toNanos();
    }
}
