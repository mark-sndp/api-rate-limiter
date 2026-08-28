package com.ratelimiter.core;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.ratelimiter.config.RateLimiterProperties;
import com.ratelimiter.domain.RateLimitPolicy;
import com.ratelimiter.policy.RateLimitPolicyProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Token-bucket {@link RateLimiter} backed by an in-memory cache of per-client buckets.
 * Idle buckets are evicted after {@code rate-limiter.bucket-eviction-duration} of inactivity
 * so memory usage stays bounded regardless of how many distinct clients have been seen.
 *
 * <p>Active by default; only usable for a single JVM instance. For horizontal scaling across
 * multiple instances, set {@code rate-limiter.store=redis} to use {@code RedisTokenBucketRateLimiter}.
 */
@Component
@ConditionalOnProperty(prefix = "rate-limiter", name = "store", havingValue = "in-memory", matchIfMissing = true)
public class TokenBucketRateLimiter implements RateLimiter {

    private final RateLimitPolicyProvider policyProvider;
    private final Cache<String, TokenBucket> bucketsByClientId;

    public TokenBucketRateLimiter(RateLimitPolicyProvider policyProvider, RateLimiterProperties properties) {
        this.policyProvider = policyProvider;
        this.bucketsByClientId = Caffeine.newBuilder()
                .expireAfterAccess(properties.bucketEvictionDuration())
                .build();
    }

    @Override
    public boolean tryAcquire(String clientId) {
        RateLimitPolicy policy = policyProvider.resolvePolicy(clientId);
        TokenBucket bucket = bucketsByClientId.get(clientId, id -> new TokenBucket(policy));
        return bucket.tryConsume(policy);
    }
}
