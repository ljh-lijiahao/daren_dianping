package com.example.drdp.controller;


import com.example.drdp.dto.Result;
import com.example.drdp.service.IFollowService;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;

/**
 * 关注控制器
 */
@RestController
@RequestMapping("/follow")
public class FollowController {
    @Resource
    private IFollowService followService;

    /**
     * 关注或取关
     *
     * @param followUserId 关注目标用户 id
     * @param isFollow     关注 true  取关 false
     */
    @PutMapping("/{id}/{isFollow}")
    public Result follow(@PathVariable("id") Long followUserId, @PathVariable("isFollow") Boolean isFollow) {
        return followService.follow(followUserId, isFollow);
    }

    /**
     * 查询是否关注
     *
     * @param followUserId 关注目标用户 id
     */
    @GetMapping("/or/not/{id}")
    public Result isFollow(@PathVariable("id") Long followUserId) {
        return followService.isFollow(followUserId);
    }

    /**
     * 查询共同关注
     *
     * @param id 目标用户 id
     */
    @GetMapping("/common/{id}")
    public Result followCommon(@PathVariable("id") Long id) {
        return followService.followCommon(id);
    }

}
