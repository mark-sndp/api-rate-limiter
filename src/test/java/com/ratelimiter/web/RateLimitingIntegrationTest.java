package com.ratelimiter.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.ratelimiter.admin.RateLimitPolicyResponse;
import com.ratelimiter.admin.UpdateRateLimitPolicyRequest;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * End-to-end verification of the exact scenarios called out in the requirements:
 * customerA 100 req/min, customerB 1000 req/min, customerC 10 req/sec.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@Testcontainers
class RateLimitingIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgresql = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private TestRestTemplate testRestTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void customerAIsConfiguredWithOneHundredRequestsPerMinute() {
        assertThat(policyFor("customerA").maxRequests()).isEqualTo(100);
        assertThat(policyFor("customerA").windowDuration()).isEqualTo(Duration.ofMinutes(1));
    }

    @Test
    void customerBIsConfiguredWithOneThousandRequestsPerMinute() {
        assertThat(policyFor("customerB").maxRequests()).isEqualTo(1000);
        assertThat(policyFor("customerB").windowDuration()).isEqualTo(Duration.ofMinutes(1));
    }

    @Test
    void customerCIsConfiguredWithTenRequestsPerSecond() {
        assertThat(policyFor("customerC").maxRequests()).isEqualTo(10);
        assertThat(policyFor("customerC").windowDuration()).isEqualTo(Duration.ofSeconds(1));
    }

    // Overrides use a long window so token refill during the HTTP round-trips below can't flake the assertions.
    @Test
    void requestsExceedingTheConfiguredLimitAreRejected() {
        overridePolicy("customerUnderTest", 5, Duration.ofHours(1));

        for (int i = 1; i <= 5; i++) {
            assertThat(pingAs("customerUnderTest").getStatusCode()).isEqualTo(HttpStatus.OK);
        }
        assertThat(pingAs("customerUnderTest").getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
    }

    @Test
    void requestsWithinTheConfiguredLimitAreIndependentPerClient() {
        overridePolicy("customerOne", 1, Duration.ofHours(1));
        overridePolicy("customerTwo", 1, Duration.ofHours(1));

        assertThat(pingAs("customerOne").getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(pingAs("customerOne").getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(pingAs("customerTwo").getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void customerPolicyUpdatesArePersistedInPostgreSQL() {
        overridePolicy("durableCustomer", 25, Duration.ofMinutes(2));

        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM rate_limit_policy WHERE client_id = ? AND max_requests = ? "
                        + "AND window_duration_millis = ?",
                Integer.class, "durableCustomer", 25, Duration.ofMinutes(2).toMillis());

        assertThat(count).isEqualTo(1);
    }

    @Test
    void requestsWithoutClientIdHeaderAreRejected() {
        ResponseEntity<String> response = testRestTemplate.getForEntity("/api/ping", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    private RateLimitPolicyResponse policyFor(String clientId) {
        ResponseEntity<RateLimitPolicyResponse> response =
                testRestTemplate.getForEntity("/api/rate-limits/" + clientId, RateLimitPolicyResponse.class);
        return response.getBody();
    }

    private void overridePolicy(String clientId, int maxRequests, Duration windowDuration) {
        testRestTemplate.put("/api/rate-limits/" + clientId, new UpdateRateLimitPolicyRequest(maxRequests, windowDuration));
    }

    private ResponseEntity<String> pingAs(String clientId) {
        HttpHeaders headers = new HttpHeaders();
        headers.set(RateLimitingFilter.CLIENT_ID_HEADER, clientId);
        return testRestTemplate.exchange("/api/ping", org.springframework.http.HttpMethod.GET,
                new HttpEntity<>(headers), String.class);
    }
}
