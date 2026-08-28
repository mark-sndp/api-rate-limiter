package com.ratelimiter.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class RateLimitPolicyTest {

    @Test
    void shouldComputeRefillRateFromMaximumRequestsAndWindow() {
        RateLimitPolicy policy = new RateLimitPolicy(100, Duration.ofSeconds(1));

        double expectedTokensPerNanosecond = 100.0 / Duration.ofSeconds(1).toNanos();
        assertThat(policy.refillTokensPerNanosecond()).isEqualTo(expectedTokensPerNanosecond);
    }

    @Test
    void shouldRejectNonPositiveMaximumRequests() {
        assertThatThrownBy(() -> new RateLimitPolicy(0, Duration.ofMinutes(1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("maximumRequests");

        assertThatThrownBy(() -> new RateLimitPolicy(-5, Duration.ofMinutes(1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("maximumRequests");
    }

    @Test
    void shouldRejectNullWindowDuration() {
        assertThatThrownBy(() -> new RateLimitPolicy(10, null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void shouldRejectZeroOrNegativeWindowDuration() {
        assertThatThrownBy(() -> new RateLimitPolicy(10, Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("windowDuration");

        assertThatThrownBy(() -> new RateLimitPolicy(10, Duration.ofSeconds(-1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("windowDuration");
    }

    @Test
    void shouldSupportValueEquality() {
        RateLimitPolicy first = new RateLimitPolicy(100, Duration.ofMinutes(1));
        RateLimitPolicy second = new RateLimitPolicy(100, Duration.ofMinutes(1));

        assertThat(first).isEqualTo(second);
        assertThat(first.hashCode()).isEqualTo(second.hashCode());
    }
}
