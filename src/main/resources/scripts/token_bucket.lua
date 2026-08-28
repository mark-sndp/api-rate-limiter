-- Atomic token bucket check-and-consume, shared across all application instances.
-- KEYS[1] = bucket key
-- ARGV[1] = capacity (maximum tokens)
-- ARGV[2] = refill tokens per millisecond
-- ARGV[3] = current time in epoch milliseconds
-- ARGV[4] = idle expiry in seconds (bounds memory; bucket resets to full after this much inactivity)
-- returns 1 if the request is allowed, 0 otherwise

local capacity = tonumber(ARGV[1])
local refillPerMillisecond = tonumber(ARGV[2])
local now = tonumber(ARGV[3])
local expirySeconds = tonumber(ARGV[4])

local bucket = redis.call('HMGET', KEYS[1], 'tokens', 'timestamp')
local tokens = tonumber(bucket[1])
local timestamp = tonumber(bucket[2])

if tokens == nil then
    tokens = capacity
    timestamp = now
end

local elapsed = math.max(0, now - timestamp)
tokens = math.min(capacity, tokens + (elapsed * refillPerMillisecond))

local allowed = 0
if tokens >= 1 then
    tokens = tokens - 1
    allowed = 1
end

redis.call('HMSET', KEYS[1], 'tokens', tokens, 'timestamp', now)
redis.call('EXPIRE', KEYS[1], expirySeconds)

return allowed
