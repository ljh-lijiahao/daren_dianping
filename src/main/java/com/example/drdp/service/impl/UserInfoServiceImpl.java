package com.example.drdp.service.impl;

import com.example.drdp.dto.Result;
import com.example.drdp.entity.UserInfo;
import com.example.drdp.mapper.UserInfoMapper;
import com.example.drdp.service.IUserInfoService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

@Service
public class UserInfoServiceImpl extends ServiceImpl<UserInfoMapper, UserInfo> implements IUserInfoService {

    @Override
    public Result queryUserInfo(Long userId) {
        // 查询详情
        UserInfo info = getById(userId);
        if (info == null) {
            // 没有详情，应该是第一次查看详情
            return Result.ok();
        }
        info.setCreateTime(null);
        info.setUpdateTime(null);
        // 返回
        return Result.ok(info);
    }
}
