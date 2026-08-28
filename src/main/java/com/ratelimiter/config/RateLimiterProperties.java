package com.ratelimiter.config;

import java.time.Duration;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Binds {@code rate-limiter.*} settings from application configuration.
 */
@ConfigurationProperties(prefix = "rate-limiter")
public record RateLimiterProperties(
        PolicyProperties defaultPolicy,
        Duration bucketEvictionDuration,
        Map<String, PolicyProperties> customerPolicies,
        String store) {

    public RateLimiterProperties {
        customerPolicies = customerPolicies == null ? Map.of() : customerPolicies;
        store = store == null ? "in-memory" : store;
    }

    /** Raw (pre-validation) representation of a rate limit policy as configured in YAML/properties. */
    public record PolicyProperties(int maxRequests, Duration windowDuration) {
    }
}
