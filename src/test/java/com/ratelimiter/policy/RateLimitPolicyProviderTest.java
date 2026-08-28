package com.ratelimiter.policy;

import static org.assertj.core.api.Assertions.assertThat;

import com.ratelimiter.config.RateLimiterProperties;
import com.ratelimiter.config.RateLimiterProperties.PolicyProperties;
import com.ratelimiter.domain.RateLimitPolicy;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RateLimitPolicyProviderTest {

    private static final PolicyProperties DEFAULT_POLICY_PROPERTIES = new PolicyProperties(60, Duration.ofMinutes(1));

    @Test
    void shouldResolveDefaultPolicyForUnknownClient() {
        RateLimitPolicyProvider provider = providerWithCustomerPolicies(Map.of());

        assertThat(provider.resolvePolicy("unknownClient"))
                .isEqualTo(new RateLimitPolicy(60, Duration.ofMinutes(1)));
    }

    @Test
    void shouldResolveConfiguredOverrideForKnownClient() {
        RateLimitPolicyProvider provider = providerWithCustomerPolicies(
                Map.of("customerC", new PolicyProperties(10, Duration.ofSeconds(1))));

        assertThat(provider.resolvePolicy("customerC"))
                .isEqualTo(new RateLimitPolicy(10, Duration.ofSeconds(1)));
    }

    @Test
    void shouldApplyRuntimePolicyUpdateImmediately() {
        RateLimitPolicyProvider provider = providerWithCustomerPolicies(Map.of());

        provider.updatePolicy("customerA", new RateLimitPolicy(100, Duration.ofMinutes(1)));

        assertThat(provider.resolvePolicy("customerA"))
                .isEqualTo(new RateLimitPolicy(100, Duration.ofMinutes(1)));
    }

    @Test
    void shouldFallBackToDefaultAfterOverrideRemoved() {
        RateLimitPolicyProvider provider = providerWithCustomerPolicies(
                Map.of("customerA", new PolicyProperties(100, Duration.ofMinutes(1))));

        provider.removeOverride("customerA");

        assertThat(provider.resolvePolicy("customerA"))
                .isEqualTo(new RateLimitPolicy(60, Duration.ofMinutes(1)));
    }

    @Test
    void shouldApplyRuntimeDefaultPolicyUpdateToClientsWithoutOverride() {
        RateLimitPolicyProvider provider = providerWithCustomerPolicies(Map.of());

        provider.updateDefaultPolicy(new RateLimitPolicy(5, Duration.ofSeconds(1)));

        assertThat(provider.resolvePolicy("anyClient"))
                .isEqualTo(new RateLimitPolicy(5, Duration.ofSeconds(1)));
    }

    private static RateLimitPolicyProvider providerWithCustomerPolicies(Map<String, PolicyProperties> customerPolicies) {
        RateLimiterProperties properties = new RateLimiterProperties(
                DEFAULT_POLICY_PROPERTIES, Duration.ofHours(1), customerPolicies, null);
        return new RateLimitPolicyProvider(properties);
    }
}
