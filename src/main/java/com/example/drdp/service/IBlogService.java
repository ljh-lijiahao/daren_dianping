package com.example.drdp.service;

import com.example.drdp.dto.Result;
import com.example.drdp.entity.Blog;
import com.baomidou.mybatisplus.extension.service.IService;

public interface IBlogService extends IService<Blog> {

    Result saveBlog(Blog blog);

    Result likeBlog(Long id);

    Result queryHotBlog(Integer current);

    Result queryBlogById(Long id);

    Result queryBlogLikes(Long id);
}
