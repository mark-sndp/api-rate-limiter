package com.ratelimiter.admin;

import java.time.Duration;

/** Request body for creating or updating a client's rate limit policy. */
public record UpdateRateLimitPolicyRequest(int maxRequests, Duration windowDuration) {
}
