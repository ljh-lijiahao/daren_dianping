package com.example.drdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.drdp.dto.Result;
import com.example.drdp.dto.UserDTO;
import com.example.drdp.entity.Blog;
import com.example.drdp.entity.User;
import com.example.drdp.mapper.BlogMapper;
import com.example.drdp.service.IBlogService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.drdp.service.IUserService;
import com.example.drdp.utils.SystemConstants;
import com.example.drdp.utils.UserHolder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static com.example.drdp.utils.RedisConstants.BLOG_LIKED_PREFIX;

@Service
public class BlogServiceImpl extends ServiceImpl<BlogMapper, Blog> implements IBlogService {

    @Resource
    private IUserService userService;
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result saveBlog(Blog blog) {
        // 获取登录用户
        UserDTO user = UserHolder.getUser();
        blog.setUserId(user.getId());
        // 保存探店博文
        save(blog);
        // 返回id
        return Result.ok(blog.getId());
    }

    @Override
    public Result likeBlog(Long id) {
        Long userId = UserHolder.getUser().getId();
        String key = BLOG_LIKED_PREFIX + id;
        Double score = stringRedisTemplate.opsForZSet().score(key, userId.toString());
        if (score == null) {
            if (update().setSql("liked = liked + 1").eq("id", id).update()) {
                stringRedisTemplate.opsForZSet().add(key, userId.toString(), System.currentTimeMillis());
            }
        } else {
            if (update().setSql("liked = liked - 1").eq("id", id).update()) {
                stringRedisTemplate.opsForZSet().remove(key, userId.toString());
            }
        }

        // 修改点赞数量
        return Result.ok();
    }

    @Override
    public Result queryHotBlog(Integer current) {
        // 根据用户查询
        Page<Blog> page = query()
                .orderByDesc("liked")
                .page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
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
     * 查询笔记所对应的用户
     */
    private void queryBlogUser(Blog blog) {
        Long userId = blog.getUserId();
        User user = userService.getById(userId);
        blog.setName(user.getNickName());
        blog.setIcon(user.getIcon());
    }

    /**
     * 查询是否点赞
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
        List<UserDTO> userDTOList = userService.query()
                .in("id", ids).last("order by field(id," + idStr + ")").list()
                .stream().map(user -> BeanUtil.copyProperties(user, UserDTO.class)).collect(Collectors.toList());
        return Result.ok(userDTOList);
    }
}
