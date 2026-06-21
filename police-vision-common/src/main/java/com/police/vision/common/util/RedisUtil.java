package com.police.vision.common.util;

import com.alibaba.fastjson2.JSON;
import com.police.vision.common.constant.RedisConstant;
import com.police.vision.common.exception.BusinessException;
import com.police.vision.common.result.ResultCode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.*;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.TimeUnit;

@Component
@RequiredArgsConstructor
public class RedisUtil {

    private final StringRedisTemplate stringRedisTemplate;

    public void set(String key, String value) {
        try {
            stringRedisTemplate.opsForValue().set(key, value);
        } catch (Exception e) {
            throw new BusinessException(ResultCode.REDIS_ERROR);
        }
    }

    public void set(String key, String value, long timeout, TimeUnit unit) {
        try {
            stringRedisTemplate.opsForValue().set(key, value, timeout, unit);
        } catch (Exception e) {
            throw new BusinessException(ResultCode.REDIS_ERROR);
        }
    }

    public void setObject(String key, Object value) {
        set(key, JSON.toJSONString(value));
    }

    public void setObject(String key, Object value, long timeout, TimeUnit unit) {
        set(key, JSON.toJSONString(value), timeout, unit);
    }

    public String get(String key) {
        try {
            return stringRedisTemplate.opsForValue().get(key);
        } catch (Exception e) {
            throw new BusinessException(ResultCode.REDIS_ERROR);
        }
    }

    public <T> T getObject(String key, Class<T> clazz) {
        String value = get(key);
        return value != null ? JSON.parseObject(value, clazz) : null;
    }

    public Boolean delete(String key) {
        try {
            return stringRedisTemplate.delete(key);
        } catch (Exception e) {
            throw new BusinessException(ResultCode.REDIS_ERROR);
        }
    }

    public Long delete(Collection<String> keys) {
        try {
            return stringRedisTemplate.delete(keys);
        } catch (Exception e) {
            throw new BusinessException(ResultCode.REDIS_ERROR);
        }
    }

    public Boolean hasKey(String key) {
        try {
            return stringRedisTemplate.hasKey(key);
        } catch (Exception e) {
            throw new BusinessException(ResultCode.REDIS_ERROR);
        }
    }

    public Boolean expire(String key, long timeout, TimeUnit unit) {
        try {
            return stringRedisTemplate.expire(key, timeout, unit);
        } catch (Exception e) {
            throw new BusinessException(ResultCode.REDIS_ERROR);
        }
    }

    public Long getExpire(String key) {
        try {
            return stringRedisTemplate.getExpire(key);
        } catch (Exception e) {
            throw new BusinessException(ResultCode.REDIS_ERROR);
        }
    }

    public Long increment(String key, long delta) {
        try {
            return stringRedisTemplate.opsForValue().increment(key, delta);
        } catch (Exception e) {
            throw new BusinessException(ResultCode.REDIS_ERROR);
        }
    }

    public Long decrement(String key, long delta) {
        try {
            return stringRedisTemplate.opsForValue().decrement(key, delta);
        } catch (Exception e) {
            throw new BusinessException(ResultCode.REDIS_ERROR);
        }
    }

    public void hSet(String key, String hashKey, String value) {
        try {
            stringRedisTemplate.opsForHash().put(key, hashKey, value);
        } catch (Exception e) {
            throw new BusinessException(ResultCode.REDIS_ERROR);
        }
    }

    public String hGet(String key, String hashKey) {
        try {
            Object value = stringRedisTemplate.opsForHash().get(key, hashKey);
            return value != null ? value.toString() : null;
        } catch (Exception e) {
            throw new BusinessException(ResultCode.REDIS_ERROR);
        }
    }

    public Boolean tryLock(String key, String value, long timeout, TimeUnit unit) {
        try {
            return Boolean.TRUE.equals(stringRedisTemplate.opsForValue()
                    .setIfAbsent(RedisConstant.LOCK_PREFIX + key, value, timeout, unit));
        } catch (Exception e) {
            return false;
        }
    }

    public Boolean unlock(String key, String value) {
        try {
            String currentValue = get(RedisConstant.LOCK_PREFIX + key);
            if (value.equals(currentValue)) {
                return delete(RedisConstant.LOCK_PREFIX + key);
            }
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    public Set<String> keys(String pattern) {
        try {
            return stringRedisTemplate.keys(pattern);
        } catch (Exception e) {
            throw new BusinessException(ResultCode.REDIS_ERROR);
        }
    }

    public void addGeo(String key, double longitude, double latitude, String member) {
        try {
            stringRedisTemplate.opsForGeo().add(key, new org.springframework.data.geo.Point(longitude, latitude), member);
        } catch (Exception e) {
            throw new BusinessException(ResultCode.REDIS_ERROR);
        }
    }

    public List<Object> getGeo(String key, String member) {
        try {
            return stringRedisTemplate.opsForGeo().position(key, member);
        } catch (Exception e) {
            throw new BusinessException(ResultCode.REDIS_ERROR);
        }
    }

    public Double distance(String key, String member1, String member2) {
        try {
            org.springframework.data.geo.Distance distance = stringRedisTemplate.opsForGeo()
                    .distance(key, member1, member2, org.springframework.data.geo.Metrics.KILOMETERS);
            return distance != null ? distance.getValue() : null;
        } catch (Exception e) {
            throw new BusinessException(ResultCode.REDIS_ERROR);
        }
    }
}
