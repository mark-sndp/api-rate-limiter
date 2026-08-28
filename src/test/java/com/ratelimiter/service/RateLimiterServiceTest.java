package com.ratelimiter.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.ratelimiter.core.RateLimiter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RateLimiterServiceTest {

    private RateLimiter rateLimiter;
    private RateLimiterService rateLimiterService;

    @BeforeEach
    void setUp() {
        rateLimiter = mock(RateLimiter.class);
        rateLimiterService = new RateLimiterService(rateLimiter);
    }

    @Test
    void shouldDelegateToRateLimiterForAllowedRequest() {
        when(rateLimiter.tryAcquire("customerA")).thenReturn(true);

        assertThat(rateLimiterService.allowRequest("customerA")).isTrue();
    }

    @Test
    void shouldDelegateToRateLimiterForRejectedRequest() {
        when(rateLimiter.tryAcquire("customerA")).thenReturn(false);

        assertThat(rateLimiterService.allowRequest("customerA")).isFalse();
    }

    @Test
    void shouldRejectNullClientId() {
        assertThatThrownBy(() -> rateLimiterService.allowRequest(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldRejectBlankClientId() {
        assertThatThrownBy(() -> rateLimiterService.allowRequest("   "))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
