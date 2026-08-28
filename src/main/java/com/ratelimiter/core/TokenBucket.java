package com.ratelimiter.core;

import com.ratelimiter.domain.RateLimitPolicy;
import java.util.Objects;
import java.util.function.LongSupplier;

/**
 * Thread-safe token bucket: refills continuously at the policy's configured rate and
 * allows bursts up to the policy's maximum request count.
 */
final class TokenBucket {

    private final LongSupplier nanosecondTimeSource;
    private RateLimitPolicy currentPolicy;
    private double availableTokens;
    private long lastRefillTimestampNanos;

    TokenBucket(RateLimitPolicy initialPolicy) {
        this(initialPolicy, System::nanoTime);
    }

    TokenBucket(RateLimitPolicy initialPolicy, LongSupplier nanosecondTimeSource) {
        this.currentPolicy = Objects.requireNonNull(initialPolicy, "initialPolicy must not be null");
        this.nanosecondTimeSource = Objects.requireNonNull(nanosecondTimeSource, "nanosecondTimeSource must not be null");
        this.availableTokens = initialPolicy.maximumRequests();
        this.lastRefillTimestampNanos = nanosecondTimeSource.getAsLong();
    }

    // Attempts to consume a token from the bucket.
    // Returns true if successful, false if the bucket is empty.
    // This method is synchronized to ensure thread safety when accessed concurrently.
    synchronized boolean tryConsume(RateLimitPolicy policy) {
        reconfigureIfPolicyChanged(policy);
        refill();
        if (availableTokens >= 1.0) {
            availableTokens -= 1.0;
            return true;
        }
        return false;
    }

    // A policy update should take effect immediately without resetting the bucket's earned burst capacity.
    private void reconfigureIfPolicyChanged(RateLimitPolicy policy) {
        if (!policy.equals(currentPolicy)) {
            currentPolicy = policy;
            availableTokens = Math.min(availableTokens, policy.maximumRequests());
        }
    }

    // Refills the bucket with tokens based on the elapsed time since the last refill,
    // up to the maximum capacity defined by the current policy.
    private void refill() {
        long now = nanosecondTimeSource.getAsLong();
        long elapsedNanoseconds = now - lastRefillTimestampNanos;
        if (elapsedNanoseconds > 0) {
            double tokensToAdd = elapsedNanoseconds * currentPolicy.refillTokensPerNanosecond();
            availableTokens = Math.min(currentPolicy.maximumRequests(), availableTokens + tokensToAdd);
            lastRefillTimestampNanos = now;
        }
    }
}
