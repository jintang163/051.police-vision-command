package com.police.vision.common.util;

import com.alibaba.fastjson2.JSON;
import com.police.vision.common.constant.RedisConstant;
import com.police.vision.common.entity.LoginUser;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

public class UserContext {

    private static final ThreadLocal<LoginUser> USER_THREAD_LOCAL = new ThreadLocal<>();
    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    private UserContext() {}

    public static void setCurrentUser(LoginUser loginUser) {
        USER_THREAD_LOCAL.set(loginUser);
    }

    public static LoginUser getCurrentUser() {
        LoginUser user = USER_THREAD_LOCAL.get();
        if (user != null) {
            return user;
        }
        String token = getTokenFromRequest();
        if (token != null) {
            RedisUtil redisUtil = SpringContextUtil.getBean(RedisUtil.class);
            if (redisUtil != null) {
                String userJson = redisUtil.get(RedisConstant.USER_PREFIX + token);
                if (userJson != null) {
                    user = JSON.parseObject(userJson, LoginUser.class);
                    USER_THREAD_LOCAL.set(user);
                }
            }
        }
        return user;
    }

    public static Long getCurrentUserId() {
        LoginUser user = getCurrentUser();
        return user != null ? user.getUserId() : null;
    }

    public static String getCurrentPoliceNo() {
        LoginUser user = getCurrentUser();
        return user != null ? user.getPoliceNo() : null;
    }

    public static String getCurrentUserName() {
        LoginUser user = getCurrentUser();
        return user != null ? user.getName() : null;
    }

    public static Long getCurrentDeptId() {
        LoginUser user = getCurrentUser();
        return user != null ? user.getDeptId() : null;
    }

    public static void clear() {
        USER_THREAD_LOCAL.remove();
    }

    private static String getTokenFromRequest() {
        try {
            ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes != null) {
                HttpServletRequest request = attributes.getRequest();
                String authHeader = request.getHeader(AUTHORIZATION_HEADER);
                if (authHeader != null && authHeader.startsWith(BEARER_PREFIX)) {
                    return authHeader.substring(BEARER_PREFIX.length());
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }
}
