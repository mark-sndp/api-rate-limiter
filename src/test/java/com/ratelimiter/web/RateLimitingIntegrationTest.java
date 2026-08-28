package com.ratelimiter.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.ratelimiter.admin.RateLimitPolicyResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * End-to-end verification of the exact scenarios called out in the requirements:
 * customerA 100 req/min, customerB 1000 req/min, customerC 10 req/sec.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class RateLimitingIntegrationTest {

    @Autowired
    private TestRestTemplate testRestTemplate;

    @Test
    void customerAIsAllowedExactlyOneHundredRequestsPerMinute() {
        for (int i = 1; i <= 100; i++) {
            assertThat(pingAs("customerA").getStatusCode()).isEqualTo(HttpStatus.OK);
        }
        assertThat(pingAs("customerA").getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
    }

    @Test
    void customerCIsAllowedExactlyTenRequestsPerSecond() {
        for (int i = 1; i <= 10; i++) {
            assertThat(pingAs("customerC").getStatusCode()).isEqualTo(HttpStatus.OK);
        }
        assertThat(pingAs("customerC").getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
    }

    @Test
    void customerBHasAConfiguredLimitOfOneThousandRequestsPerMinuteAndIsInitiallyAllowed() {
        ResponseEntity<RateLimitPolicyResponse> policyResponse =
                testRestTemplate.getForEntity("/api/rate-limits/customerB", RateLimitPolicyResponse.class);

        assertThat(policyResponse.getBody()).isNotNull();
        assertThat(policyResponse.getBody().maxRequests()).isEqualTo(1000);
        assertThat(pingAs("customerB").getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void requestsWithoutClientIdHeaderAreRejected() {
        ResponseEntity<String> response = testRestTemplate.getForEntity("/api/ping", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    private ResponseEntity<String> pingAs(String clientId) {
        HttpHeaders headers = new HttpHeaders();
        headers.set(RateLimitingFilter.CLIENT_ID_HEADER, clientId);
        return testRestTemplate.exchange("/api/ping", org.springframework.http.HttpMethod.GET,
                new HttpEntity<>(headers), String.class);
    }
}
