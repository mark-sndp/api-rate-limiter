package com.ratelimiter.policy;

import org.springframework.data.jpa.repository.JpaRepository;

public interface RateLimitPolicyRepository extends JpaRepository<RateLimitPolicyEntity, String> {
}