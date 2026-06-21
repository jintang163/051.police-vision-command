package com.police.vision.gateway.filter;

import com.alibaba.fastjson2.JSON;
import com.police.vision.common.constant.RedisConstant;
import com.police.vision.common.result.Result;
import com.police.vision.common.result.ResultCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class BlacklistFilter implements GlobalFilter, Ordered {

    private final StringRedisTemplate stringRedisTemplate;
    private static final int MAX_REQUESTS_PER_MINUTE = 300;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String ip = Objects.requireNonNull(request.getRemoteAddress()).getAddress().getHostAddress();

        if (isBlacklisted(ip)) {
            return forbiddenResponse(exchange, "IP已被限制访问，请稍后再试");
        }

        if (isRateLimited(ip)) {
            addToBlacklist(ip);
            return forbiddenResponse(exchange, "请求过于频繁，IP已被临时限制");
        }

        return chain.filter(exchange);
    }

    @Override
    public int getOrder() {
        return -200;
    }

    private boolean isBlacklisted(String ip) {
        String key = RedisConstant.RATE_LIMIT_PREFIX + "blacklist:" + ip;
        return Boolean.TRUE.equals(stringRedisTemplate.hasKey(key));
    }

    private boolean isRateLimited(String ip) {
        String key = RedisConstant.RATE_LIMIT_PREFIX + "count:" + ip;
        Long count = stringRedisTemplate.opsForValue().increment(key);
        if (count != null && count == 1) {
            stringRedisTemplate.expire(key, 1, TimeUnit.MINUTES);
        }
        return count != null && count > MAX_REQUESTS_PER_MINUTE;
    }

    private void addToBlacklist(String ip) {
        String key = RedisConstant.RATE_LIMIT_PREFIX + "blacklist:" + ip;
        stringRedisTemplate.opsForValue().set(key, "1", 5, TimeUnit.MINUTES);
        log.warn("IP {} 因请求频繁被加入黑名单5分钟", ip);
    }

    private Mono<Void> forbiddenResponse(ServerWebExchange exchange, String message) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.FORBIDDEN);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);

        Result<Void> result = Result.fail(ResultCode.RATE_LIMITED.getCode(), message);
        String json = JSON.toJSONString(result);
        DataBuffer buffer = response.bufferFactory().wrap(json.getBytes(StandardCharsets.UTF_8));

        return response.writeWith(Mono.just(buffer));
    }
}
