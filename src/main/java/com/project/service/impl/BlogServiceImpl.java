package com.project.service.impl;

import com.project.entity.Blog;
import com.project.mapper.BlogMapper;
import com.project.service.IBlogService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;


@Service
public class BlogServiceImpl extends ServiceImpl<BlogMapper, Blog> implements IBlogService {

}
