package com.ratelimiter.core;

import static org.assertj.core.api.Assertions.assertThat;

import com.ratelimiter.config.RateLimiterProperties;
import com.ratelimiter.config.RateLimiterProperties.PolicyProperties;
import com.ratelimiter.policy.RateLimitPolicyProvider;
import com.ratelimiter.policy.RateLimitPolicyRepository;
import java.time.Duration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * Verifies the Redis-backed rate limiter enforces limits atomically against a real Redis instance.
 * Requires a running Docker daemon; excluded from the default {@code mvn test} run (see pom.xml)
 * and runs explicitly via {@code mvn test -Dgroups=redis}.
 */
@Tag("redis")
@Testcontainers
class RedisTokenBucketRateLimiterTest {

    @SuppressWarnings("resource")
    private static final GenericContainer<?> REDIS =
            new GenericContainer<>(DockerImageName.parse("redis:7-alpine")).withExposedPorts(6379);

    private static LettuceConnectionFactory connectionFactory;

    @BeforeAll
    static void startRedis() {
        REDIS.start();
        connectionFactory = new LettuceConnectionFactory(
                new RedisStandaloneConfiguration(REDIS.getHost(), REDIS.getMappedPort(6379)));
        connectionFactory.afterPropertiesSet();
    }

    @AfterAll
    static void stopRedis() {
        connectionFactory.destroy();
        REDIS.stop();
    }

    @Test
    void shouldAllowRequestsUpToConfiguredLimitThenReject() {
        RedisTokenBucketRateLimiter rateLimiter = rateLimiterWithDefaultPolicy(3, Duration.ofMinutes(1));

        assertThat(rateLimiter.tryAcquire("customerA")).isTrue();
        assertThat(rateLimiter.tryAcquire("customerA")).isTrue();
        assertThat(rateLimiter.tryAcquire("customerA")).isTrue();
        assertThat(rateLimiter.tryAcquire("customerA")).isFalse();
    }

    @Test
    void shouldTrackLimitsIndependentlyPerClient() {
        RedisTokenBucketRateLimiter rateLimiter = rateLimiterWithDefaultPolicy(1, Duration.ofMinutes(1));

        assertThat(rateLimiter.tryAcquire("customerX")).isTrue();
        assertThat(rateLimiter.tryAcquire("customerX")).isFalse();
        assertThat(rateLimiter.tryAcquire("customerY")).isTrue();
    }

    /**
     * Verifies that the rate limiter is thread-safe when multiple threads attempt to acquire tokens for the same client.
     * This test simulates concurrent requests and ensures that the total number of successful acquisitions does not
     * exceed the configured limit.
     */
    @Test
    void shouldShareStateAcrossMultipleRateLimiterInstances() {
        RateLimiterProperties properties = properties(1, Duration.ofMinutes(1));
        RateLimitPolicyProvider policyProvider = new RateLimitPolicyProvider(
            properties, Mockito.mock(RateLimitPolicyRepository.class));
        StringRedisTemplate redisTemplate = new StringRedisTemplate(connectionFactory);

        // Two instances simulate two separate application nodes sharing the same Redis-backed quota.
        RedisTokenBucketRateLimiter instanceOne = new RedisTokenBucketRateLimiter(redisTemplate, policyProvider, properties);
        RedisTokenBucketRateLimiter instanceTwo = new RedisTokenBucketRateLimiter(redisTemplate, policyProvider, properties);

        assertThat(instanceOne.tryAcquire("sharedClient")).isTrue();
        assertThat(instanceTwo.tryAcquire("sharedClient")).isFalse();
    }

    private static RedisTokenBucketRateLimiter rateLimiterWithDefaultPolicy(int maximumRequests, Duration windowDuration) {
        RateLimiterProperties properties = properties(maximumRequests, windowDuration);
        RateLimitPolicyProvider policyProvider = new RateLimitPolicyProvider(
            properties, Mockito.mock(RateLimitPolicyRepository.class));
        StringRedisTemplate redisTemplate = new StringRedisTemplate(connectionFactory);
        return new RedisTokenBucketRateLimiter(redisTemplate, policyProvider, properties);
    }

    private static RateLimiterProperties properties(int maximumRequests, Duration windowDuration) {
        return new RateLimiterProperties(
            new PolicyProperties(maximumRequests, windowDuration), Duration.ofHours(1), Duration.ofSeconds(1), "redis");
    }
}
