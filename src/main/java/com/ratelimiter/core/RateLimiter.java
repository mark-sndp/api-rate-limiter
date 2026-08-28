package com.ratelimiter.core;

/**
 * Strategy abstraction for admitting or rejecting a request for a given client.
 * Implementations decide the algorithm (token bucket, sliding window, etc.) and
 * where bucket state lives (in-memory, distributed cache), allowing this seam to
 * be swapped for a distributed implementation without changing calling code.
 */
public interface RateLimiter {

    /**
     * Attempts to consume one unit of quota for the given client.
     *
     * @param clientId identifier of the client making the request
     * @return true if the request is allowed, false if the client has exceeded its limit
     */
    boolean tryAcquire(String clientId);
}
