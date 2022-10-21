package com.example.drdp.service.impl;

import cn.hutool.json.JSONUtil;
import com.example.drdp.dto.Result;
import com.example.drdp.entity.ShopType;
import com.example.drdp.mapper.ShopTypeMapper;
import com.example.drdp.service.IShopTypeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static com.example.drdp.utils.RedisConstants.SHOP_TYPE_NAME;
import static com.example.drdp.utils.RedisConstants.SHOP_TYPE_TTL;

@Service
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 查询所有商铺类型
     */
    @Override
    public Result queryAllType() {
        // 查询 redis 中商铺类型信息
        String typeListJSON = stringRedisTemplate.opsForValue().get(SHOP_TYPE_NAME);
        if (typeListJSON != null) {
            return Result.ok(JSONUtil.toList(typeListJSON, ShopType.class));
        }
        List<ShopType> typeList = query().orderByAsc("sort").list();
        if (typeList == null) {
            return Result.fail("商铺类型查询错误，请稍后重试");
        }
        // 保存商铺类型信息到 redis 中
        stringRedisTemplate.opsForValue().set(SHOP_TYPE_NAME, JSONUtil.toJsonStr(typeList), SHOP_TYPE_TTL, TimeUnit.MINUTES);
        return Result.ok(typeList);
    }
}
