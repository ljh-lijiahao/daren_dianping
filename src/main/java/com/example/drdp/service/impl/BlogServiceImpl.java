package com.example.drdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.drdp.dto.Result;
import com.example.drdp.dto.ScrollResult;
import com.example.drdp.dto.UserDTO;
import com.example.drdp.entity.Blog;
import com.example.drdp.entity.Follow;
import com.example.drdp.entity.User;
import com.example.drdp.mapper.BlogMapper;
import com.example.drdp.service.IBlogService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.drdp.service.IFollowService;
import com.example.drdp.service.IUserService;
import com.example.drdp.utils.SystemConstants;
import com.example.drdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static com.example.drdp.utils.RedisConstants.*;

@Slf4j
@Service
public class BlogServiceImpl extends ServiceImpl<BlogMapper, Blog> implements IBlogService {

    @Resource
    private IUserService userService;
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private IFollowService followService;

    @Override
    public Result saveBlog(Blog blog) {
        // 获取登录用户
        UserDTO user = UserHolder.getUser();
        blog.setUserId(user.getId());
        // 保存探店笔记
        if (!save(blog)) {
            return Result.fail("发布笔记失败");
        }
        List<Follow> followList = followService.lambdaQuery().eq(Follow::getFollowUserId, user.getId()).list();
        followList.forEach(follow -> stringRedisTemplate.opsForZSet().add(
                FEED_PREFIX + follow.getUserId(), blog.getId().toString(), System.currentTimeMillis()));
        // 返回id
        return Result.ok(blog.getId());
    }

    @Override
    public Result likeBlog(Long id) {
        Long userId = UserHolder.getUser().getId();
        String key = BLOG_LIKED_PREFIX + id;
        Double score = stringRedisTemplate.opsForZSet().score(key, userId.toString());
        if (score == null) {
            if (lambdaUpdate().setSql("liked = liked + 1").eq(Blog::getId, id).update()) {
                stringRedisTemplate.opsForZSet().add(key, userId.toString(), System.currentTimeMillis());
            }
        } else {
            if (lambdaUpdate().setSql("liked = liked - 1").eq(Blog::getId, id).update()) {
                stringRedisTemplate.opsForZSet().remove(key, userId.toString());
            }
        }

        // 修改点赞数量
        return Result.ok();
    }

    @Override
    public Result queryMyBlog(Integer current) {
        // 获取登录用户
        UserDTO user = UserHolder.getUser();
        // 根据用户查询
        Page<Blog> page = lambdaQuery()
                .eq(Blog::getUserId, user.getId()).page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        // 获取当前页数据
        List<Blog> records = page.getRecords();
        return Result.ok(records);
    }

    @Override
    public Result queryHotBlog(Integer current) {
        // 根据用户查询
        Page<Blog> page = lambdaQuery()
                .orderByDesc(Blog::getLiked).page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        // 获取当前页数据
        List<Blog> records = page.getRecords();
        // 查询用户
        records.forEach(blog -> {
            queryBlogUser(blog);
            isBlogLiked(blog);
        });
        return Result.ok(records);
    }

    /**
     * 查询笔记相关的用户
     */
    private void queryBlogUser(Blog blog) {
        Long userId = blog.getUserId();
        User user = userService.getById(userId);
        blog.setName(user.getNickName());
        blog.setIcon(user.getIcon());
    }

    /**
     * 查询笔记是否被点赞
     */
    private void isBlogLiked(Blog blog) {
        UserDTO user = UserHolder.getUser();
        if (user == null) {
            // 用户未登录，无需查询是否点赞
            return;
        }
        Long userId = user.getId();
        String key = BLOG_LIKED_PREFIX + blog.getId();
        blog.setIsLike(stringRedisTemplate.opsForZSet().score(key, userId.toString()) != null);
    }

    @Override
    public Result queryBlogById(Long id) {
        Blog blog = getById(id);
        if (blog == null) {
            return Result.fail("笔记已不存在！");
        }
        queryBlogUser(blog);
        isBlogLiked(blog);
        return Result.ok(blog);
    }

    @Override
    public Result queryBlogLikes(Long id) {
        // 点赞排行榜前五名
        Set<String> top = stringRedisTemplate.opsForZSet().range(BLOG_LIKED_PREFIX + id, 0, 4);
        if (top == null || top.isEmpty()) {
            return Result.ok(Collections.emptyList());
        }
        List<Long> ids = top.stream().map(Long::valueOf).collect(Collectors.toList());
        String idStr = StrUtil.join(",", ids);
        List<UserDTO> userDTOList = userService.lambdaQuery()
                .in(User::getId, ids).last("order by field(id," + idStr + ")").list()
                .stream().map(user -> BeanUtil.copyProperties(user, UserDTO.class)).collect(Collectors.toList());
        return Result.ok(userDTOList);
    }

    @Override
    public Result queryBlogByUserId(Integer current, Long id) {
        // 根据用户查询
        Page<Blog> page = lambdaQuery()
                .eq(Blog::getUserId, id).page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        // 获取当前页数据
        List<Blog> records = page.getRecords();
        return Result.ok(records);
    }

    @Override
    public Result queryBlogOfFollow(Long max, Integer offset) {
        Long userId = UserHolder.getUser().getId();
        Set<ZSetOperations.TypedTuple<String>> typedTuples = stringRedisTemplate.opsForZSet()
                .reverseRangeByScoreWithScores(FEED_PREFIX + userId, 0, max, offset, 5);
        if (typedTuples == null || typedTuples.isEmpty()) {
            return Result.ok();
        }
        List<Long> ids = new ArrayList<>(typedTuples.size());
        long minTime = 0;
        int num = 1;
        for (ZSetOperations.TypedTuple<String> typedTuple : typedTuples) {
            ids.add(Long.valueOf(typedTuple.getValue()));
            long time = typedTuple.getScore().longValue();
            if (time == minTime) {
                num++;
            } else {
                minTime = time;
                num = 1;
            }
        }
        String idStr = StrUtil.join(",", ids);
        List<Blog> blogList = lambdaQuery()
                .in(Blog::getId, ids).last("order by field(id," + idStr + ")").list()
                .stream().peek(blog -> {
                    queryBlogUser(blog);
                    isBlogLiked(blog);
                }).collect(Collectors.toList());
        ScrollResult result = new ScrollResult();
        result.setList(blogList);
        result.setOffset(num);
        result.setMinTime(minTime);
        return Result.ok(result);
    }
}
