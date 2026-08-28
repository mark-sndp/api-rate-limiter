package com.ratelimiter.policy;

import com.ratelimiter.config.RateLimiterProperties;
import com.ratelimiter.domain.RateLimitPolicy;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.stereotype.Component;

/**
 * Resolves the effective {@link RateLimitPolicy} for a client, combining a configured
 * default with per-client overrides that can be changed at runtime without a restart.
 */
@Component
public class RateLimitPolicyProvider {

    private final ConcurrentMap<String, RateLimitPolicy> customerOverrides = new ConcurrentHashMap<>();
    private volatile RateLimitPolicy defaultPolicy;

    public RateLimitPolicyProvider(RateLimiterProperties properties) {
        this.defaultPolicy = toRateLimitPolicy(properties.defaultPolicy());
        for (Map.Entry<String, RateLimiterProperties.PolicyProperties> entry : properties.customerPolicies().entrySet()) {
            customerOverrides.put(entry.getKey(), toRateLimitPolicy(entry.getValue()));
        }
    }

    /** Returns the client's override policy if one exists, otherwise the current default policy. */
    public RateLimitPolicy resolvePolicy(String clientId) {
        return customerOverrides.getOrDefault(clientId, defaultPolicy);
    }

    public void updatePolicy(String clientId, RateLimitPolicy policy) {
        customerOverrides.put(clientId, Objects.requireNonNull(policy, "policy must not be null"));
    }

    public void removeOverride(String clientId) {
        customerOverrides.remove(clientId);
    }

    public void updateDefaultPolicy(RateLimitPolicy policy) {
        this.defaultPolicy = Objects.requireNonNull(policy, "policy must not be null");
    }

    private static RateLimitPolicy toRateLimitPolicy(RateLimiterProperties.PolicyProperties properties) {
        Objects.requireNonNull(properties, "policy properties must not be null");
        return new RateLimitPolicy(properties.maxRequests(), properties.windowDuration());
    }
}
