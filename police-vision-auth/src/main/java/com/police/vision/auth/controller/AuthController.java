package com.police.vision.auth.controller;

import com.police.vision.auth.service.AuthService;
import com.police.vision.common.dto.LoginDTO;
import com.police.vision.common.entity.LoginUser;
import com.police.vision.common.result.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Tag(name = "认证授权", description = "登录、登出、验证码等")
@RestController
@RequestMapping
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @Operation(summary = "用户登录")
    @PostMapping("/login")
    public Result<Map<String, Object>> login(@Valid @RequestBody LoginDTO loginDTO) {
        return Result.success(authService.login(loginDTO));
    }

    @Operation(summary = "用户登出")
    @PostMapping("/logout")
    public Result<Void> logout(@RequestHeader("Authorization") String token) {
        authService.logout(token);
        return Result.success();
    }

    @Operation(summary = "获取当前用户信息")
    @GetMapping("/user/info")
    public Result<LoginUser> getUserInfo(@RequestHeader("Authorization") String token) {
        return Result.success(authService.getCurrentUser(token));
    }

    @Operation(summary = "获取验证码")
    @GetMapping("/captcha")
    public Result<Map<String, String>> getCaptcha() {
        return Result.success(authService.getCaptcha());
    }

    @Operation(summary = "刷新Token")
    @PostMapping("/refresh")
    public Result<Map<String, Object>> refreshToken(@RequestHeader("Authorization") String token) {
        return Result.success(authService.refreshToken(token));
    }
}
