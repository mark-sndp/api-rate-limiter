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
- **Horizontal scaling**: `RateLimiter` is a Strategy interface. `TokenBucketRateLimiter` (in-memory,
  default) is limited to a single JVM. `RedisTokenBucketRateLimiter` shares bucket state across every
  application instance via Redis, so multiple instances behind a load balancer enforce one combined
  limit instead of each enforcing its own independent quota. Select it with `rate-limiter.store=redis`.
  All state mutation happens inside one atomic Lua script (`scripts/token_bucket.lua`) to avoid a
  read-modify-write race between concurrent instances; idle buckets expire via Redis key TTL, which
  bounds memory the same way the in-memory implementation evicts idle buckets.
- **Design patterns used**: `RateLimiterService` is a Facade over the `RateLimiter` strategy for the
  rest of the application.

## Configuration

Default and per-customer policies are configured in `application.yml`:

```yaml
rate-limiter:
  # "in-memory" (default, single JVM only) or "redis" (shared state, required for horizontal scaling).
  store: in-memory
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

When `rate-limiter.store` is `redis`, the connection is configured via the standard
`spring.data.redis.host` / `spring.data.redis.port` properties (defaults: `localhost` / `6379`).

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

### Running multiple instances with Docker Compose

`docker-compose.yml` starts Redis and one or more app instances configured with
`RATE_LIMITER_STORE=redis`, so all instances share the same per-client quota:

```bash
docker compose up --build --scale app=3
```

Each replica gets a random host port (no fixed port mapping, to avoid conflicts when scaled).
Find them with:

```bash
docker compose ps
```

Requests to any replica for the same `X-Client-Id` count against the same shared limit, so hitting
different replicas in round-robin does not let a client exceed its configured rate.

## Testing

```bash
mvn test
```

Includes unit tests (domain validation, token bucket refill math, policy resolution), a
multi-threaded concurrency test asserting exactly `maxRequests` successes under concurrent load,
MockMvc tests for the admin API, and a full Spring Boot integration test reproducing the exact
Customer A/B/C scenarios from the requirements.

The Redis-backed rate limiter has its own Testcontainers-based test
(`RedisTokenBucketRateLimiterTest`). It requires a running Docker daemon and is excluded from the
default run; execute it explicitly with:

```bash
mvn test -Dgroups=redis
```

## Assumptions & production notes

- **In-memory is the default store**, scoped to a single JVM instance — matches the original
  requirement's simplest interpretation and requires no extra infrastructure to run or test.
  Switching a horizontally-scaled deployment to the Redis store (`rate-limiter.store=redis`) is a
  configuration change, not a code change, thanks to the `RateLimiter` strategy interface.
- **Redis is a single point of failure/bottleneck once enabled**: this implementation targets a
  single Redis instance (or a client-side-compatible cluster via hash-tagged keys). Production use
  at larger scale would want Redis Cluster/Sentinel for availability and to shard bucket keys.
- **Policies are not persisted**: runtime overrides live only in memory (or only in the in-memory
  `RateLimitPolicyProvider`, regardless of which `RateLimiter` store is active) and are lost on
  restart. A production deployment would back `RateLimitPolicyProvider` with a database or config
  service, or replicate policy updates to all instances (e.g. via Redis pub/sub) when running the
  Redis store.
- **No authentication on the admin API**: `/api/rate-limits/**` has no access control in this
  exercise. Production use requires securing these endpoints (e.g. Spring Security with RBAC).
- **No request queueing**: requests exceeding the limit are rejected immediately, per the
  requirements — there is no fairness queue or backoff/retry mechanism.
- **Monotonic clock**: the in-memory implementation uses `System.nanoTime()` (immune to wall-clock/
  NTP adjustments); the Redis implementation uses `System.currentTimeMillis()` from whichever
  instance handles the request, so significant clock drift between application hosts could skew
  refill timing slightly — acceptable for rate limiting, but worth running NTP in production.
