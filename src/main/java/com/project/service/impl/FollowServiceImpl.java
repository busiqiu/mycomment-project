package com.project.service.impl;

import com.project.entity.Follow;
import com.project.mapper.FollowMapper;
import com.project.service.IFollowService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;


@Service
public class FollowServiceImpl extends ServiceImpl<FollowMapper, Follow> implements IFollowService {

}
