package com.example.drdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.crypto.digest.DigestUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.drdp.dto.LoginFormDTO;
import com.example.drdp.dto.Result;
import com.example.drdp.dto.UserDTO;
import com.example.drdp.entity.User;
import com.example.drdp.mapper.UserMapper;
import com.example.drdp.service.IUserService;
import com.example.drdp.utils.RegexUtils;
import com.example.drdp.utils.SystemConstants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.example.drdp.utils.RedisConstants.*;

@Slf4j
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 发送短信验证码
     */
    @Override
    public Result sendCode(String phone) {
        // 手机号格式验证
        if (RegexUtils.isPhoneInvalid(phone)) {
            return Result.fail("请输入正确的手机号");
        }
        // 符合，生成验证码
        String code = RandomUtil.randomNumbers(6);
        // 保存验证码到 redis 中
        stringRedisTemplate.opsForValue().set(LOGIN_CODE_PREFIX + phone, code, LOGIN_CODE_TTL, TimeUnit.MINUTES);
        // 发送验证码
        log.info("验证码:{}", code);
        return Result.ok();
    }

    @Override
    public Result login(LoginFormDTO loginForm) {
        // 手机号格式验证
        if (RegexUtils.isPhoneInvalid(loginForm.getPhone())) {
            return Result.fail("请输入正确的手机号");
        }
        // 判断是短信登录或是密码登录
        if (loginForm.getCode() != null) {
            return codeLogin(loginForm);
        } else if (loginForm.getPassword() != null) {
            return passwordLogin(loginForm);
        } else
            return Result.fail("登录页面异常，请刷新页面");
    }

    /**
     * 密码登陆
     */
    private Result passwordLogin(LoginFormDTO loginForm) {
        // 根据手机号查询用户
        String phone = loginForm.getPhone();
        LambdaQueryWrapper<User> lambdaQueryWrapper = new LambdaQueryWrapper<>();
        lambdaQueryWrapper.eq(User::getPhone, phone);
        User user = getOne(lambdaQueryWrapper);
        // 判断用户是否存在
        if (user == null) {
            return Result.fail("手机号未注册，请使用短信登陆");
        }
        // md5加密
        String password = DigestUtil.md5Hex(loginForm.getPassword().getBytes());
        if (user.getPassword() == null || !user.getPassword().equals(password)) {
            return Result.fail("用户名或密码错误");
        }
        String token = saveUserInRedis(user);
        return Result.ok(token);
    }

    /**
     * 登录成功，保存用户信息保存到 redis 中
     */
    private String saveUserInRedis(User user) {
        String token = UUID.randomUUID().toString(true);
        Map<String, Object> userMap = BeanUtil.beanToMap(BeanUtil.copyProperties(user, UserDTO.class),
                new HashMap<>(), CopyOptions.create().setIgnoreNullValue(true)
                        .setFieldValueEditor((fieldName, fieldValue) -> fieldValue.toString()));
        String key = LOGIN_USER_PREFIX + token;
        stringRedisTemplate.opsForHash().putAll(key, userMap);
        stringRedisTemplate.expire(key, LOGIN_USER_TTL, TimeUnit.MINUTES);
        stringRedisTemplate.delete(LOGIN_CODE_PREFIX + user.getPhone());
        return token;
    }

    /**
     * 短信登陆
     */
    private Result codeLogin(LoginFormDTO loginForm) {
        // 校验验证码
        String phone = loginForm.getPhone();
        String cacheCode = stringRedisTemplate.opsForValue().get(LOGIN_CODE_PREFIX + phone);
        if (cacheCode == null || !cacheCode.equals(loginForm.getCode())) {
            return Result.fail("验证码错误");
        }
        // 根据手机号查询用户
        LambdaQueryWrapper<User> lambdaQueryWrapper = new LambdaQueryWrapper<>();
        lambdaQueryWrapper.eq(User::getPhone, phone);
        User user = getOne(lambdaQueryWrapper);
        // 判断用户是否存在
        if (user == null) {
            user = createUserWithPhone(phone);
        }
        String token = saveUserInRedis(user);
        return Result.ok(token);
    }

    /**
     * 创建用户
     */
    private User createUserWithPhone(String phone) {
        User user = new User();
        user.setPhone(phone);
        user.setNickName(SystemConstants.USER_NICK_NAME_PREFIX + RandomUtil.randomString(10));
        save(user);
        return user;
    }

    @Override
    public Result queryUserById(Long userId) {
        // 查询详情
        User user = getById(userId);
        if (user == null) {
            return Result.ok();
        }
        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
        // 返回
        return Result.ok(userDTO);
    }
}
