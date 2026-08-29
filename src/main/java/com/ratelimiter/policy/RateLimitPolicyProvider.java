package com.ratelimiter.policy;

import com.ratelimiter.config.RateLimiterProperties;
import com.ratelimiter.domain.RateLimitPolicy;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.util.Objects;
import java.util.Optional;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Resolves the effective {@link RateLimitPolicy} for a client, combining a configured
 * default from configuration with durable, per-client database overrides.
 */
@Component
public class RateLimitPolicyProvider {

    private final RateLimitPolicy defaultPolicy;
    private final RateLimitPolicyRepository policyRepository;
    private final Cache<String, Optional<RateLimitPolicy>> policiesByClientId;

    public RateLimitPolicyProvider(RateLimiterProperties properties, RateLimitPolicyRepository policyRepository) {
        this.defaultPolicy = toRateLimitPolicy(properties.defaultPolicy());
        this.policyRepository = policyRepository;
        this.policiesByClientId = Caffeine.newBuilder()
                .expireAfterWrite(properties.policyCacheDuration())
                .build();
    }

    /** Returns the client's override policy if one exists, otherwise the current default policy. */
    public RateLimitPolicy resolvePolicy(String clientId) {
        validateClientId(clientId);
        return policiesByClientId.get(clientId, this::fetchFromDB)
                .orElse(defaultPolicy);
    }

    @Transactional
    public void updatePolicy(String clientId, RateLimitPolicy policy) {
        validateClientId(clientId);
        policyRepository.save(RateLimitPolicyEntity.from(clientId, Objects.requireNonNull(policy, "policy must not be null")));
        policiesByClientId.invalidate(clientId);
    }

    @Transactional
    public void remove(String clientId) {
        validateClientId(clientId);
        policyRepository.deleteById(clientId);
        policiesByClientId.invalidate(clientId);
    }

    // Converts the configuration properties to a {@link RateLimitPolicy} instance.
    private static RateLimitPolicy toRateLimitPolicy(RateLimiterProperties.PolicyProperties properties) {
        Objects.requireNonNull(properties, "policy properties must not be null");
        return new RateLimitPolicy(properties.maxRequests(), properties.windowDuration());
    }

    private Optional<RateLimitPolicy> fetchFromDB(String clientId) {
        return policyRepository.findById(clientId).map(RateLimitPolicyEntity::toDomainPolicy);
    }

    private static void validateClientId(String clientId) {
        if (clientId == null || clientId.isBlank()) {
            throw new IllegalArgumentException("clientId must not be null or blank");
        }
    }
}
