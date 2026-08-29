package com.ratelimiter.policy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ratelimiter.config.RateLimiterProperties;
import com.ratelimiter.config.RateLimiterProperties.PolicyProperties;
import com.ratelimiter.domain.RateLimitPolicy;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RateLimitPolicyProviderTest {

    private static final PolicyProperties DEFAULT_POLICY_PROPERTIES = new PolicyProperties(60, Duration.ofMinutes(1));

    @Mock
    private RateLimitPolicyRepository policyRepository;

    @Test
    void shouldResolveDefaultPolicyForUnknownClient() {
        RateLimitPolicyProvider provider = provider();

        assertThat(provider.resolvePolicy("unknownClient"))
                .isEqualTo(new RateLimitPolicy(60, Duration.ofMinutes(1)));
    }

    @Test
    void shouldResolvePersistedOverrideForKnownClient() {
        when(policyRepository.findById("customerC"))
                .thenReturn(java.util.Optional.of(RateLimitPolicyEntity.from(
                        "customerC", new RateLimitPolicy(10, Duration.ofSeconds(1)))));
        RateLimitPolicyProvider provider = provider();

        assertThat(provider.resolvePolicy("customerC"))
                .isEqualTo(new RateLimitPolicy(10, Duration.ofSeconds(1)));
    }

    @Test
    void shouldApplyRuntimePolicyUpdateImmediately() {
        RateLimitPolicyProvider provider = provider();

        provider.updatePolicy("customerA", new RateLimitPolicy(100, Duration.ofMinutes(1)));

        verify(policyRepository).save(any(RateLimitPolicyEntity.class));
    }

    @Test
    void shouldFallBackToDefaultAfterOverrideRemoved() {
        RateLimitPolicyProvider provider = provider();

        provider.remove("customerA");

        assertThat(provider.resolvePolicy("customerA"))
                .isEqualTo(new RateLimitPolicy(60, Duration.ofMinutes(1)));
        verify(policyRepository).deleteById("customerA");
    }

    @Test
    void shouldInvalidateCachedOverrideAfterUpdate() {
        RateLimitPolicy initialPolicy = new RateLimitPolicy(10, Duration.ofSeconds(1));
        when(policyRepository.findById("customerA"))
                .thenReturn(java.util.Optional.of(RateLimitPolicyEntity.from("customerA", initialPolicy)));
        RateLimitPolicyProvider provider = provider();

        assertThat(provider.resolvePolicy("customerA")).isEqualTo(initialPolicy);
        provider.updatePolicy("customerA", new RateLimitPolicy(100, Duration.ofMinutes(1)));
        provider.resolvePolicy("customerA");

        verify(policyRepository, org.mockito.Mockito.times(2)).findById("customerA");
    }

    @Test
    void shouldRejectBlankClientIds() {
        RateLimitPolicyProvider provider = provider();

        assertThatThrownBy(() -> provider.resolvePolicy("   "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("clientId");
    }

    @Test
    void shouldApplyDefaultValuesWhenOptionalPropertiesAreMissing() {
        RateLimiterProperties properties = new RateLimiterProperties(null, null, null, null);

        assertThat(properties.defaultPolicy()).isEqualTo(new PolicyProperties(60, Duration.ofMinutes(1)));
        assertThat(properties.bucketEvictionDuration()).isEqualTo(Duration.ofHours(1));
        assertThat(properties.policyCacheDuration()).isEqualTo(Duration.ofSeconds(1));
        assertThat(properties.store()).isEqualTo("in-memory");
    }

    private RateLimitPolicyProvider provider() {
        RateLimiterProperties properties = new RateLimiterProperties(
                DEFAULT_POLICY_PROPERTIES, Duration.ofHours(1), Duration.ofSeconds(1), null);
        return new RateLimitPolicyProvider(properties, policyRepository);
    }
}
