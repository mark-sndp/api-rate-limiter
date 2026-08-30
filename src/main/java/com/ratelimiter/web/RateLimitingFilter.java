package com.ratelimiter.web;

import com.ratelimiter.service.RateLimiterService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Applies per-client rate limiting to incoming HTTP requests, identifying the
 * client via the {@value #CLIENT_ID_HEADER} header. Admin endpoints are excluded
 * so operators can always manage policies even for a currently throttled client.
 */
@Component
public class RateLimitingFilter extends OncePerRequestFilter {

    public static final String CLIENT_ID_HEADER = "X-Client-Id";

    private static final Logger log = LoggerFactory.getLogger(RateLimitingFilter.class);

    private final RateLimiterService rateLimiterService;

    public RateLimitingFilter(RateLimiterService rateLimiterService) {
        this.rateLimiterService = rateLimiterService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String clientId = request.getHeader(CLIENT_ID_HEADER);
        if (clientId == null || clientId.isBlank()) {
            log.info("Rejected {} {} because the required {} header is missing", request.getMethod(),
                request.getRequestURI(), CLIENT_ID_HEADER);
            response.sendError(HttpStatus.BAD_REQUEST.value(), "Missing required header: " + CLIENT_ID_HEADER);
            return;
        }

        if (!rateLimiterService.allowRequest(clientId)) {
            log.info("Rate limit exceeded for client {} on {} {}", clientId, request.getMethod(),
                request.getRequestURI());
            response.sendError(HttpStatus.TOO_MANY_REQUESTS.value(), "Rate limit exceeded for client: " + clientId);
            return;
        }

        log.debug("Rate limit accepted request for client {} on {} {}", clientId, request.getMethod(),
            request.getRequestURI());
        filterChain.doFilter(request, response);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return request.getRequestURI().startsWith("/api/rate-limits");
    }
}
