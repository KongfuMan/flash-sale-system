if redis.call('EXISTS', KEYS[1]) == 0 then return -1 end
if redis.call('TYPE', KEYS[1]).ok ~= 'hash' then return -2 end
local status = redis.call('HGET', KEYS[1], 'status')
if status ~= 'UPCOMING' and status ~= 'RUNNING' then return -2 end
redis.call('HSET', KEYS[1], 'status', 'RUNNING')
return 1
