package com.example.drdp.service.impl;

import com.example.drdp.entity.UserInfo;
import com.example.drdp.mapper.UserInfoMapper;
import com.example.drdp.service.IUserInfoService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

@Service
public class UserInfoServiceImpl extends ServiceImpl<UserInfoMapper, UserInfo> implements IUserInfoService {

}
