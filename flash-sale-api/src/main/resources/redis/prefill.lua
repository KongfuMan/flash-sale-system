-- KEYS[1]: flashsale:inventory:v1:{event:<eventId>} (a Redis hash).
-- ARGV[1]: Redis key TTL in seconds, ending after the event plus its retention buffer.
-- ARGV[2..n]: alternating hash fields and values:
--   status                -> event status, e.g. "RUNNING"
--   prod:<eventProductId> -> available stock, e.g. "100"
if redis.call('EXISTS', KEYS[1]) ~= 0 then return 0 end
redis.call('HSET', KEYS[1], unpack(ARGV, 2))
redis.call('EXPIRE', KEYS[1], ARGV[1])
return 1
