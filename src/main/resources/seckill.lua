-- 优惠券 id
local voucherId = ARGV[1]
-- 用户 id
local userId = ARGV[2]
-- 订单 id
local orderId = ARGV[3]

-- 消息队列名
local queueName = ARGV[4]

-- 库存 key
local stockKey = 'seckill:stock:' .. voucherId
-- 订单 key
local orderKey = 'seckill:order:' .. voucherId


-- 判断库存是否充足
if (redis.call('exists', stockKey) == 1 and tonumber(redis.call('get', stockKey)) <= 0) then
    return 0
end
-- 判断用户是否重复下单
if (redis.call('sismember', orderKey, userId) == 1) then
    return -1
end
-- 库存 -1
redis.call('incrby', stockKey, -1)
-- 下单
redis.call('sadd', orderKey, userId)

-- 添加到消息队列中
redis.call('xadd', queueName, '*', 'userId', userId, 'voucherId', voucherId, 'orderId', orderId)

return 1