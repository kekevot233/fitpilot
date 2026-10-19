local outcome = ARGV[1]
local maximum = tonumber(ARGV[2])
local window = tonumber(ARGV[3])
redis.call('ZREM', KEYS[2], ARGV[4])
if outcome == 'success' then
    redis.call('DEL', KEYS[1])
elseif outcome == 'failure' then
    local failures = tonumber(redis.call('GET', KEYS[1]) or '0')
    if failures < maximum then
        failures = redis.call('INCR', KEYS[1])
        if failures == 1 then redis.call('EXPIRE', KEYS[1], window) end
    end
    if failures >= maximum then
        return math.max(1, redis.call('TTL', KEYS[1]))
    end
end
return 0
