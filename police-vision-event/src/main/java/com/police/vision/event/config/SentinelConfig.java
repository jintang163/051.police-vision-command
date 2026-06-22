package com.police.vision.event.config;

import com.alibaba.csp.sentinel.annotation.aspectj.SentinelResourceAspect;
import com.alibaba.csp.sentinel.slots.block.RuleConstant;
import com.alibaba.csp.sentinel.slots.block.degrade.DegradeRule;
import com.alibaba.csp.sentinel.slots.block.degrade.DegradeRuleManager;
import com.alibaba.csp.sentinel.slots.block.degrade.circuitbreaker.CircuitBreakerStrategy;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRule;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRuleManager;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

@Configuration
@RequiredArgsConstructor
public class SentinelConfig {

    public static final String EMERGENCY_PLAN_START = "emergency_plan_start";
    public static final String EMERGENCY_COMMAND_DISPATCH = "emergency_command_dispatch";
    public static final String EMERGENCY_RESOURCE_ALLOCATE = "emergency_resource_allocate";
    public static final String EMERGENCY_WEBRTC_SIGNAL = "emergency_webrtc_signal";
    public static final String EMERGENCY_FENCE_OPERATION = "emergency_fence_operation";

    private final EventNacosConfig nacosConfig;

    @Bean
    public SentinelResourceAspect sentinelResourceAspect() {
        return new SentinelResourceAspect();
    }

    @PostConstruct
    public void initRules() {
        initFlowRules();
        initDegradeRules();
    }

    private void initFlowRules() {
        List<FlowRule> rules = new ArrayList<>();

        FlowRule planStartRule = new FlowRule();
        planStartRule.setResource(EMERGENCY_PLAN_START);
        planStartRule.setGrade(RuleConstant.FLOW_GRADE_QPS);
        planStartRule.setCount(nacosConfig.getSentinelPlanStartQps());
        planStartRule.setLimitApp("default");
        rules.add(planStartRule);

        FlowRule commandDispatchRule = new FlowRule();
        commandDispatchRule.setResource(EMERGENCY_COMMAND_DISPATCH);
        commandDispatchRule.setGrade(RuleConstant.FLOW_GRADE_QPS);
        commandDispatchRule.setCount(nacosConfig.getSentinelCommandDispatchQps());
        commandDispatchRule.setLimitApp("default");
        rules.add(commandDispatchRule);

        FlowRule resourceAllocateRule = new FlowRule();
        resourceAllocateRule.setResource(EMERGENCY_RESOURCE_ALLOCATE);
        resourceAllocateRule.setGrade(RuleConstant.FLOW_GRADE_QPS);
        resourceAllocateRule.setCount(20);
        resourceAllocateRule.setLimitApp("default");
        rules.add(resourceAllocateRule);

        FlowRule webrtcSignalRule = new FlowRule();
        webrtcSignalRule.setResource(EMERGENCY_WEBRTC_SIGNAL);
        webrtcSignalRule.setGrade(RuleConstant.FLOW_GRADE_QPS);
        webrtcSignalRule.setCount(100);
        webrtcSignalRule.setLimitApp("default");
        rules.add(webrtcSignalRule);

        FlowRule fenceOperationRule = new FlowRule();
        fenceOperationRule.setResource(EMERGENCY_FENCE_OPERATION);
        fenceOperationRule.setGrade(RuleConstant.FLOW_GRADE_QPS);
        fenceOperationRule.setCount(30);
        fenceOperationRule.setLimitApp("default");
        rules.add(fenceOperationRule);

        FlowRuleManager.loadRules(rules);
    }

    private void initDegradeRules() {
        List<DegradeRule> degradeRules = new ArrayList<>();

        DegradeRule planStartDegradeRule = new DegradeRule();
        planStartDegradeRule.setResource(EMERGENCY_PLAN_START);
        planStartDegradeRule.setGrade(CircuitBreakerStrategy.ERROR_RATIO.getType());
        planStartDegradeRule.setCount(0.4);
        planStartDegradeRule.setTimeWindow(30);
        planStartDegradeRule.setStatIntervalMs(10000);
        planStartDegradeRule.setMinRequestAmount(5);
        degradeRules.add(planStartDegradeRule);

        DegradeRule commandDispatchDegradeRule = new DegradeRule();
        commandDispatchDegradeRule.setResource(EMERGENCY_COMMAND_DISPATCH);
        commandDispatchDegradeRule.setGrade(CircuitBreakerStrategy.ERROR_RATIO.getType());
        commandDispatchDegradeRule.setCount(0.3);
        commandDispatchDegradeRule.setTimeWindow(20);
        commandDispatchDegradeRule.setStatIntervalMs(10000);
        commandDispatchDegradeRule.setMinRequestAmount(10);
        degradeRules.add(commandDispatchDegradeRule);

        DegradeRule resourceAllocateDegradeRule = new DegradeRule();
        resourceAllocateDegradeRule.setResource(EMERGENCY_RESOURCE_ALLOCATE);
        resourceAllocateDegradeRule.setGrade(CircuitBreakerStrategy.SLOW_REQUEST_RATIO.getType());
        resourceAllocateDegradeRule.setCount(0.3);
        resourceAllocateDegradeRule.setTimeWindow(20);
        resourceAllocateDegradeRule.setSlowRatioThreshold(3000);
        resourceAllocateDegradeRule.setStatIntervalMs(10000);
        resourceAllocateDegradeRule.setMinRequestAmount(5);
        degradeRules.add(resourceAllocateDegradeRule);

        DegradeRuleManager.loadRules(degradeRules);
    }
}
