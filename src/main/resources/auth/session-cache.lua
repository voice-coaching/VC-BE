-- KEYS share the {sessions} hash tag. All invalidation/fill decisions are atomic.
local meta, state = KEYS[1], KEYS[2]
local action, owner = ARGV[1], ARGV[2]
local server = redis.call('INFO', 'server')
local runId = string.match(server, 'run_id:([a-zA-Z0-9]+)')
if not runId then return {'BYPASS'} end
local evicted = string.match(redis.call('INFO', 'stats'), 'evicted_keys:([0-9]+)')
if action == 'BOOT_BEGIN' then
    redis.call('HSET', meta, 'gate', owner)
    redis.call('PERSIST', meta)
    return {'BOOT', runId}
end
if action == 'BOOT_END' then
    if runId ~= ARGV[3] or redis.call('HGET', meta, 'gate') ~= owner then return {'BYPASS'} end
    redis.call('HSET', meta, 'runId', runId, 'epoch', owner, 'evicted', evicted or '')
    redis.call('HDEL', meta, 'gate')
    return {'OK'}
end
if action == 'BEGIN' then
    local generation = redis.call('HGET', meta, 'gate') or redis.call('HGET', meta, 'epoch') or 'uninitialized'
    redis.call('HDEL', state, 'value', 'loader')
    redis.call('HSET', state, 'w:' .. owner, generation)
    redis.call('PERSIST', state)
    return {'OK'}
end
if action == 'END' then
    -- Invalidate even if an earlier Redis restart lost this owner's marker.
    redis.call('HDEL', state, 'value', 'loader', 'w:' .. owner)
    local fields = redis.call('HKEYS', state)
    for _, field in ipairs(fields) do
        if string.sub(field, 1, 2) == 'w:' then return {'OK'} end
    end
    redis.call('DEL', state)
    return {'OK'}
end
if redis.call('HGET', meta, 'runId') ~= runId then return {'BOOTSTRAP'} end
if redis.call('HGET', meta, 'evicted') ~= evicted then return {'BOOTSTRAP'} end
if redis.call('HEXISTS', meta, 'gate') == 1 then return {'BYPASS'} end
local epoch = redis.call('HGET', meta, 'epoch')
if not epoch then return {'BOOTSTRAP'} end
local fields = redis.call('HGETALL', state)
local pending = false
for i = 1, #fields, 2 do
    if string.sub(fields[i], 1, 2) == 'w:' then
        if fields[i + 1] == epoch then pending = true
        else redis.call('HDEL', state, fields[i]) end
    end
end
if pending then return {'BYPASS'} end
if action == 'PUBLISH' then
    if redis.call('HGET', state, 'epoch') ~= epoch or redis.call('HGET', state, 'loader') ~= owner then return {'BYPASS'} end
    redis.call('HDEL', state, 'loader')
    redis.call('HSET', state, 'value', ARGV[3])
    redis.call('PEXPIRE', state, ARGV[4])
    return {'OK'}
end
if action ~= 'LOOKUP' then return {'BYPASS'} end
if redis.call('HGET', state, 'epoch') ~= epoch then
    redis.call('HDEL', state, 'value', 'loader')
    redis.call('HSET', state, 'epoch', epoch)
end
local value = redis.call('HGET', state, 'value')
if value then return {'CACHED', value} end
if redis.call('HEXISTS', state, 'loader') == 1 then return {'BYPASS'} end
redis.call('HSET', state, 'loader', owner)
redis.call('PEXPIRE', state, ARGV[4])
return {'LOAD', owner}
