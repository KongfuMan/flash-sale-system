-- Redis key (KEYS[1]): flashsale:inventory:v1:{event:<eventId>}.
-- Its value is a Redis hash containing these field -> value pairs:
--   status              -> event status string, e.g. "RUNNING".
--   prod:<eventProductId> -> available stock string, e.g. prod:456 -> "100".
--   idem:<idempotencyKey> -> JSON string for an accepted idempotency key:
--     {version: 3, status: "PENDING", idempotencyKey: <raw key>,
--      requestId: <SHA-256 of eventId + ":" + raw key>,
--      eventId: <string>, eventProductId: <string>, userId: <string>}
-- Java serializes InventoryMarker and passes the JSON string to the script.
-- On retry, Lua decodes it to verify the key and request binding before
-- returning REPLAY, LEGACY_REPLAY, or CONFLICT without decrementing stock again.
-- Versions 1 and 2 predate the outbox and require manual reconciliation on replay.
-- Version 1 also requires consistent redundant binding and message fields.
-- Markers have no per-field TTL.
-- outbox:<requestId> contains the full serialized Kafka envelope.
-- ARGV: eventId, eventProductId, userId, idempotencyKey, requestId, marker JSON, envelope JSON.
local keyType = redis.call('TYPE', KEYS[1]).ok
if keyType == 'none' then return 'MISSING_EVENT' end
if keyType ~= 'hash' then return 'INVALID_STATE' end

local eventId, productId, userId, idempotencyKey, requestId, encoded, payload = unpack(ARGV)
local marker = redis.call('HGET', KEYS[1], 'idem:' .. idempotencyKey)
if marker then
    local ok, record = pcall(cjson.decode, marker)
    if not ok or type(record) ~= 'table' or (record.version ~= 1 and record.version ~= 2 and record.version ~= 3)
        or record.status ~= 'PENDING'
        or type(record.eventProductId) ~= 'string' or type(record.userId) ~= 'string'
        or record.eventId ~= eventId
        or record.requestId ~= requestId or record.idempotencyKey ~= idempotencyKey then return 'INVALID_STATE' end
    if record.version == 1 and (type(record.binding) ~= 'table' or type(record.message) ~= 'table'
        or record.binding.eventId ~= record.eventId or record.binding.eventProductId ~= record.eventProductId
        or record.binding.userId ~= record.userId or record.message.eventId ~= record.eventId
        or record.message.eventProductId ~= record.eventProductId or record.message.userId ~= record.userId
        or record.message.requestId ~= record.requestId) then return 'INVALID_STATE' end
    if record.eventProductId ~= productId or record.userId ~= userId then return 'CONFLICT' end
    if record.version ~= 3 then return 'LEGACY_REPLAY' end
    return 'REPLAY'
end
if redis.call('HEXISTS', KEYS[1], 'outbox:' .. requestId) == 1 then return 'INVALID_STATE' end

local status = redis.call('HGET', KEYS[1], 'status')
if status ~= 'RUNNING' and status ~= 'UPCOMING' and status ~= 'ENDED' and status ~= 'CANCELED' then return 'INVALID_STATE' end
if status ~= 'RUNNING' then return 'EVENT_NOT_RUNNING' end
local stock = redis.call('HGET', KEYS[1], 'prod:' .. productId)
if not stock then return 'MISSING_PRODUCT' end
if not string.match(stock, '^%d+$') or #stock > 10 or
   (#stock > 1 and string.sub(stock, 1, 1) == '0') then return 'INVALID_STATE' end
local n = tonumber(stock)
if n > 2147483647 then return 'INVALID_STATE' end
if n == 0 then return 'OUT_OF_STOCK' end

local ok, record = pcall(cjson.decode, encoded)
if not ok or type(record) ~= 'table' or record.version ~= 3 or record.status ~= 'PENDING'
    or record.idempotencyKey ~= idempotencyKey or record.requestId ~= requestId
    or record.eventId ~= eventId or record.eventProductId ~= productId or record.userId ~= userId then
    return 'INVALID_STATE'
end
if not payload or payload == '' then return 'INVALID_STATE' end
-- Redis scripts do not roll back on error: the only mutation must be one command.
redis.call('HSET', KEYS[1], 'prod:' .. productId, tostring(n - 1), 'idem:' .. idempotencyKey, encoded,
    'outbox:' .. requestId, payload)
return 'RESERVED'
