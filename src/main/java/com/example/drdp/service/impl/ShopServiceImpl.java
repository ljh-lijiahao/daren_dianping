package com.example.drdp.service.impl;

import com.example.drdp.dto.Result;
import com.example.drdp.entity.Shop;
import com.example.drdp.mapper.ShopMapper;
import com.example.drdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.drdp.utils.CacheClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;

import java.util.concurrent.TimeUnit;

import static com.example.drdp.utils.RedisConstants.*;

@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {
    @Resource
    private CacheClient cacheClient;

    @Override
    public Result saveShop(Shop shop) {
        // 写入数据库
        save(shop);
        Long shopId = shop.getId();
        cacheClient.setWithLogicalExpire(
                CACHE_SHOP_PREFIX + shopId, shop, CACHE_SHOP_TTL, TimeUnit.MINUTES);
        // 返回店铺id
        return Result.ok(shopId);
    }

    /**
     * 根据id查询商铺信息
     */
    @Override
    public Result queryById(Long id) {
        // 缓存击穿
/*
        Shop shop = cacheClient.queryWithPassThrough(
                CACHE_SHOP_PREFIX, id, Shop.class, this::getById, CACHE_SHOP_TTL, TimeUnit.MINUTES);
*/
        // 互斥锁解决缓存击穿
/*
        Shop shop = cacheClient.queryWithMutex(
                CACHE_SHOP_PREFIX, id, Shop.class, this::getById, CACHE_SHOP_TTL, TimeUnit.MINUTES);
*/
        // 逻辑过期解决缓存击穿
        Shop shop = cacheClient.queryWithLogicalExpire(
                CACHE_SHOP_PREFIX, id, Shop.class, this::getById, CACHE_SHOP_TTL, TimeUnit.MINUTES);
        if (shop == null) {
            return Result.fail("商铺不存在，请稍后重试");
        }
        return Result.ok(shop);
    }

    /**
     * 更新商铺信息
     */
    @Override
    @Transactional
    public Result update(Shop shop) {
        Long shopId = shop.getId();
        if (shopId == null) {
            return Result.fail("商铺id不存在，不能修改");
        }
        updateById(shop);
        cacheClient.setWithLogicalExpire(
                CACHE_SHOP_PREFIX + shopId, shop, CACHE_SHOP_TTL, TimeUnit.MINUTES);
        return Result.ok();
    }
}
