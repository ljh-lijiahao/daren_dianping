package com.example.drdp.service;

import com.example.drdp.dto.Result;
import com.example.drdp.entity.Follow;
import com.baomidou.mybatisplus.extension.service.IService;

public interface IFollowService extends IService<Follow> {

    Result follow(Long followUserId, Boolean isFollow);

    Result isFollow(Long followUserId);

    Result followCommon(Long id);
}
