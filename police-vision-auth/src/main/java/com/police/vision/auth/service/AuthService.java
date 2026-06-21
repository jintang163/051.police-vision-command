package com.police.vision.auth.service;

import com.alibaba.fastjson2.JSON;
import com.police.vision.auth.entity.PoliceUser;
import com.police.vision.auth.mapper.PoliceUserMapper;
import com.police.vision.common.constant.RedisConstant;
import com.police.vision.common.dto.LoginDTO;
import com.police.vision.common.entity.LoginUser;
import com.police.vision.common.exception.BusinessException;
import com.police.vision.common.result.ResultCode;
import com.police.vision.common.util.JwtUtil;
import com.police.vision.common.util.RedisUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final PoliceUserMapper policeUserMapper;
    private final PasswordEncoder passwordEncoder;
    private final RedisUtil redisUtil;

    public Map<String, Object> login(LoginDTO loginDTO) {
        PoliceUser user = policeUserMapper.selectByPoliceNo(loginDTO.getPoliceNo());
        if (user == null) {
            throw new BusinessException(ResultCode.USER_NOT_EXIST);
        }
        if (user.getStatus() != 1) {
            throw new BusinessException(ResultCode.USER_DISABLED);
        }
        if (!passwordEncoder.matches(loginDTO.getPassword(), user.getPassword())) {
            throw new BusinessException(ResultCode.USER_PASSWORD_ERROR);
        }

        String token = JwtUtil.generateToken(user.getId(), user.getPoliceNo(), user.getName());
        List<String> roles = policeUserMapper.selectRoleCodesByUserId(user.getId());
        List<String> permissions = policeUserMapper.selectPermissionCodesByUserId(user.getId());

        LoginUser loginUser = new LoginUser(
                user.getId(),
                user.getPoliceNo(),
                user.getName(),
                user.getPhone(),
                user.getDeptId(),
                user.getDeptName(),
                roles,
                permissions,
                token,
                System.currentTimeMillis() + JwtUtil.getExpireTime() * 1000
        );

        redisUtil.setObject(RedisConstant.USER_PREFIX + token, loginUser, JwtUtil.getExpireTime(), TimeUnit.SECONDS);
        redisUtil.setObject(RedisConstant.TOKEN_PREFIX + user.getId(), token, JwtUtil.getExpireTime(), TimeUnit.SECONDS);

        Map<String, Object> result = new HashMap<>();
        result.put("token", token);
        result.put("tokenType", "Bearer");
        result.put("expiresIn", JwtUtil.getExpireTime());
        result.put("user", loginUser);

        log.info("用户登录成功：policeNo={}, name={}", user.getPoliceNo(), user.getName());
        return result;
    }

    public void logout(String token) {
        if (token != null && token.startsWith("Bearer ")) {
            token = token.substring(7);
        }
        Long userId = JwtUtil.getUserId(token);
        if (userId != null) {
            redisUtil.delete(RedisConstant.USER_PREFIX + token);
            redisUtil.delete(RedisConstant.TOKEN_PREFIX + userId);
        }
    }

    public LoginUser getCurrentUser(String token) {
        if (token != null && token.startsWith("Bearer ")) {
            token = token.substring(7);
        }
        return redisUtil.getObject(RedisConstant.USER_PREFIX + token, LoginUser.class);
    }

    public Map<String, String> getCaptcha() {
        String captchaKey = UUID.randomUUID().toString().replace("-", "");
        String captcha = UUID.randomUUID().toString().substring(0, 4);

        redisUtil.set(RedisConstant.RATE_LIMIT_PREFIX + "captcha:" + captchaKey, captcha.toLowerCase(), 5, TimeUnit.MINUTES);

        Map<String, String> result = new HashMap<>();
        result.put("captchaKey", captchaKey);
        result.put("captcha", captcha);
        return result;
    }

    public Map<String, Object> refreshToken(String token) {
        if (token != null && token.startsWith("Bearer ")) {
            token = token.substring(7);
        }
        LoginUser loginUser = redisUtil.getObject(RedisConstant.USER_PREFIX + token, LoginUser.class);
        if (loginUser == null) {
            throw new BusinessException(ResultCode.TOKEN_EXPIRED);
        }

        String newToken = JwtUtil.generateToken(loginUser.getUserId(), loginUser.getPoliceNo(), loginUser.getName());
        loginUser.setToken(newToken);
        loginUser.setExpireTime(System.currentTimeMillis() + JwtUtil.getExpireTime() * 1000);

        redisUtil.delete(RedisConstant.USER_PREFIX + token);
        redisUtil.delete(RedisConstant.TOKEN_PREFIX + loginUser.getUserId());
        redisUtil.setObject(RedisConstant.USER_PREFIX + newToken, loginUser, JwtUtil.getExpireTime(), TimeUnit.SECONDS);
        redisUtil.setObject(RedisConstant.TOKEN_PREFIX + loginUser.getUserId(), newToken, JwtUtil.getExpireTime(), TimeUnit.SECONDS);

        Map<String, Object> result = new HashMap<>();
        result.put("token", newToken);
        result.put("expiresIn", JwtUtil.getExpireTime());
        return result;
    }
}
