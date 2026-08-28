package com.ratelimiter.admin;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ratelimiter.domain.RateLimitPolicy;
import com.ratelimiter.policy.RateLimitPolicyProvider;
import com.ratelimiter.service.RateLimiterService;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

// RateLimitingFilter is auto-detected by @WebMvcTest and needs RateLimiterService, so it must be mocked too.
@WebMvcTest(RateLimitAdminController.class)
class RateLimitAdminControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RateLimitPolicyProvider policyProvider;

    @MockitoBean
    private RateLimiterService rateLimiterService;

    @Test
    void shouldReturnCurrentPolicyForClient() throws Exception {
        when(policyProvider.resolvePolicy("customerA")).thenReturn(new RateLimitPolicy(100, Duration.ofMinutes(1)));

        mockMvc.perform(get("/api/rate-limits/customerA"))
                .andExpect(status().isOk())
                .andExpect(content().json("""
                        {"clientId":"customerA","maxRequests":100,"windowDuration":"PT1M"}
                        """));
    }

    @Test
    void shouldUpdatePolicyAndReturnEffectiveValue() throws Exception {
        mockMvc.perform(put("/api/rate-limits/customerA")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"maxRequests":200,"windowDuration":"PT1M"}
                                """))
                .andExpect(status().isOk())
                .andExpect(content().json("""
                        {"clientId":"customerA","maxRequests":200,"windowDuration":"PT1M"}
                        """));

        verify(policyProvider).updatePolicy(eq("customerA"), any(RateLimitPolicy.class));
    }

    @Test
    void shouldRejectInvalidPolicyWithBadRequest() throws Exception {
        mockMvc.perform(put("/api/rate-limits/customerA")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"maxRequests":0,"windowDuration":"PT1M"}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldResetPolicyToDefault() throws Exception {
        mockMvc.perform(delete("/api/rate-limits/customerA"))
                .andExpect(status().isNoContent());

        verify(policyProvider).removeOverride("customerA");
    }
}
