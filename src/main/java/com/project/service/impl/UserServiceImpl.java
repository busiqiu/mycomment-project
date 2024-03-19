package com.project.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.project.dto.LoginFormDTO;
import com.project.dto.Result;
import com.project.dto.UserDTO;
import com.project.entity.User;
import com.project.mapper.UserMapper;
import com.project.service.IUserService;
import com.project.utils.RegexUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.servlet.http.HttpSession;

import java.util.HashMap;
import java.util.Map;

import java.util.concurrent.TimeUnit;

import static com.project.utils.RedisConstants.*;
import static com.project.utils.SystemConstants.USER_NICK_NAME_PREFIX;

@Slf4j
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Override
    public Result sendCode(String phone, HttpSession session) {
        //校验手机号
        if(RegexUtils.isPhoneInvalid(phone)){
            //不符合返回错误信息
            return Result.fail("手机号格式错误");
        }
        //符合生成验证码
        String code = RandomUtil.randomNumbers(6);
        //保存验证码到redis中
        stringRedisTemplate.opsForValue().set(LOGIN_CODE_KEY + phone,code,LOGIN_CODE_TTL, TimeUnit.MINUTES);
        //发送验证码
        log.debug("发送验证码成功,验证码:{}",code);
        return Result.ok();
    }

    @Override
    public Result login(LoginFormDTO loginForm) {
        String phone = loginForm.getPhone();
        //校验手机号
        if(RegexUtils.isPhoneInvalid(phone)){
            //不符合返回错误信息
            return Result.fail("手机号格式错误");
        }
        //从redis中获取，验证验证码
        String cacheCode = stringRedisTemplate.opsForValue().get(LOGIN_CODE_KEY + phone);
        String code = loginForm.getCode();//前端提供的code
        if(cacheCode == null || !cacheCode.equals(code)){//验证码不一致
            return Result.fail("验证码错误");
        }
        //根据手机号查询用户
        User user = query().eq("phone", phone).one();
        if (user == null){//用户不存在则需要创建用户
           user = createUserWithPhone(phone);
        }
        //将用户保存到redis中
        //根据UUID随机生成token
        String token = UUID.randomUUID().toString(true);
        //将User对象转为HashMap存储
        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
        Map<String, Object> userMap = BeanUtil.beanToMap(userDTO,new HashMap<>(),
                CopyOptions.create().setIgnoreNullValue(true)
                        .setFieldValueEditor((fieldName,filedValue) -> filedValue.toString()));

        stringRedisTemplate.opsForHash().putAll(LOGIN_USER_KEY + token,userMap);
        //设置有效期
        stringRedisTemplate.expire(LOGIN_USER_KEY + token,LOGIN_USER_TTL,TimeUnit.MINUTES);
        return Result.ok(token);
    }

    private User createUserWithPhone(String phone) {
        //创建用户
        User user = new User();
        user.setPhone(phone);
        user.setNickName(USER_NICK_NAME_PREFIX+ RandomUtil.randomString(10));
        //保存用户
        save(user);
        return user;
    }
}
