package com.ratelimiter.core;

import static org.assertj.core.api.Assertions.assertThat;

import com.ratelimiter.config.RateLimiterProperties;
import com.ratelimiter.config.RateLimiterProperties.PolicyProperties;
import com.ratelimiter.policy.RateLimitPolicyProvider;
import com.ratelimiter.policy.RateLimitPolicyRepository;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class TokenBucketRateLimiterTest {

    @Test
    void shouldAllowRequestsUpToConfiguredLimitThenReject() {
        TokenBucketRateLimiter rateLimiter = rateLimiterWithDefaultPolicy(3, Duration.ofMinutes(1));

        assertThat(rateLimiter.tryAcquire("customerA")).isTrue();
        assertThat(rateLimiter.tryAcquire("customerA")).isTrue();
        assertThat(rateLimiter.tryAcquire("customerA")).isTrue();
        assertThat(rateLimiter.tryAcquire("customerA")).isFalse();
    }

    @Test
    void shouldTrackLimitsIndependentlyPerClient() {
        TokenBucketRateLimiter rateLimiter = rateLimiterWithDefaultPolicy(1, Duration.ofMinutes(1));

        assertThat(rateLimiter.tryAcquire("customerA")).isTrue();
        assertThat(rateLimiter.tryAcquire("customerA")).isFalse();
        assertThat(rateLimiter.tryAcquire("customerB")).isTrue();
    }

    /**
     * Verifies that the rate limiter is thread-safe when multiple threads attempt to acquire tokens for the same client.
     * This test simulates concurrent requests and ensures that the total number of successful acquisitions does not
     * exceed the configured limit.
     */
    @Test
    void shouldRemainThreadSafeUnderConcurrentRequestsFromSameClient() throws InterruptedException {
        int maximumRequests = 20;
        int threadCount = 50;
        TokenBucketRateLimiter rateLimiter = rateLimiterWithDefaultPolicy(maximumRequests, Duration.ofHours(1));

        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch readyLatch = new CountDownLatch(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        AtomicInteger successCount = new AtomicInteger();

        for (int i = 0; i < threadCount; i++) {
            executorService.submit(() -> {
                readyLatch.countDown();
                awaitUninterruptibly(startLatch);
                if (rateLimiter.tryAcquire("sharedClient")) {
                    successCount.incrementAndGet();
                }
            });
        }

        readyLatch.await();
        startLatch.countDown();
        executorService.shutdown();
        assertThat(executorService.awaitTermination(5, TimeUnit.SECONDS)).isTrue();

        assertThat(successCount.get()).isEqualTo(maximumRequests);
    }

    private static void awaitUninterruptibly(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }

    private static TokenBucketRateLimiter rateLimiterWithDefaultPolicy(int maximumRequests, Duration windowDuration) {
        RateLimiterProperties properties = new RateLimiterProperties(
            new PolicyProperties(maximumRequests, windowDuration), Duration.ofHours(1), Duration.ofSeconds(1), null);
        RateLimitPolicyProvider policyProvider = new RateLimitPolicyProvider(
            properties, Mockito.mock(RateLimitPolicyRepository.class));
        return new TokenBucketRateLimiter(policyProvider, properties);
    }
}
