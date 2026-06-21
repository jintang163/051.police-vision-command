package com.police.vision.gateway.filter;

import com.alibaba.fastjson2.JSON;
import com.police.vision.common.constant.RedisConstant;
import com.police.vision.common.result.Result;
import com.police.vision.common.result.ResultCode;
import com.police.vision.common.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class AuthFilter implements GlobalFilter, Ordered {

    private final StringRedisTemplate stringRedisTemplate;
    private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();

    private static final List<String> WHITE_LIST = Arrays.asList(
            "/auth/login",
            "/auth/captcha",
            "/auth/register",
            "/ws/**",
            "/doc.html",
            "/webjars/**",
            "/v3/api-docs/**",
            "/swagger-resources/**",
            "/favicon.ico",
            "/actuator/**",
            "/error"
    );

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getURI().getPath();

        if (isWhiteList(path)) {
            return chain.filter(exchange);
        }

        String token = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (token == null || token.isEmpty()) {
            return unauthorizedResponse(exchange, ResultCode.UNAUTHORIZED);
        }

        if (token.startsWith("Bearer ")) {
            token = token.substring(7);
        }

        if (!JwtUtil.validateToken(token)) {
            return unauthorizedResponse(exchange, ResultCode.TOKEN_INVALID);
        }

        String userKey = RedisConstant.USER_PREFIX + token;
        Boolean hasKey = stringRedisTemplate.hasKey(userKey);
        if (Boolean.FALSE.equals(hasKey)) {
            return unauthorizedResponse(exchange, ResultCode.TOKEN_EXPIRED);
        }

        Long userId = JwtUtil.getUserId(token);
        ServerHttpRequest modifiedRequest = request.mutate()
                .header("X-User-Id", userId != null ? userId.toString() : "")
                .header("X-Token", token)
                .build();

        return chain.filter(exchange.mutate().request(modifiedRequest).build());
    }

    @Override
    public int getOrder() {
        return -100;
    }

    private boolean isWhiteList(String path) {
        return WHITE_LIST.stream()
                .anyMatch(pattern -> PATH_MATCHER.match(pattern, path));
    }

    private Mono<Void> unauthorizedResponse(ServerWebExchange exchange, ResultCode resultCode) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);

        Result<Void> result = Result.fail(resultCode);
        String json = JSON.toJSONString(result);
        DataBuffer buffer = response.bufferFactory().wrap(json.getBytes(StandardCharsets.UTF_8));

        log.warn("请求未授权：path={}, code={}, message={}",
                exchange.getRequest().getURI().getPath(), resultCode.getCode(), resultCode.getMessage());

        return response.writeWith(Mono.just(buffer));
    }
}
