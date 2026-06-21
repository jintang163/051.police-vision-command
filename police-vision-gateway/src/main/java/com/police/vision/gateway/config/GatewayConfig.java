package com.police.vision.gateway.config;

import com.alibaba.csp.sentinel.adapter.gateway.common.rule.GatewayFlowRule;
import com.alibaba.csp.sentinel.adapter.gateway.common.rule.GatewayRuleManager;
import com.alibaba.csp.sentinel.adapter.gateway.sc.SentinelGatewayFilter;
import com.alibaba.csp.sentinel.adapter.gateway.sc.callback.BlockRequestHandler;
import com.alibaba.csp.sentinel.adapter.gateway.sc.callback.GatewayCallbackManager;
import com.alibaba.csp.sentinel.adapter.gateway.sc.exception.SentinelGatewayBlockExceptionHandler;
import com.alibaba.fastjson2.JSON;
import com.police.vision.common.result.Result;
import com.police.vision.common.result.ResultCode;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.cloud.gateway.route.RouteDefinitionLocator;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerCodecConfigurer;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

import javax.annotation.PostConstruct;
import java.util.*;

@Configuration
public class GatewayConfig {

    private final List<GlobalFilter> globalFilters;
    private final ServerCodecConfigurer serverCodecConfigurer;

    public GatewayConfig(ObjectProvider<List<GlobalFilter>> globalFiltersProvider,
                         ServerCodecConfigurer serverCodecConfigurer) {
        this.globalFilters = globalFiltersProvider.getIfAvailable(Collections::emptyList);
        this.serverCodecConfigurer = serverCodecConfigurer;
    }

    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE)
    public SentinelGatewayBlockExceptionHandler sentinelGatewayBlockExceptionHandler() {
        return new SentinelGatewayBlockExceptionHandler(
                new org.springframework.cloud.gateway.handler.support.HandlerResultHandler[] {},
                serverCodecConfigurer);
    }

    @Bean
    @Order(-1)
    public GlobalFilter sentinelGatewayFilter() {
        return new SentinelGatewayFilter();
    }

    @Bean
    public KeyResolver ipKeyResolver() {
        return exchange -> Mono.just(
                Objects.requireNonNull(exchange.getRequest().getRemoteAddress()).getAddress().getHostAddress()
        );
    }

    @Bean
    public KeyResolver userKeyResolver() {
        return exchange -> {
            String token = exchange.getRequest().getHeaders().getFirst("Authorization");
            if (token != null && token.startsWith("Bearer ")) {
                token = token.substring(7);
            }
            return Mono.just(token != null ? token : "anonymous");
        };
    }

    @PostConstruct
    public void doInit() {
        initBlockHandler();
        initGatewayRules();
    }

    private void initBlockHandler() {
        BlockRequestHandler blockRequestHandler = (exchange, t) -> {
            Result<Void> result = Result.fail(ResultCode.RATE_LIMITED);
            return ServerResponse.status(HttpStatus.TOO_MANY_REQUESTS)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(BodyInserters.fromValue(JSON.toJSONString(result)));
        };
        GatewayCallbackManager.setBlockHandler(blockRequestHandler);
    }

    private void initGatewayRules() {
        Set<GatewayFlowRule> rules = new HashSet<>();

        rules.add(new GatewayFlowRule("police-vision-gis")
                .setCount(100)
                .setIntervalSec(1)
                .setGrade(1)
        );

        rules.add(new GatewayFlowRule("police-vision-alarm")
                .setCount(50)
                .setIntervalSec(1)
        );

        rules.add(new GatewayFlowRule("police-vision-websocket")
                .setCount(200)
                .setIntervalSec(1)
        );

        GatewayRuleManager.loadRules(rules);
    }

    @Bean
    public RouteLocator routeLocator(RouteLocatorBuilder builder, RouteDefinitionLocator routeDefinitionLocator) {
        return builder.routes()
                .route("websocket_route", r -> r
                        .path("/ws/**")
                        .uri("lb:ws://police-vision-websocket"))
                .build();
    }
}
