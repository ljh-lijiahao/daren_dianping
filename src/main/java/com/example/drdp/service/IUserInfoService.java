package com.example.drdp.service;

import com.example.drdp.dto.Result;
import com.example.drdp.entity.UserInfo;
import com.baomidou.mybatisplus.extension.service.IService;

public interface IUserInfoService extends IService<UserInfo> {

    Result queryUserInfo(Long userId);
}
