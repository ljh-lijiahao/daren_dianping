package com.example.drdp.service.impl;

import com.example.drdp.entity.BlogComments;
import com.example.drdp.mapper.BlogCommentsMapper;
import com.example.drdp.service.IBlogCommentsService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

@Service
public class BlogCommentsServiceImpl extends ServiceImpl<BlogCommentsMapper, BlogComments> implements IBlogCommentsService {

}
