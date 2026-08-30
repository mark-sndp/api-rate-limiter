package com.ratelimiter.core;

import com.ratelimiter.config.RateLimiterProperties;
import com.ratelimiter.domain.RateLimitPolicy;
import com.ratelimiter.policy.RateLimitPolicyProvider;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

/**
 * Token-bucket {@link RateLimiter} backed by Redis, so multiple application instances behind a
 * load balancer share the same per-client quota instead of each enforcing its own independent limit.
 *
 * <p>All state changes happen inside a single Lua script executed atomically by Redis, avoiding the
 * read-modify-write race that separate GET/SET calls would introduce across concurrent instances.
 * Idle buckets expire via Redis key TTL, so memory stays bounded the same way the in-memory
 * implementation evicts idle {@link TokenBucket} entries.
 *
 * <p>Enabled by setting {@code rate-limiter.store=redis}.
 */
@Component
@ConditionalOnProperty(prefix = "rate-limiter", name = "store", havingValue = "redis")
public class RedisTokenBucketRateLimiter implements RateLimiter {

    private static final RedisScript<Long> TOKEN_BUCKET_SCRIPT =
            RedisScript.of(new ClassPathResource("scripts/token_bucket.lua"), Long.class);
    private static final Logger log = LoggerFactory.getLogger(RedisTokenBucketRateLimiter.class);

    private final StringRedisTemplate redisTemplate;
    private final RateLimitPolicyProvider policyProvider;
    private final long bucketEvictionSeconds;

    public RedisTokenBucketRateLimiter(StringRedisTemplate redisTemplate, RateLimitPolicyProvider policyProvider,
            RateLimiterProperties properties) {
        this.redisTemplate = redisTemplate;
        this.policyProvider = policyProvider;
        this.bucketEvictionSeconds = Math.max(1L, (long) Math.ceil(properties.bucketEvictionDuration().toMillis() / 1000.0));
    }

    @Override
    public boolean tryAcquire(String clientId) {
        RateLimitPolicy policy = policyProvider.resolvePolicy(clientId);
        double refillTokensPerMillisecond = policy.refillTokensPerNanosecond() * 1_000_000;

        Long allowed = redisTemplate.execute(TOKEN_BUCKET_SCRIPT, List.of(bucketKey(clientId)),
                String.valueOf(policy.maximumRequests()),
                String.valueOf(refillTokensPerMillisecond),
                String.valueOf(System.currentTimeMillis()),
                String.valueOf(bucketEvictionSeconds));

        if (allowed == null) {
            log.warn("Redis token-bucket script returned no decision for client {}", clientId);
            return false;
        }

        boolean requestAllowed = allowed == 1L;
        log.debug("Redis bucket {} request for client {} using key {} and policy {}/{}",
                requestAllowed ? "allowed" : "rejected", clientId, bucketKey(clientId), policy.maximumRequests(),
                policy.windowDuration());
        return requestAllowed;
    }

    // Curly-brace hash tag keeps a client's bucket on a single Redis Cluster slot.
    private static String bucketKey(String clientId) {
        return "rate-limiter:{" + clientId + "}";
    }
}
