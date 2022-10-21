package com.example.drdp.service;

import com.example.drdp.dto.Result;
import com.example.drdp.entity.Shop;
import com.baomidou.mybatisplus.extension.service.IService;

public interface IShopService extends IService<Shop> {

    Result queryById(Long id);

    Result update(Shop shop);

    Result saveShop(Shop shop);
}
