package com.project.service.impl;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSON;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.project.dto.Result;
import com.project.entity.Shop;
import com.project.mapper.ShopMapper;
import com.project.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.project.utils.CacheClient;
import com.project.utils.RedisConstants;
import com.project.utils.RedisData;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;

import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static com.project.utils.RedisConstants.*;


@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private CacheClient cacheClient;

    @Override
    public Result queryShopById(Long id) {
        //解决缓存穿透
        Shop shop = null;
        if (id != 1){//TODO:测试期间使用
            shop = cacheClient.queryWithPassThrough(CACHE_SHOP_KEY,id,Shop.class,this::getById,CACHE_SHOP_TTL,TimeUnit.MINUTES);
        }
        //互斥锁解决缓存击穿
//        Shop shop = queryWithMutex(id);
        //逻辑过期解决缓存击穿
        if (id == 1) {//TODO:测试期间使用
            shop = cacheClient.queryWithLogicalExpire
                    (CACHE_SHOP_KEY,LOCK_SHOP_KEY,id,Shop.class,this::getById,20L,TimeUnit.SECONDS);
        }
        if (shop == null) {
            return Result.fail("店铺不存在!!");
        }

        //互斥锁解决缓存击穿
        return Result.ok(shop);
    }



//    public Shop queryWithMutex(Long id){//利用互斥锁防止缓存击穿
//        String key = CACHE_SHOP_KEY +id;
//        //1.从redis中查询商铺缓存
//        String shopJson = stringRedisTemplate.opsForValue().get(key);
//        //判断是否存在
//        if (StrUtil.isNotBlank(shopJson)) {//存在返回数据
//            return JSONUtil.toBean(shopJson, Shop.class);//将json串转化成shop对象
//        }
//        if (shopJson != null) {//如果查询到的是空值直接返回
//            return null;
//        }
//        Shop shop = null;
//        try {
//            //获取互斥锁
//            boolean islock = trylock(LOCK_SHOP_KEY + id);
//            //获取失败,休眠一段时间后继续尝试
//            if (!islock) {
//                Thread.sleep(50);
//                return queryWithMutex(id);
//            }
//            //获取锁成功后再次检测redis缓存是否存在
//            String shopJson2 = stringRedisTemplate.opsForValue().get(key);
//            //判断是否存在，存在则无需重建缓存
//            if (StrUtil.isNotBlank(shopJson2)) {//存在返回数据
//                return JSONUtil.toBean(shopJson2, Shop.class);
//            }
//            if (shopJson2 != null) {//如果查询到的是空值直接返回
//                return null;
//            }
//            //不存在再到数据库中查找进行缓存重建
//            shop = getById(id);
//
////            //模拟重建的延时
////            Thread.sleep(200);
//
//            if (shop == null) {//不存在将空值写入redis,防止缓存穿透，最后返回错误
//                stringRedisTemplate.opsForValue().set(key,"",CACHE_NULL_TTL,TimeUnit.MINUTES);
//                return null;
//            }
//            //存在先写入redis中,设置超时时间保证线程安全
//            stringRedisTemplate.opsForValue().set(key,JSONUtil.toJsonStr(shop),CACHE_SHOP_TTL, TimeUnit.MINUTES);
//        } catch (InterruptedException e) {
//            throw new RuntimeException(e);
//        }finally {
//            //释放互斥锁
//            unlock(LOCK_SHOP_KEY + id);
//        }
//        return shop;
//    }

    public void saveShop2Redis(Long id,Long expireSeconds){
        // 查询店铺数据
        Shop shop = getById(id);
        // 封装逻辑过期时间和商铺信息
        RedisData redisData = new RedisData();
        redisData.setData(shop);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(expireSeconds));
        // 写入Redis
        stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id,JSONUtil.toJsonStr(redisData));
    }
    @Override
    @Transactional
    public Result updateShop(Shop shop) {
        Long id = shop.getId();
        if (id == null) {
            return Result.fail("店铺ID不能为空");
        }
        //更新数据库
        updateById(shop);
        //删除缓存
        stringRedisTemplate.delete(CACHE_SHOP_KEY + shop.getId());
        return Result.ok();
    }


}
