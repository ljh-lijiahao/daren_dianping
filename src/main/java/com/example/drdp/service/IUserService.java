package com.example.drdp.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.example.drdp.dto.LoginFormDTO;
import com.example.drdp.dto.Result;
import com.example.drdp.entity.User;


public interface IUserService extends IService<User> {

    Result sendCode(String phone);

    Result login(LoginFormDTO loginForm);
}
