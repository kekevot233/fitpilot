local maximum = tonumber(ARGV[1])
local lease = tonumber(ARGV[2])
local time = redis.call('TIME')
local now = tonumber(time[1]) * 1000 + math.floor(tonumber(time[2]) / 1000)
redis.call('ZREMRANGEBYSCORE', KEYS[2], '-inf', now)
local failures = tonumber(redis.call('GET', KEYS[1]) or '0')
if failures >= maximum then
    return math.max(1, redis.call('TTL', KEYS[1]))
end
-- Reserve capacity before password verification, shared across application instances.
if failures + redis.call('ZCARD', KEYS[2]) >= maximum then
    return 1
end
redis.call('ZADD', KEYS[2], now + lease * 1000, ARGV[3])
redis.call('EXPIRE', KEYS[2], lease + 1)
return 0
