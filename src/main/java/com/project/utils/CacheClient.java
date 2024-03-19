package com.project.utils;


import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.project.entity.Shop;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static com.project.utils.RedisConstants.*;

@Slf4j
@Component
public class CacheClient {

    private final StringRedisTemplate stringRedisTemplate;

    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);
    public CacheClient(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }
    public void set(String key, Object value, Long timeout, TimeUnit timeUnit) {
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(value), timeout, timeUnit);
    }
    public void setWithLogicalExpire(String key, Object value,Long time,TimeUnit timeUnit) {
        //设置逻辑过期
        RedisData redisData = new RedisData();
        redisData.setData(value);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(timeUnit.toSeconds(time)));
        stringRedisTemplate.opsForValue().set(key,JSONUtil.toJsonStr(redisData));
    }

    public <R,ID> R queryWithPassThrough(String keyPrefix, ID id, Class<R> type,
                                         Function<ID,R> dbFallback,Long time,TimeUnit timeUnit){//防止缓存穿透
        String key = keyPrefix +id;
        //1.从redis中查询商铺缓存
        String json = stringRedisTemplate.opsForValue().get(key);
        //判断是否存在
        if (StrUtil.isNotBlank(json)) {//存在返回数据
            return JSONUtil.toBean(json,type);//将json串转化成shop对象
        }
        if (json != null) {//如果查询到的是空值直接返回
            return null;
        }
        //不存在，到数据库中查找
        R r = dbFallback.apply(id);
        if (r == null) {//不存在将空值写入redis,防止缓存穿透，最后返回错误
            stringRedisTemplate.opsForValue().set(key,"",CACHE_NULL_TTL,TimeUnit.MINUTES);
            return null;
        }
        //存在先写入redis中,设置超时时间保证线程安全
        this.set(key,r,time,timeUnit);
        return r;
    }
    public <R,ID> R queryWithLogicalExpire(String keyPrefix,String lockPrefix,
                                           ID id,Class<R> type,Function<ID,R> dbFallback,Long time,TimeUnit timeUnit){//使用逻辑过期防止缓存击穿
        String key = keyPrefix +id;
        //1.从redis中查询商铺缓存
        String shopJson = stringRedisTemplate.opsForValue().get(key);
        //判断是否存在
        if (StrUtil.isBlank(shopJson)) {
            return null;
        }
        //存在的话判断是否过期
        RedisData redisData = JSONUtil.toBean(shopJson, RedisData.class);
        JSONObject data = (JSONObject) redisData.getData();
        R r = JSONUtil.toBean(data, type);
        LocalDateTime expireTime = redisData.getExpireTime();

        if (expireTime.isAfter(LocalDateTime.now())) {
            return r;
        }
        //过期进行缓存重建
        String lockKey = lockPrefix + id;
        //获取互斥锁
        boolean isLock = trylock(lockKey);
        if (isLock) {//获取锁成功
            //先进行DoubleCheck确认缓存不存在
            shopJson = stringRedisTemplate.opsForValue().get(key);
            //判断是否存在
            if (StrUtil.isBlank(shopJson)) {
                return null;
            }
            //存在的话判断是否过期
            redisData = JSONUtil.toBean(shopJson, RedisData.class);
            data = (JSONObject) redisData.getData();
            r = JSONUtil.toBean(data, type);
            expireTime = redisData.getExpireTime();
            if (expireTime.isAfter(LocalDateTime.now())) {
                return r;
            }
            //双重检查不存在后，开启一个新的线程进行缓存重建
            CACHE_REBUILD_EXECUTOR.submit(() ->{
                try {
                    R r1 = dbFallback.apply(id);
                    this.setWithLogicalExpire(key,r1,time,timeUnit);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }finally {
                    unlock(lockKey);
                }
            } );
        }
        //过期了先返回旧数据
        return r;
    }

    private boolean trylock(String key){
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", 10, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(flag);
    }
    private void unlock(String key){
        stringRedisTemplate.delete(key);
    }
}
