package com.example.drdp.controller;


import com.example.drdp.dto.LoginFormDTO;
import com.example.drdp.dto.Result;
import com.example.drdp.service.IUserInfoService;
import com.example.drdp.service.IUserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;

/**
 * 用户控制器
 */
@Slf4j
@RestController
@RequestMapping("/user")
public class UserController {

    @Resource
    private IUserService userService;

    @Resource
    private IUserInfoService userInfoService;

    /**
     * 发送手机验证码
     *
     * @param phone 手机号
     */
    @PostMapping("/code")
    public Result sendCode(@RequestParam("phone") String phone) {
        // 发送短信验证码并保存验证码
        return userService.sendCode(phone);
    }

    /**
     * 登录功能
     *
     * @param loginForm 登录参数，包含手机号、验证码；或者手机号、密码
     */
    @PostMapping("/login")
    public Result login(@RequestBody LoginFormDTO loginForm) {
        // 实现登录功能
        return userService.login(loginForm);
    }

    /**
     * 登出功能
     **/
    @PostMapping("/logout")
    public Result logout(HttpServletRequest request) {
        return userService.logout(request);
    }

    /**
     * 当前登录用户信息
     */
    @GetMapping("/me")
    public Result me() {
        return userService.getMe();
    }

    /**
     * 根据 id 查询详情
     *
     * @param userId 用户 id
     */
    @GetMapping("/info/{id}")
    public Result info(@PathVariable("id") Long userId) {
        return userInfoService.queryUserInfo(userId);
    }

    /**
     * 根据用户 id 查询基本信息
     *
     * @param userId 用户 id
     */
    @GetMapping("/{id}")
    public Result queryUserById(@PathVariable("id") Long userId) {
        return userService.queryUserById(userId);
    }

    /**
     * 签到
     */
    @PostMapping("/sign")
    public Result sign() {
        return userService.sign();
    }

    /**
     * 统计本月截至今天连续签到天数
     */
    @GetMapping("/sign/count")
    public Result signCount() {
        return userService.signCount();
    }
}
