package com.police.vision.event.util;

import com.police.vision.event.enums.CommandStatusEnum;
import lombok.extern.slf4j.Slf4j;

import java.util.*;

@Slf4j
public class CommandStateMachine {

    private static final Map<Integer, Set<Integer>> VALID_TRANSITIONS = new HashMap<>();

    static {
        Set<Integer> fromCreated = new HashSet<>();
        fromCreated.add(CommandStatusEnum.DISPATCHED.getCode());
        fromCreated.add(CommandStatusEnum.CANCELLED.getCode());
        VALID_TRANSITIONS.put(CommandStatusEnum.CREATED.getCode(), fromCreated);

        Set<Integer> fromDispatched = new HashSet<>();
        fromDispatched.add(CommandStatusEnum.RECEIVED.getCode());
        fromDispatched.add(CommandStatusEnum.CANCELLED.getCode());
        fromDispatched.add(CommandStatusEnum.TIMEOUT.getCode());
        VALID_TRANSITIONS.put(CommandStatusEnum.DISPATCHED.getCode(), fromDispatched);

        Set<Integer> fromReceived = new HashSet<>();
        fromReceived.add(CommandStatusEnum.EXECUTING.getCode());
        fromReceived.add(CommandStatusEnum.FEEDBACK.getCode());
        fromReceived.add(CommandStatusEnum.CANCELLED.getCode());
        fromReceived.add(CommandStatusEnum.TIMEOUT.getCode());
        VALID_TRANSITIONS.put(CommandStatusEnum.RECEIVED.getCode(), fromReceived);

        Set<Integer> fromExecuting = new HashSet<>();
        fromExecuting.add(CommandStatusEnum.FEEDBACK.getCode());
        fromExecuting.add(CommandStatusEnum.COMPLETED.getCode());
        fromExecuting.add(CommandStatusEnum.CANCELLED.getCode());
        fromExecuting.add(CommandStatusEnum.TIMEOUT.getCode());
        VALID_TRANSITIONS.put(CommandStatusEnum.EXECUTING.getCode(), fromExecuting);

        Set<Integer> fromFeedback = new HashSet<>();
        fromFeedback.add(CommandStatusEnum.COMPLETED.getCode());
        fromFeedback.add(CommandStatusEnum.EXECUTING.getCode());
        fromFeedback.add(CommandStatusEnum.CANCELLED.getCode());
        VALID_TRANSITIONS.put(CommandStatusEnum.FEEDBACK.getCode(), fromFeedback);

        Set<Integer> fromCompleted = new HashSet<>();
        VALID_TRANSITIONS.put(CommandStatusEnum.COMPLETED.getCode(), fromCompleted);

        Set<Integer> fromCancelled = new HashSet<>();
        VALID_TRANSITIONS.put(CommandStatusEnum.CANCELLED.getCode(), fromCancelled);

        Set<Integer> fromTimeout = new HashSet<>();
        fromTimeout.add(CommandStatusEnum.EXECUTING.getCode());
        fromTimeout.add(CommandStatusEnum.FEEDBACK.getCode());
        fromTimeout.add(CommandStatusEnum.CANCELLED.getCode());
        VALID_TRANSITIONS.put(CommandStatusEnum.TIMEOUT.getCode(), fromTimeout);
    }

    public static boolean canTransition(Integer currentStatus, Integer targetStatus) {
        if (currentStatus == null || targetStatus == null) {
            return false;
        }
        Set<Integer> validTargets = VALID_TRANSITIONS.get(currentStatus);
        return validTargets != null && validTargets.contains(targetStatus);
    }

    public static boolean isFinalStatus(Integer status) {
        if (status == null) return false;
        return CommandStatusEnum.COMPLETED.getCode().equals(status)
                || CommandStatusEnum.CANCELLED.getCode().equals(status);
    }

    public static boolean isActiveStatus(Integer status) {
        if (status == null) return false;
        return CommandStatusEnum.DISPATCHED.getCode().equals(status)
                || CommandStatusEnum.RECEIVED.getCode().equals(status)
                || CommandStatusEnum.EXECUTING.getCode().equals(status)
                || CommandStatusEnum.FEEDBACK.getCode().equals(status)
                || CommandStatusEnum.TIMEOUT.getCode().equals(status);
    }

    public static List<Integer> getValidNextStatuses(Integer currentStatus) {
        if (currentStatus == null) {
            return Collections.emptyList();
        }
        Set<Integer> nextStatuses = VALID_TRANSITIONS.get(currentStatus);
        if (nextStatuses == null) {
            return Collections.emptyList();
        }
        List<Integer> result = new ArrayList<>(nextStatuses);
        result.sort(Comparator.comparingInt(s -> s));
        return result;
    }

    public static List<Map<String, Object>> getValidNextStatusesWithInfo(Integer currentStatus) {
        List<Integer> codes = getValidNextStatuses(currentStatus);
        List<Map<String, Object>> result = new ArrayList<>();
        for (Integer code : codes) {
            CommandStatusEnum statusEnum = CommandStatusEnum.getByCode(code);
            if (statusEnum != null) {
                Map<String, Object> info = new HashMap<>();
                info.put("code", code);
                info.put("name", statusEnum.getName());
                info.put("description", statusEnum.getDescription());
                result.add(info);
            }
        }
        return result;
    }

    public static String getTransitionDescription(Integer fromStatus, Integer toStatus) {
        if (fromStatus == null || toStatus == null) {
            return "未知状态变更";
        }
        CommandStatusEnum from = CommandStatusEnum.getByCode(fromStatus);
        CommandStatusEnum to = CommandStatusEnum.getByCode(toStatus);
        String fromName = from != null ? from.getName() : "未知";
        String toName = to != null ? to.getName() : "未知";
        return fromName + " → " + toName;
    }

    public static void validateTransition(Integer currentStatus, Integer targetStatus, String commandNo) {
        if (!canTransition(currentStatus, targetStatus)) {
            String desc = getTransitionDescription(currentStatus, targetStatus);
            log.warn("指令状态流转非法，指令：{}，状态：{}", commandNo, desc);
            throw new IllegalStateException("指令状态不允许从 " + currentStatus + " 流转到 " + targetStatus
                    + "（" + desc + "），请检查操作顺序");
        }
    }

    private CommandStateMachine() {}
}
