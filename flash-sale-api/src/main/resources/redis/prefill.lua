-- KEYS[1]: event inventory hash key.
-- ARGV: alternating field/value pairs (status, then p:<event_product_id> stock).
if redis.call('EXISTS', KEYS[1]) ~= 0 then return 0 end
redis.call('HSET', KEYS[1], unpack(ARGV))
return 1
