package com.ratelimiter.core;

import static org.assertj.core.api.Assertions.assertThat;

import com.ratelimiter.domain.RateLimitPolicy;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

class TokenBucketTest {

    @Test
    void shouldAllowRequestsUpToBurstCapacity() {
        AtomicLong clockNanos = new AtomicLong(0);
        RateLimitPolicy policy = new RateLimitPolicy(3, Duration.ofSeconds(1));
        TokenBucket bucket = new TokenBucket(policy, clockNanos::get);

        assertThat(bucket.tryConsume(policy)).isTrue();
        assertThat(bucket.tryConsume(policy)).isTrue();
        assertThat(bucket.tryConsume(policy)).isTrue();
        assertThat(bucket.tryConsume(policy)).isFalse();
    }

    @Test
    void shouldRefillTokensProportionallyToElapsedTime() {
        AtomicLong clockNanos = new AtomicLong(0);
        RateLimitPolicy policy = new RateLimitPolicy(2, Duration.ofSeconds(1));
        TokenBucket bucket = new TokenBucket(policy, clockNanos::get);

        assertThat(bucket.tryConsume(policy)).isTrue();
        assertThat(bucket.tryConsume(policy)).isTrue();
        assertThat(bucket.tryConsume(policy)).isFalse();

        clockNanos.addAndGet(Duration.ofMillis(500).toNanos());
        assertThat(bucket.tryConsume(policy)).isTrue();
        assertThat(bucket.tryConsume(policy)).isFalse();
    }

    @Test
    void shouldNeverExceedMaximumCapacityEvenAfterLongIdlePeriod() {
        AtomicLong clockNanos = new AtomicLong(0);
        RateLimitPolicy policy = new RateLimitPolicy(5, Duration.ofSeconds(1));
        TokenBucket bucket = new TokenBucket(policy, clockNanos::get);

        assertThat(bucket.tryConsume(policy)).isTrue();
        clockNanos.addAndGet(Duration.ofHours(1).toNanos());

        for (int i = 0; i < 5; i++) {
            assertThat(bucket.tryConsume(policy)).isTrue();
        }
        assertThat(bucket.tryConsume(policy)).isFalse();
    }

    @Test
    void shouldApplyNewPolicyImmediatelyWhileCappingExistingTokensToNewCapacity() {
        AtomicLong clockNanos = new AtomicLong(0);
        RateLimitPolicy generousPolicy = new RateLimitPolicy(10, Duration.ofSeconds(1));
        TokenBucket bucket = new TokenBucket(generousPolicy, clockNanos::get);

        RateLimitPolicy strictPolicy = new RateLimitPolicy(2, Duration.ofSeconds(1));
        assertThat(bucket.tryConsume(strictPolicy)).isTrue();
        assertThat(bucket.tryConsume(strictPolicy)).isTrue();
        assertThat(bucket.tryConsume(strictPolicy)).isFalse();
    }
}
