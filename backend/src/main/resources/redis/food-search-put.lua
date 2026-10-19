-- A bounded shared hash: new searches never extend the current cache window.
if redis.call('HEXISTS', KEYS[1], ARGV[1]) == 0
    and redis.call('HLEN', KEYS[1]) >= tonumber(ARGV[4]) then
    return 0
end
redis.call('HSET', KEYS[1], ARGV[1], ARGV[2])
if redis.call('TTL', KEYS[1]) < 0 then
    redis.call('EXPIRE', KEYS[1], tonumber(ARGV[3]))
end
return 1
