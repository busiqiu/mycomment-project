package com.project.utils;

import cn.hutool.core.bean.BeanUtil;
import com.project.dto.UserDTO;
import com.project.entity.User;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class LoginInterceptor implements HandlerInterceptor {//判断
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        //根据ThreadLocal中是否有用户
        if(UserHolder.getUser() == null){//没有用户，拦截
            response.setStatus(401);
            return false;
        }
        return true;
    }
}
