package com.project.service.impl;

import com.project.entity.UserInfo;
import com.project.mapper.UserInfoMapper;
import com.project.service.IUserInfoService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;


@Service
public class UserInfoServiceImpl extends ServiceImpl<UserInfoMapper, UserInfo> implements IUserInfoService {

}
