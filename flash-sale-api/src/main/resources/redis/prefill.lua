-- KEYS[1]: flashsale:inventory:v1:{event:<eventId>} (a Redis hash).
-- ARGV: alternating hash fields and values:
--   status                -> event status, e.g. "RUNNING"
--   prod:<eventProductId> -> available stock, e.g. "100"
-- if redis.call('EXISTS', KEYS[1]) ~= 0 then return 0 end
redis.call('DEL', KEYS[1])
redis.call('HSET', KEYS[1], unpack(ARGV))
return 1
