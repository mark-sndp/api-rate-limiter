package com.ratelimiter.policy;

import com.ratelimiter.domain.RateLimitPolicy;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Duration;

@Entity
@Table(name = "rate_limit_policy")
class RateLimitPolicyEntity {

    @Id
    @Column(name = "client_id", nullable = false, updatable = false)
    private String clientId;

    @Column(name = "max_requests", nullable = false)
    private int maxRequests;

    @Column(name = "window_duration_millis", nullable = false)
    private long windowDurationMillis;

    protected RateLimitPolicyEntity() {
    }

    private RateLimitPolicyEntity(String clientId, int maxRequests, long windowDurationMillis) {
        this.clientId = clientId;
        this.maxRequests = maxRequests;
        this.windowDurationMillis = windowDurationMillis;
    }

    static RateLimitPolicyEntity from(String clientId, RateLimitPolicy policy) {
        long durationMillis = policy.windowDuration().toMillis();
        if (durationMillis <= 0 || !Duration.ofMillis(durationMillis).equals(policy.windowDuration())) {
            throw new IllegalArgumentException("windowDuration must be a positive whole-millisecond duration");
        }
        return new RateLimitPolicyEntity(clientId, policy.maximumRequests(), durationMillis);
    }

    RateLimitPolicy toDomainPolicy() {
        return new RateLimitPolicy(maxRequests, Duration.ofMillis(windowDurationMillis));
    }
}