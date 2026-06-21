package com.police.vision.alarm.service;

import com.police.vision.alarm.entity.DispatchContext;
import com.police.vision.alarm.entity.PoliceOfficer;
import lombok.RequiredArgsConstructor;
import org.kie.api.runtime.KieSession;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class DispatchRuleEngine {

    private final KieSession kieSession;

    public DispatchContext calculateDispatch(DispatchContext context) {
        try {
            kieSession.insert(context);
            kieSession.fireAllRules();
        } finally {
            kieSession.dispose();
        }
        return context;
    }

    public List<PoliceOfficer> getRecommendedOfficers(DispatchContext context) {
        DispatchContext result = calculateDispatch(context);
        return result.getRecommendedOfficers();
    }
}
