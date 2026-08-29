package com.ratelimiter.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Binds {@code rate-limiter.*} settings from application configuration.
 */
@ConfigurationProperties(prefix = "rate-limiter")
public record RateLimiterProperties(
        PolicyProperties defaultPolicy,
        Duration bucketEvictionDuration,
        Duration policyCacheDuration,
        String store) {

    /**
     * Default values for optional properties are applied in the canonical
     * constructor.
     */
    public RateLimiterProperties {
        defaultPolicy = defaultPolicy == null ? new PolicyProperties(60, Duration.ofMinutes(1)) : defaultPolicy;
        bucketEvictionDuration = bucketEvictionDuration == null ? Duration.ofHours(1) : bucketEvictionDuration;
        policyCacheDuration = policyCacheDuration == null ? Duration.ofSeconds(1) : policyCacheDuration;
        store = store == null ? "in-memory" : store;

        if (bucketEvictionDuration.isZero() || bucketEvictionDuration.isNegative()) {
            throw new IllegalArgumentException("bucketEvictionDuration must be a positive duration");
        }
        if (policyCacheDuration.isZero() || policyCacheDuration.isNegative()) {
            throw new IllegalArgumentException("policyCacheDuration must be a positive duration");
        }
        if (defaultPolicy.maxRequests() <= 0) {
            throw new IllegalArgumentException("defaultPolicy.maxRequests must be greater than zero");
        }
        if (defaultPolicy.windowDuration() == null || defaultPolicy.windowDuration().isZero()
                || defaultPolicy.windowDuration().isNegative()) {
            throw new IllegalArgumentException("defaultPolicy.windowDuration must be a positive duration");
        }
    }

    /**
     * Raw (pre-validation) representation of a rate limit policy as configured in
     * YAML/properties.
     */
    public record PolicyProperties(int maxRequests, Duration windowDuration) {
        public PolicyProperties {
            if (maxRequests <= 0) {
                throw new IllegalArgumentException("maxRequests must be greater than zero");
            }
            if (windowDuration == null || windowDuration.isZero() || windowDuration.isNegative()) {
                throw new IllegalArgumentException("windowDuration must be a positive duration");
            }
        }
    }
}
