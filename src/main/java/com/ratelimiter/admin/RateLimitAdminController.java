package com.ratelimiter.admin;

import com.ratelimiter.domain.RateLimitPolicy;
import com.ratelimiter.policy.RateLimitPolicyProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Runtime administration of per-client rate limit policies, without requiring a restart.
 */
@RestController
@RequestMapping("/api/rate-limits")
public class RateLimitAdminController {

    private static final Logger log = LoggerFactory.getLogger(RateLimitAdminController.class);

    private final RateLimitPolicyProvider policyProvider;

    public RateLimitAdminController(RateLimitPolicyProvider policyProvider) {
        this.policyProvider = policyProvider;
    }

    @GetMapping("/{clientId}")
    public RateLimitPolicyResponse getPolicy(@PathVariable String clientId) {
        RateLimitPolicy policy = policyProvider.resolvePolicy(clientId);
        log.debug("Retrieved effective rate limit policy for client {}", clientId);
        return toResponse(clientId, policy);
    }

    @PutMapping("/{clientId}")
    public RateLimitPolicyResponse updatePolicy(@PathVariable String clientId,
            @RequestBody UpdateRateLimitPolicyRequest request) {
        RateLimitPolicy policy = new RateLimitPolicy(request.maxRequests(), request.windowDuration());
        policyProvider.updatePolicy(clientId, policy);
        log.info("Received policy update request for client {}", clientId);
        return toResponse(clientId, policy);
    }

    @DeleteMapping("/{clientId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void resetPolicyToDefault(@PathVariable String clientId) {
        policyProvider.remove(clientId);
        log.info("Received policy reset request for client {}", clientId);
    }

    private static RateLimitPolicyResponse toResponse(String clientId, RateLimitPolicy policy) {
        return new RateLimitPolicyResponse(clientId, policy.maximumRequests(), policy.windowDuration());
    }
}
