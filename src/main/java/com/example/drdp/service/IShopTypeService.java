package com.example.drdp.service;

import com.example.drdp.dto.Result;
import com.example.drdp.entity.ShopType;
import com.baomidou.mybatisplus.extension.service.IService;

public interface IShopTypeService extends IService<ShopType> {

    Result queryAllType();
}
