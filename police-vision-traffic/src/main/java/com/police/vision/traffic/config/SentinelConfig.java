package com.police.vision.traffic.config;

import com.alibaba.csp.sentinel.annotation.aspectj.SentinelResourceAspect;
import com.alibaba.csp.sentinel.slots.block.RuleConstant;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRule;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRuleManager;
import jakarta.annotation.PostConstruct;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

@Configuration
public class SentinelConfig {

    public static final String VEHICLE_CONTROL_HANDLE = "vehicle_control_handle";
    public static final String VEHICLE_TRACK_STORE = "vehicle_track_store";
    public static final String TRAFFIC_CAPTURE_CONSUME = "traffic_capture_consume";

    @Bean
    public SentinelResourceAspect sentinelResourceAspect() {
        return new SentinelResourceAspect();
    }

    @PostConstruct
    public void initFlowRules() {
        List<FlowRule> rules = new ArrayList<>();

        FlowRule controlRule = new FlowRule();
        controlRule.setResource(VEHICLE_CONTROL_HANDLE);
        controlRule.setGrade(RuleConstant.FLOW_GRADE_QPS);
        controlRule.setCount(100);
        controlRule.setLimitApp("default");
        rules.add(controlRule);

        FlowRule trackRule = new FlowRule();
        trackRule.setResource(VEHICLE_TRACK_STORE);
        trackRule.setGrade(RuleConstant.FLOW_GRADE_QPS);
        trackRule.setCount(500);
        trackRule.setLimitApp("default");
        rules.add(trackRule);

        FlowRule captureRule = new FlowRule();
        captureRule.setResource(TRAFFIC_CAPTURE_CONSUME);
        captureRule.setGrade(RuleConstant.FLOW_GRADE_QPS);
        captureRule.setCount(1000);
        captureRule.setLimitApp("default");
        rules.add(captureRule);

        FlowRuleManager.loadRules(rules);
    }
}
