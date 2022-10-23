package com.example.drdp.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.drdp.dto.Result;
import com.example.drdp.entity.Shop;
import com.example.drdp.mapper.ShopMapper;
import com.example.drdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.drdp.utils.CacheClient;
import com.example.drdp.utils.SystemConstants;
import org.springframework.data.geo.Distance;
import org.springframework.data.geo.GeoResult;
import org.springframework.data.geo.GeoResults;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.domain.geo.GeoReference;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;

import java.util.*;
import java.util.concurrent.TimeUnit;

import static com.example.drdp.utils.RedisConstants.*;

@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {
    @Resource
    private CacheClient cacheClient;
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result saveShop(Shop shop) {
        // 写入数据库
        save(shop);
        Long shopId = shop.getId();
        // 写入 redis
        cacheClient.setWithLogicalExpire(
                CACHE_SHOP_PREFIX + shopId, shop, CACHE_SHOP_TTL, TimeUnit.MINUTES);
        // 根据商铺类型保存的经纬度到 redis
        stringRedisTemplate.opsForGeo().add(
                SHOP_GEO_PREFIX + shop.getTypeId(), new RedisGeoCommands.GeoLocation<>(
                        shopId.toString(), new Point(shop.getX(), shop.getY())));
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
        // 根据商铺类型保存的经纬度到 redis
        stringRedisTemplate.opsForGeo().add(
                SHOP_GEO_PREFIX + shop.getTypeId(), new RedisGeoCommands.GeoLocation<>(
                        shopId.toString(), new Point(shop.getX(), shop.getY())));
        return Result.ok();
    }

    @Override
    public Result queryShopByType(Integer typeId, Integer current, Double x, Double y) {
        // 是否需要根据经纬度查询
        if (x == null || y == null) {
            // 根据类型分页查询
            Page<Shop> page = lambdaQuery()
                    .eq(Shop::getTypeId, typeId)
                    .page(new Page<>(current, SystemConstants.DEFAULT_PAGE_SIZE));
            // 返回数据
            return Result.ok(page.getRecords());
        }
        int begin = (current - 1) * SystemConstants.DEFAULT_PAGE_SIZE;
        int end = current * SystemConstants.DEFAULT_PAGE_SIZE;
        GeoResults<RedisGeoCommands.GeoLocation<String>> search = stringRedisTemplate.opsForGeo().search(
                SHOP_GEO_PREFIX + typeId,
                GeoReference.fromCoordinate(x, y),
                new Distance(5000),
                RedisGeoCommands.GeoSearchCommandArgs.newGeoSearchArgs().includeDistance().limit(end)
        );
        if (search == null || search.getContent().isEmpty()) {
            return Result.ok(Collections.emptyList());
        }
        List<GeoResult<RedisGeoCommands.GeoLocation<String>>> geoResultList = search.getContent();
        if (begin > geoResultList.size()) {
            return Result.ok(Collections.emptyList());
        }
        // 截取 begin 到 end 部分
        List<Long> ids = new ArrayList<>(geoResultList.size());
        Map<Long, Distance> distanceMap = new HashMap<>(geoResultList.size());
        geoResultList.stream().skip(begin).forEach(geoLocationGeoResult -> {
            Long shopId = Long.valueOf(geoLocationGeoResult.getContent().getName());
            Distance distance = geoLocationGeoResult.getDistance();
            ids.add(shopId);
            distanceMap.put(shopId, distance);
        });
        String strIds = StrUtil.join(",", ids);
        List<Shop> shopList = lambdaQuery()
                .in(Shop::getId, ids).last("order by field(id," + strIds + ")").list();
        shopList.forEach(shop -> shop.setDistance(distanceMap.get(shop.getId()).getValue()));
        return Result.ok(shopList);
    }
}
