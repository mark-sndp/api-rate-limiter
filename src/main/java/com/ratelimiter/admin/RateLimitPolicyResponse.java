package com.ratelimiter.admin;

import java.time.Duration;

/** Response body describing the effective rate limit policy for a client. */
public record RateLimitPolicyResponse(String clientId, int maxRequests, Duration windowDuration) {
}
