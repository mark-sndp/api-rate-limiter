# api-rate-limiter

An in-memory, per-client API rate limiting service built with Java 21 and Spring Boot 4.

## Core API

```java
boolean allowRequest(String clientId)
```

Exposed via `RateLimiterService`. Returns `true` if the request is allowed under the client's
current policy, `false` if the client has exceeded its limit.

## How it works

- **Algorithm**: token bucket per client (`TokenBucket`). Each client has a bucket that holds up
  to `maxRequests` tokens and refills continuously at `maxRequests / windowDuration`. This allows
  short bursts up to the configured maximum while enforcing a steady average rate.
- **Thread safety**: each `TokenBucket` guards its state with a single `synchronized` method, so
  concurrent requests for the same client are serialized correctly. Buckets are stored in a
  `com.github.benmanes.caffeine.cache.Cache<String, TokenBucket>`, which is safe for concurrent
  access across many different clients.
- **Bounded memory**: buckets are evicted after `rate-limiter.bucket-eviction-duration` of
  inactivity (Caffeine `expireAfterAccess`), so memory does not grow indefinitely as new clients
  are seen. An evicted client simply gets a fresh, full bucket on its next request — this is safe
  (never more permissive than the configured rate over any window), just not persisted forever.
- **Per-client configuration**: `RateLimitPolicyProvider` resolves a policy per `clientId`,
  falling back to a configured default. Overrides can be changed at runtime (see Admin API below)
  without restarting the service.
- **Design patterns used**: `RateLimiter` is a Strategy interface (`TokenBucketRateLimiter` is the
  current implementation) so an alternative algorithm or distributed store can be substituted
  without touching `RateLimiterService` or callers. `RateLimiterService` is a Facade over the
  strategy for the rest of the application.

## Configuration

Default and per-customer policies are configured in `application.yml`:

```yaml
rate-limiter:
  default-policy:
    max-requests: 60
    window-duration: 1m
  bucket-eviction-duration: 1h
  customer-policies:
    customerA:
      max-requests: 100
      window-duration: 1m
    customerB:
      max-requests: 1000
      window-duration: 1m
    customerC:
      max-requests: 10
      window-duration: 1s
```

### Runtime admin API

Policies can be inspected and changed at runtime without a restart:

| Method | Path                          | Description                                  |
|--------|-------------------------------|-----------------------------------------------|
| GET    | `/api/rate-limits/{clientId}` | Get the effective policy for a client        |
| PUT    | `/api/rate-limits/{clientId}` | Set/override the policy for a client         |
| DELETE | `/api/rate-limits/{clientId}` | Remove the override, revert to default       |

Example:

```bash
curl -X PUT localhost:8080/api/rate-limits/customerD \
  -H "Content-Type: application/json" \
  -d '{"maxRequests": 500, "windowDuration": "PT1M"}'
```

### Demo protected endpoint

`GET /api/ping` is guarded by `RateLimitingFilter`, which identifies the caller via the
`X-Client-Id` header and returns `429 Too Many Requests` once the limit is exceeded:

```bash
curl -H "X-Client-Id: customerA" localhost:8080/api/ping
```

## Running

```bash
mvn spring-boot:run
```

## Testing

```bash
mvn test
```

Includes unit tests (domain validation, token bucket refill math, policy resolution), a
multi-threaded concurrency test asserting exactly `maxRequests` successes under concurrent load,
MockMvc tests for the admin API, and a full Spring Boot integration test reproducing the exact
Customer A/B/C scenarios from the requirements.

## Assumptions & production notes

- **Single-node, in-memory design**: this implementation targets a single JVM instance. If the
  service is scaled horizontally behind a load balancer, each instance would enforce its own
  independent limit, effectively multiplying the true limit by the instance count. The
  `RateLimiter` interface is the intended seam for a distributed implementation (e.g. Redis with
  an atomic Lua script) without changing `RateLimiterService` or any caller.
- **Policies are not persisted**: runtime overrides live only in memory and are lost on restart.
  A production deployment would back `RateLimitPolicyProvider` with a database or config service.
- **No authentication on the admin API**: `/api/rate-limits/**` has no access control in this
  exercise. Production use requires securing these endpoints (e.g. Spring Security with RBAC).
- **No request queueing**: requests exceeding the limit are rejected immediately, per the
  requirements — there is no fairness queue or backoff/retry mechanism.
- **Monotonic clock**: refill math uses `System.nanoTime()`, which is immune to wall-clock/NTP
  adjustments.
