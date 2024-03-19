package com.project.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.project.dto.LoginFormDTO;
import com.project.dto.Result;
import com.project.entity.User;

import javax.servlet.http.HttpSession;


public interface IUserService extends IService<User> {

    Result sendCode(String phone, HttpSession session);

    Result login(LoginFormDTO loginForm);
}
