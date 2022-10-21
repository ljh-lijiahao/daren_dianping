package com.example.drdp.controller;


import com.example.drdp.dto.Result;
import com.example.drdp.entity.ShopType;
import com.example.drdp.service.IShopTypeService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.util.List;

/**
 * 商铺类型控制器
 */
@RestController
@RequestMapping("/shop-type")
public class ShopTypeController {
    @Resource
    private IShopTypeService typeService;

    /**
     * 查询所有商铺类型
     */
    @GetMapping("list")
    public Result queryTypeList() {
        return typeService.queryAllType();
    }
}
