package com.police.vision.event.service;

import com.alibaba.csp.sentinel.annotation.SentinelResource;
import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.police.vision.common.config.RocketMQConfig;
import com.police.vision.common.constant.MqConstant;
import com.police.vision.common.entity.PageResult;
import com.police.vision.common.exception.BusinessException;
import com.police.vision.common.result.ResultCode;
import com.police.vision.common.util.MqUtil;
import com.police.vision.common.util.SnowflakeIdUtil;
import com.police.vision.event.config.EventNacosConfig;
import com.police.vision.event.config.SentinelConfig;
import com.police.vision.event.dto.*;
import com.police.vision.event.entity.*;
import com.police.vision.event.entity.vo.CameraPointVO;
import com.police.vision.event.entity.vo.PoliceLocationVO;
import com.police.vision.event.enums.CommandPriorityEnum;
import com.police.vision.event.enums.CommandStatusEnum;
import com.police.vision.event.enums.EmergencyPlanTemplateEnum;
import com.police.vision.event.feign.GisFeignClient;
import com.police.vision.event.mapper.*;
import com.police.vision.event.util.GeoUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.locationtech.jts.geom.Polygon;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RefreshScope
@RequiredArgsConstructor
public class EmergencyDispatchService {

    private final SecSecurityPlanMapper secSecurityPlanMapper;
    private final SecEventMapper secEventMapper;
    private final SecEmergencyCommandMapper commandMapper;
    private final SecCommandStatusLogMapper statusLogMapper;
    private final SecEmergencySupplyMapper supplyMapper;
    private final GisFeignClient gisFeignClient;
    private final MqUtil mqUtil;
    private final EventNacosConfig nacosConfig;

    @Value("${emergency.plan.nacos.prefix:EMERGENCY_PLAN}")
    private String nacosPlanPrefix;

    @SentinelResource(value = "emergency_plan_start", blockHandler = "startPlanBlockHandler",
            fallback = "startPlanFallbackHandler")
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> startEmergencyPlan(EmergencyPlanStartDTO dto) {
        log.info("启动应急预案开始，事件ID：{}，模板：{}", dto.getEventId(), dto.getTemplateCode());

        SecEvent event = secEventMapper.selectById(dto.getEventId());
        if (event == null) {
            throw new BusinessException(ResultCode.DATA_NOT_FOUND, "事件不存在");
        }

        double radius = (dto.getResourceRadius() != null && dto.getResourceRadius() > 0)
                ? dto.getResourceRadius() : nacosConfig.getEmergencyDefaultResourceRadius();

        boolean autoAllocate = dto.getAutoAllocateResources() != null ? dto.getAutoAllocateResources() : true;
        boolean autoVideo = dto.getAutoStartVideoConference() != null ? dto.getAutoStartVideoConference() : true;

        SecSecurityPlan plan;
        if (dto.getPlanId() != null) {
            plan = secSecurityPlanMapper.selectById(dto.getPlanId());
            if (plan == null) {
                throw new BusinessException(ResultCode.DATA_NOT_FOUND, "预案不存在");
            }
        } else {
            String templateCode = StringUtils.hasText(dto.getTemplateCode())
                    ? dto.getTemplateCode() : nacosConfig.getEmergencyDefaultTemplate();
            plan = createPlanFromTemplate(templateCode, event, radius, autoAllocate, autoVideo);
        }

        plan.setStatus(2);
        plan.setUpdateTime(LocalDateTime.now());
        secSecurityPlanMapper.updateById(plan);

        Map<String, Object> result = new HashMap<>();
        result.put("planId", plan.getId());
        result.put("planName", plan.getPlanName());
        result.put("eventId", event.getId());
        result.put("eventName", event.getEventName());
        result.put("startTime", LocalDateTime.now());

        int policeCount = 0;
        int cameraCount = 0;
        int supplyCount = 0;

        if (autoAllocate) {
            Map<String, Integer> resourceResult = allocateEmergencyResources(event, plan, radius);
            policeCount = resourceResult.getOrDefault("policeCount", 0);
            cameraCount = resourceResult.getOrDefault("cameraCount", 0);
            supplyCount = resourceResult.getOrDefault("supplyCount", 0);
        }

        result.put("policeCount", policeCount);
        result.put("cameraCount", cameraCount);
        result.put("supplyCount", supplyCount);
        result.put("resourceRadius", radius);

        if (autoVideo) {
            String roomId = generateRoomId(event.getId());
            result.put("videoRoomId", roomId);
            result.put("videoRoomUrl", generateRoomUrl(event.getId(), roomId));
        }

        sendPlanStartEvent(event, plan, result);

        log.info("启动应急预案成功，事件ID：{}，预案ID：{}，警力：{}，摄像头：{}，物资：{}",
                event.getId(), plan.getId(), policeCount, cameraCount, supplyCount);
        return result;
    }

    public Map<String, Object> startPlanBlockHandler(EmergencyPlanStartDTO dto, BlockException ex) {
        log.warn("启动预案Sentinel限流熔断，事件ID：{}，异常：{}", dto.getEventId(), ex.getClass().getSimpleName());
        Map<String, Object> fallback = new HashMap<>();
        fallback.put("fallback", true);
        fallback.put("message", "系统繁忙，请稍后重试启动预案");
        return fallback;
    }

    public Map<String, Object> startPlanFallbackHandler(EmergencyPlanStartDTO dto, Throwable ex) {
        log.error("启动预案降级处理，事件ID：{}", dto.getEventId(), ex);
        Map<String, Object> fallback = new HashMap<>();
        fallback.put("fallback", true);
        fallback.put("message", "预案启动失败，已触发降级保护");
        return fallback;
    }

    @SentinelResource(value = "emergency_command_dispatch", blockHandler = "dispatchCommandBlockHandler")
    @Transactional(rollbackFor = Exception.class)
    public SecEmergencyCommand dispatchCommand(EmergencyCommandCreateDTO dto) {
        log.info("下达应急指令开始，事件ID：{}，标题：{}", dto.getEventId(), dto.getCommandTitle());

        SecEvent event = secEventMapper.selectById(dto.getEventId());
        if (event == null) {
            throw new BusinessException(ResultCode.DATA_NOT_FOUND, "事件不存在");
        }

        SecEmergencyCommand command = new SecEmergencyCommand();
        BeanUtils.copyProperties(dto, command);
        command.setId(SnowflakeIdUtil.nextId());
        command.setCommandNo(generateCommandNo());
        command.setStatus(CommandStatusEnum.CREATED.getCode());

        if (dto.getPriority() == null) {
            command.setPriority(CommandPriorityEnum.NORMAL.getCode());
        }
        if (dto.getDeadlineMinutes() == null) {
            command.setDeadlineMinutes(nacosConfig.getEmergencyCommandDefaultDeadlineMinutes());
        }
        if (dto.getReceiverDeptIds() != null && !dto.getReceiverDeptIds().isEmpty()) {
            command.setReceiverDeptIds(JSON.toJSONString(dto.getReceiverDeptIds()));
        }
        if (dto.getReceiverNames() != null && !dto.getReceiverNames().isEmpty()) {
            command.setReceiverNames(JSON.toJSONString(dto.getReceiverNames()));
        }
        command.setDispatchTime(LocalDateTime.now());
        command.setStatus(CommandStatusEnum.DISPATCHED.getCode());
        command.setTimeoutCount(0);
        commandMapper.insert(command);

        recordStatusLog(command.getId(), null, CommandStatusEnum.CREATED.getCode(),
                CommandStatusEnum.DISPATCHED.getCode(),
                dto.getSenderId(), dto.getSenderName(), "系统",
                "指令下达成功", null);

        sendCommandMq(command, MqConstant.TAG_COMMAND_DISPATCH);

        log.info("下达应急指令成功，指令ID：{}，指令编号：{}", command.getId(), command.getCommandNo());
        return command;
    }

    public SecEmergencyCommand dispatchCommandBlockHandler(EmergencyCommandCreateDTO dto, BlockException ex) {
        log.warn("下达指令Sentinel限流，事件ID：{}", dto.getEventId());
        throw new BusinessException(ResultCode.SYSTEM_ERROR, "系统繁忙，请稍后重试下达指令");
    }

    @Transactional(rollbackFor = Exception.class)
    public SecEmergencyCommand processCommandFeedback(EmergencyCommandFeedbackDTO dto) {
        log.info("处理指令反馈开始，指令ID：{}，目标状态：{}", dto.getCommandId(), dto.getToStatus());

        SecEmergencyCommand command = commandMapper.selectById(dto.getCommandId());
        if (command == null) {
            throw new BusinessException(ResultCode.DATA_NOT_FOUND, "指令不存在");
        }

        Integer oldStatus = command.getStatus();
        Integer newStatus = dto.getToStatus() != null ? dto.getToStatus() : CommandStatusEnum.FEEDBACK.getCode();

        LocalDateTime now = LocalDateTime.now();
        switch (CommandStatusEnum.getByCode(newStatus)) {
            case RECEIVED:
                command.setReceiveTime(now);
                break;
            case EXECUTING:
                command.setExecuteStartTime(now);
                break;
            case FEEDBACK:
                command.setFeedbackTime(now);
                command.setFeedbackContent(dto.getFeedbackContent());
                command.setFeedbackAttachments(dto.getFeedbackAttachments());
                break;
            case COMPLETED:
                command.setCompleteTime(now);
                command.setFeedbackContent(dto.getFeedbackContent());
                command.setFeedbackAttachments(dto.getFeedbackAttachments());
                break;
            case CANCELLED:
                break;
            case TIMEOUT:
                command.setTimeoutCount(command.getTimeoutCount() + 1);
                break;
            default:
                break;
        }

        command.setStatus(newStatus);
        command.setUpdateTime(now);
        commandMapper.updateById(command);

        recordStatusLog(command.getId(), oldStatus, newStatus,
                dto.getOperatorId(), dto.getOperatorName(), dto.getOperatorDept(),
                dto.getOperateRemark(), dto.getExtraData() != null ? JSON.toJSONString(dto.getExtraData()) : null);

        String tag;
        switch (CommandStatusEnum.getByCode(newStatus)) {
            case RECEIVED:
                tag = MqConstant.TAG_COMMAND_RECEIVE;
                break;
            case FEEDBACK:
                tag = MqConstant.TAG_COMMAND_FEEDBACK;
                break;
            case COMPLETED:
                tag = MqConstant.TAG_COMMAND_COMPLETE;
                break;
            case CANCELLED:
                tag = MqConstant.TAG_COMMAND_CANCEL;
                break;
            case TIMEOUT:
                tag = MqConstant.TAG_COMMAND_TIMEOUT;
                break;
            default:
                tag = MqConstant.TAG_COMMAND_FEEDBACK;
        }
        sendCommandMq(command, tag);

        log.info("处理指令反馈成功，指令ID：{}，状态：{} -> {}", command.getId(), oldStatus, newStatus);
        return command;
    }

    public PageResult<SecEmergencyCommand> listCommands(Long eventId, Integer status, Integer priority,
                                                        int page, int size) {
        LambdaQueryWrapper<SecEmergencyCommand> wrapper = new LambdaQueryWrapper<>();
        if (eventId != null) {
            wrapper.eq(SecEmergencyCommand::getEventId, eventId);
        }
        if (status != null) {
            wrapper.eq(SecEmergencyCommand::getStatus, status);
        }
        if (priority != null) {
            wrapper.eq(SecEmergencyCommand::getPriority, priority);
        }
        wrapper.orderByDesc(SecEmergencyCommand::getDispatchTime);

        Page<SecEmergencyCommand> pageParam = new Page<>(page, size);
        IPage<SecEmergencyCommand> result = commandMapper.selectPage(pageParam, wrapper);
        return PageResult.of(result.getTotal(), result.getRecords(), page, size);
    }

    public Map<String, Object> getCommandDetail(Long commandId) {
        SecEmergencyCommand command = commandMapper.selectById(commandId);
        if (command == null) {
            throw new BusinessException(ResultCode.DATA_NOT_FOUND, "指令不存在");
        }

        Map<String, Object> result = new HashMap<>();
        result.put("command", command);

        LambdaQueryWrapper<SecCommandStatusLog> logWrapper = new LambdaQueryWrapper<>();
        logWrapper.eq(SecCommandStatusLog::getCommandId, commandId);
        logWrapper.orderByAsc(SecCommandStatusLog::getOperateTime);
        List<SecCommandStatusLog> statusLogs = statusLogMapper.selectList(logWrapper);
        result.put("statusLogs", statusLogs);

        return result;
    }

    @SentinelResource(value = "emergency_resource_allocate")
    public Map<String, Object> queryEmergencyResources(EmergencyResourceQueryDTO dto) {
        log.info("查询应急资源，事件ID：{}，类型：{}，半径：{}米", dto.getEventId(), dto.getResourceType(), dto.getRadiusMeters());

        SecEvent event = secEventMapper.selectById(dto.getEventId());
        if (event == null) {
            throw new BusinessException(ResultCode.DATA_NOT_FOUND, "事件不存在");
        }

        double[] center = calculateEventCenter(event, dto.getLng(), dto.getLat());
        double lng = center[0];
        double lat = center[1];
        double radius = (dto.getRadiusMeters() != null && dto.getRadiusMeters() > 0)
                ? dto.getRadiusMeters() : nacosConfig.getEmergencyDefaultResourceRadius();

        Map<String, Object> result = new HashMap<>();
        result.put("centerLng", lng);
        result.put("centerLat", lat);
        result.put("radiusMeters", radius);

        String resourceType = dto.getResourceType();
        if (resourceType == null || "police".equals(resourceType)) {
            List<Map<String, Object>> policeList = queryNearbyPolice(lng, lat, radius / 1000.0);
            result.put("policeList", policeList);
            result.put("policeCount", policeList.size());
        }
        if (resourceType == null || "camera".equals(resourceType)) {
            List<Map<String, Object>> cameraList = queryNearbyCameras(lng, lat, radius);
            result.put("cameraList", cameraList);
            result.put("cameraCount", cameraList.size());
        }
        if (resourceType == null || "supply".equals(resourceType)) {
            List<SecEmergencySupply> supplyList = queryNearbySupplies(event.getId(), lng, lat, radius);
            result.put("supplyList", supplyList);
            result.put("supplyCount", supplyList.size());
        }

        return result;
    }

    public List<Map<String, Object>> listPlanTemplates() {
        List<Map<String, Object>> templates = new ArrayList<>();
        for (EmergencyPlanTemplateEnum templateEnum : EmergencyPlanTemplateEnum.values()) {
            Map<String, Object> tpl = new HashMap<>();
            tpl.put("code", templateEnum.getCode());
            tpl.put("name", templateEnum.getName());
            tpl.put("priority", templateEnum.getPriority());
            tpl.put("description", templateEnum.getDescription());
            tpl.put("nacosConfigKey", nacosPlanPrefix + "_" + templateEnum.getNacosConfigKey());
            templates.add(tpl);
        }
        templates.sort(Comparator.comparingInt(t -> (Integer) t.get("priority")));
        return templates;
    }

    private SecSecurityPlan createPlanFromTemplate(String templateCode, SecEvent event,
                                                    double radius, boolean autoAllocate, boolean autoVideo) {
        EmergencyPlanTemplateEnum template = EmergencyPlanTemplateEnum.getByCode(templateCode);
        if (template == null) {
            template = EmergencyPlanTemplateEnum.getByCode(nacosConfig.getEmergencyDefaultTemplate());
        }

        SecSecurityPlan plan = new SecSecurityPlan();
        plan.setId(SnowflakeIdUtil.nextId());
        plan.setEventId(event.getId());
        plan.setPlanName(template.getName() + "-" + event.getEventName());
        plan.setPlanType("emergency");
        plan.setPlanTemplateCode(template.getCode());
        plan.setIsTemplate(0);
        plan.setEmergencyLevel(template.getPriority());
        plan.setResourceRadius(radius);
        plan.setAutoAllocateResources(autoAllocate ? 1 : 0);
        plan.setAutoStartVideoConference(autoVideo ? 1 : 0);
        plan.setNacosConfigKey(nacosPlanPrefix + "_" + template.getNacosConfigKey());
        plan.setDescription(template.getDescription());
        plan.setStatus(1);
        secSecurityPlanMapper.insert(plan);

        log.info("基于模板创建预案成功，模板：{}，预案ID：{}", template.getCode(), plan.getId());
        return plan;
    }

    @SentinelResource(value = "emergency_resource_allocate")
    private Map<String, Integer> allocateEmergencyResources(SecEvent event, SecSecurityPlan plan, double radius) {
        double[] center = calculateEventCenter(event, null, null);
        double lng = center[0];
        double lat = center[1];

        Map<String, Integer> result = new HashMap<>();
        result.put("policeCount", 0);
        result.put("cameraCount", 0);
        result.put("supplyCount", 0);

        try {
            List<Map<String, Object>> policeList = queryNearbyPolice(lng, lat, radius / 1000.0);
            result.put("policeCount", policeList.size());
        } catch (Exception e) {
            log.error("分配警力资源失败，事件ID：{}", event.getId(), e);
        }

        try {
            List<Map<String, Object>> cameraList = queryNearbyCameras(lng, lat, radius);
            result.put("cameraCount", cameraList.size());
        } catch (Exception e) {
            log.error("分配摄像头资源失败，事件ID：{}", event.getId(), e);
        }

        try {
            List<SecEmergencySupply> supplies = initDefaultSupplies(event, lng, lat, radius);
            result.put("supplyCount", supplies.size());
        } catch (Exception e) {
            log.error("分配物资资源失败，事件ID：{}", event.getId(), e);
        }

        return result;
    }

    private double[] calculateEventCenter(SecEvent event, Double lng, Double lat) {
        if (lng != null && lat != null) {
            return new double[]{lng, lat};
        }
        if (StringUtils.hasText(event.getAreaPolygon())) {
            try {
                Polygon polygon = GeoUtil.parsePolygon(event.getAreaPolygon());
                return GeoUtil.calculatePolygonCenter(polygon);
            } catch (Exception e) {
                log.warn("解析事件区域多边形失败，使用默认坐标", e);
            }
        }
        return new double[]{116.4074, 39.9042};
    }

    private List<Map<String, Object>> queryNearbyPolice(double lng, double lat, double radiusKm) {
        List<PoliceLocationVO> allPolice;
        try {
            com.police.vision.common.result.Result<List<PoliceLocationVO>> result =
                    gisFeignClient.getNearbyPolice(BigDecimal.valueOf(lng), BigDecimal.valueOf(lat), radiusKm);
            allPolice = (result != null && result.isSuccess()) ? result.getData() : Collections.emptyList();
        } catch (Exception e) {
            log.error("查询附近警力异常，尝试获取全量数据", e);
            com.police.vision.common.result.Result<List<PoliceLocationVO>> result = gisFeignClient.getPoliceDistribution();
            allPolice = (result != null && result.isSuccess()) ? result.getData() : Collections.emptyList();
        }

        if (allPolice == null || allPolice.isEmpty()) {
            return Collections.emptyList();
        }

        return allPolice.stream()
                .filter(p -> p.getLongitude() != null && p.getLatitude() != null)
                .map(p -> {
                    Map<String, Object> item = new HashMap<>();
                    item.put("policeId", p.getPoliceId());
                    item.put("name", p.getName());
                    item.put("policeNo", p.getPoliceNo());
                    item.put("dept", p.getDeptName());
                    item.put("status", p.getStatus());
                    item.put("lng", p.getLongitude());
                    item.put("lat", p.getLatitude());
                    item.put("phone", p.getPhone());
                    item.put("deviceId", p.getDeviceId());
                    double distance = GeoUtil.haversineDistance(lng, lat,
                            p.getLongitude().doubleValue(), p.getLatitude().doubleValue());
                    item.put("distanceMeters", Math.round(distance));
                    return item;
                })
                .sorted(Comparator.comparingDouble(m -> (Double) m.get("distanceMeters")))
                .collect(Collectors.toList());
    }

    private List<Map<String, Object>> queryNearbyCameras(double lng, double lat, double radiusMeters) {
        com.police.vision.common.result.Result<List<CameraPointVO>> result = gisFeignClient.getCameraPoints();
        List<CameraPointVO> allCameras = (result != null && result.isSuccess()) ? result.getData() : Collections.emptyList();

        if (allCameras == null || allCameras.isEmpty()) {
            return Collections.emptyList();
        }

        return allCameras.stream()
                .filter(c -> c.getLongitude() != null && c.getLatitude() != null)
                .map(c -> {
                    double distance = GeoUtil.haversineDistance(lng, lat,
                            c.getLongitude().doubleValue(), c.getLatitude().doubleValue());
                    Map<String, Object> item = new HashMap<>();
                    item.put("cameraId", c.getId());
                    item.put("name", c.getName());
                    item.put("deviceNo", c.getDeviceNo());
                    item.put("status", c.getStatus());
                    item.put("lng", c.getLongitude());
                    item.put("lat", c.getLatitude());
                    item.put("address", c.getAddress());
                    item.put("rtspUrl", c.getRtspUrl());
                    item.put("distanceMeters", Math.round(distance));
                    return item;
                })
                .filter(m -> (Double) m.get("distanceMeters") <= radiusMeters)
                .sorted(Comparator.comparingDouble(m -> (Double) m.get("distanceMeters")))
                .collect(Collectors.toList());
    }

    private List<SecEmergencySupply> queryNearbySupplies(Long eventId, double lng, double lat, double radiusMeters) {
        LambdaQueryWrapper<SecEmergencySupply> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SecEmergencySupply::getEventId, eventId);
        List<SecEmergencySupply> list = supplyMapper.selectList(wrapper);

        if (list == null || list.isEmpty()) {
            return Collections.emptyList();
        }

        return list.stream()
                .filter(s -> s.getLng() != null && s.getLat() != null)
                .peek(s -> {
                    double distance = GeoUtil.haversineDistance(lng, lat, s.getLng(), s.getLat());
                    s.setDistanceMeters(distance);
                })
                .filter(s -> s.getDistanceMeters() <= radiusMeters)
                .sorted(Comparator.comparingDouble(SecEmergencySupply::getDistanceMeters))
                .collect(Collectors.toList());
    }

    private List<SecEmergencySupply> initDefaultSupplies(SecEvent event, double lng, double lat, double radius) {
        LambdaQueryWrapper<SecEmergencySupply> clearWrapper = new LambdaQueryWrapper<>();
        clearWrapper.eq(SecEmergencySupply::getEventId, event.getId());
        supplyMapper.delete(clearWrapper);

        List<SecEmergencySupply> defaultSupplies = new ArrayList<>();

        String[][] defaultData = {
                {"防爆盾", "equipment", "50", "面", "防爆装备仓库"},
                {"防刺服", "equipment", "30", "件", "防爆装备仓库"},
                {"头盔", "equipment", "50", "个", "防爆装备仓库"},
                {"催泪喷射器", "equipment", "30", "支", "防爆装备仓库"},
                {"警戒带", "equipment", "20", "卷", "交通器材仓库"},
                {"反光锥", "equipment", "100", "个", "交通器材仓库"},
                {"应急照明", "equipment", "20", "套", "应急物资库"},
                {"扩音喇叭", "communication", "15", "个", "通信保障组"},
                {"对讲机", "communication", "50", "台", "通信保障组"},
                {"急救包", "medical", "30", "个", "医疗救护组"},
                {"担架", "medical", "10", "副", "医疗救护组"},
                {"AED除颤仪", "medical", "5", "台", "医疗救护组"}
        };

        for (int i = 0; i < defaultData.length; i++) {
            SecEmergencySupply supply = new SecEmergencySupply();
            supply.setId(SnowflakeIdUtil.nextId());
            supply.setEventId(event.getId());
            supply.setSupplyName(defaultData[i][0]);
            supply.setSupplyType(defaultData[i][1]);
            supply.setQuantity(Integer.parseInt(defaultData[i][2]));
            supply.setUnit(defaultData[i][3]);
            double angle = (i * 36) * Math.PI / 180;
            double r = radius * (0.3 + 0.5 * (i % 3) / 2.0);
            double supplyLng = lng + (r * Math.cos(angle)) / (111000 * Math.cos(lat * Math.PI / 180));
            double supplyLat = lat + (r * Math.sin(angle)) / 111000;
            supply.setLng(supplyLng);
            supply.setLat(supplyLat);
            supply.setAddress(defaultData[i][4]);
            supply.setContactPerson("物资管理员" + (i + 1));
            supply.setContactPhone("138001380" + String.format("%02d", i + 10));
            supply.setStatus(1);
            supply.setDistanceMeters(r);
            supplyMapper.insert(supply);
            defaultSupplies.add(supply);
        }

        log.info("初始化应急物资成功，事件ID：{}，数量：{}", event.getId(), defaultSupplies.size());
        return defaultSupplies;
    }

    private void recordStatusLog(Long commandId, Integer fromStatus, Integer toStatus,
                                  Long operatorId, String operatorName, String operatorDept,
                                  String remark, String extraData) {
        SecCommandStatusLog log = new SecCommandStatusLog();
        log.setId(SnowflakeIdUtil.nextId());
        log.setCommandId(commandId);
        log.setFromStatus(fromStatus);
        log.setToStatus(toStatus);
        log.setOperatorId(operatorId);
        log.setOperatorName(operatorName);
        log.setOperatorDept(operatorDept);
        log.setOperateTime(LocalDateTime.now());
        log.setOperateRemark(remark);
        log.setExtraData(extraData);
        statusLogMapper.insert(log);
    }

    private void sendCommandMq(SecEmergencyCommand command, String tag) {
        Map<String, Object> message = new HashMap<>();
        message.put("commandId", command.getId());
        message.put("commandNo", command.getCommandNo());
        message.put("eventId", command.getEventId());
        message.put("planId", command.getPlanId());
        message.put("commandTitle", command.getCommandTitle());
        message.put("commandContent", command.getCommandContent());
        message.put("priority", command.getPriority());
        message.put("status", command.getStatus());
        message.put("senderId", command.getSenderId());
        message.put("senderName", command.getSenderName());
        message.put("receiverDeptIds", command.getReceiverDeptIds());
        message.put("receiverNames", command.getReceiverNames());
        message.put("deadlineMinutes", command.getDeadlineMinutes());
        message.put("dispatchTime", command.getDispatchTime());
        message.put("timestamp", System.currentTimeMillis());
        message.put("tag", tag);

        mqUtil.sendAsync(
                RocketMQConfig.buildDestination(MqConstant.EMERGENCY_COMMAND_TOPIC, tag),
                message
        );

        mqUtil.sendBroadcast(
                RocketMQConfig.buildDestination(MqConstant.EMERGENCY_COMMAND_TOPIC, tag),
                message
        );

        log.debug("发送指令MQ消息成功，指令ID：{}，标签：{}", command.getId(), tag);
    }

    private void sendPlanStartEvent(SecEvent event, SecSecurityPlan plan, Map<String, Object> result) {
        Map<String, Object> message = new HashMap<>();
        message.put("eventId", event.getId());
        message.put("eventName", event.getEventName());
        message.put("eventLevel", event.getEventLevel());
        message.put("planId", plan.getId());
        message.put("planName", plan.getPlanName());
        message.put("templateCode", plan.getPlanTemplateCode());
        message.put("emergencyLevel", plan.getEmergencyLevel());
        message.put("resourceRadius", result.get("resourceRadius"));
        message.put("policeCount", result.get("policeCount"));
        message.put("cameraCount", result.get("cameraCount"));
        message.put("supplyCount", result.get("supplyCount"));
        message.put("videoRoomId", result.get("videoRoomId"));
        message.put("startTime", result.get("startTime"));
        message.put("timestamp", System.currentTimeMillis());

        mqUtil.sendAsync(
                RocketMQConfig.buildDestination(MqConstant.EMERGENCY_COMMAND_TOPIC, MqConstant.TAG_EMERGENCY_PLAN_START),
                message
        );

        mqUtil.sendBroadcast(
                RocketMQConfig.buildDestination(MqConstant.EMERGENCY_COMMAND_TOPIC, MqConstant.TAG_EMERGENCY_PLAN_START),
                message
        );

        log.info("发送预案启动MQ事件成功，事件ID：{}，预案ID：{}", event.getId(), plan.getId());
    }

    private String generateCommandNo() {
        String dateStr = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        String random = String.format("%04d", new Random().nextInt(10000));
        return "CMD" + dateStr + random;
    }

    private String generateRoomId(Long eventId) {
        return "ROOM_" + eventId + "_" + System.currentTimeMillis();
    }

    private String generateRoomUrl(Long eventId, String roomId) {
        return "/emergency/video/" + eventId + "/" + roomId;
    }
}
