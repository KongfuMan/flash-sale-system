-- Redis key (KEYS[1]): flashsale:inventory:v1:{event:<eventId>}.
-- Its value is a Redis hash containing these field -> value pairs:
--   status              -> event status string, e.g. "RUNNING".
--   prod:<eventProductId> -> available stock string, e.g. prod:456 -> "100".
--   idem:<idempotencyKey> -> JSON string for an accepted idempotency key:
--     {version: 1, status: "PENDING", idempotencyKey: <raw key>,
--      requestId: <SHA-256 of eventId + ":" + raw key>,
--      eventId: <string>, eventProductId: <string>, userId: <string>,
--      binding: {eventId, eventProductId, userId},
--      message: {requestId, eventId, eventProductId, userId}}
-- cjson.encode converts this record to the JSON string stored in the hash.
-- On retry, Lua decodes it to verify the key and request binding before
-- returning REPLAY or CONFLICT without decrementing stock again. PENDING and
-- message retain request details; they do not publish a message or create an order.
-- IDs in binding and message are strings too. Markers have no per-field TTL.
-- ARGV: eventId, eventProductId, userId, idempotencyKey, requestId.
local keyType = redis.call('TYPE', KEYS[1]).ok
if keyType == 'none' then return 'MISSING_EVENT' end
if keyType ~= 'hash' then return 'INVALID_STATE' end

local eventId, productId, userId, idempotencyKey, requestId = unpack(ARGV)
local marker = redis.call('HGET', KEYS[1], 'idem:' .. idempotencyKey)
if marker then
    local ok, record = pcall(cjson.decode, marker)
    if not ok or type(record) ~= 'table' or record.version ~= 1 or record.status ~= 'PENDING'
        or type(record.binding) ~= 'table' or type(record.message) ~= 'table'
        or type(record.eventProductId) ~= 'string' or type(record.userId) ~= 'string'
        or record.eventId ~= eventId
        or record.requestId ~= requestId or record.idempotencyKey ~= idempotencyKey
        or record.binding.eventId ~= record.eventId or record.binding.eventProductId ~= record.eventProductId
        or record.binding.userId ~= record.userId or record.message.eventId ~= record.eventId
        or record.message.eventProductId ~= record.eventProductId or record.message.userId ~= record.userId
        or record.message.requestId ~= record.requestId then return 'INVALID_STATE' end
    if record.eventProductId ~= productId or record.userId ~= userId then return 'CONFLICT' end
    return 'REPLAY'
end

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

local record = {
    version = 1, status = 'PENDING', idempotencyKey = idempotencyKey, requestId = requestId,
    eventId = eventId, eventProductId = productId, userId = userId,
    binding = {eventId = eventId, eventProductId = productId, userId = userId},
    message = {requestId = requestId, eventId = eventId, eventProductId = productId, userId = userId}
}
local ok, encoded = pcall(cjson.encode, record)
if not ok then return 'INVALID_STATE' end
-- Redis scripts do not roll back on error: the only mutation must be one command.
redis.call('HSET', KEYS[1], 'prod:' .. productId, tostring(n - 1), 'idem:' .. idempotencyKey, encoded)
return 'RESERVED'
