package com.police.vision.event.service;

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
import com.police.vision.event.dto.PostDTO;
import com.police.vision.event.dto.SecurityPlanCreateDTO;
import com.police.vision.event.dto.TaskGroupDTO;
import com.police.vision.event.entity.SecPost;
import com.police.vision.event.entity.SecSecurityPlan;
import com.police.vision.event.entity.SecTaskGroup;
import com.police.vision.event.mapper.SecPostMapper;
import com.police.vision.event.mapper.SecSecurityPlanMapper;
import com.police.vision.event.mapper.SecTaskGroupMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class SecurityPlanService {

    private final SecSecurityPlanMapper secSecurityPlanMapper;
    private final SecTaskGroupMapper secTaskGroupMapper;
    private final SecPostMapper secPostMapper;
    private final MqUtil mqUtil;

    @Transactional(rollbackFor = Exception.class)
    public SecSecurityPlan createPlan(SecurityPlanCreateDTO dto) {
        SecSecurityPlan plan = new SecSecurityPlan();
        BeanUtils.copyProperties(dto, plan);
        plan.setId(SnowflakeIdUtil.nextId());
        plan.setStatus(0);
        secSecurityPlanMapper.insert(plan);
        log.info("创建安保方案成功，planId: {}", plan.getId());

        if (dto.getTaskGroups() != null && !dto.getTaskGroups().isEmpty()) {
            for (TaskGroupDTO groupDTO : dto.getTaskGroups()) {
                SecTaskGroup taskGroup = new SecTaskGroup();
                BeanUtils.copyProperties(groupDTO, taskGroup);
                taskGroup.setId(SnowflakeIdUtil.nextId());
                taskGroup.setPlanId(plan.getId());
                secTaskGroupMapper.insert(taskGroup);
                log.info("创建任务组成功，groupId: {}, planId: {}", taskGroup.getId(), plan.getId());

                if (groupDTO.getPosts() != null && !groupDTO.getPosts().isEmpty()) {
                    for (PostDTO postDTO : groupDTO.getPosts()) {
                        SecPost post = new SecPost();
                        BeanUtils.copyProperties(postDTO, post);
                        post.setId(SnowflakeIdUtil.nextId());
                        post.setPlanId(plan.getId());
                        post.setGroupId(taskGroup.getId());
                        secPostMapper.insert(post);
                    }
                    log.info("创建岗位成功，groupId: {}, 岗位数量: {}", taskGroup.getId(), groupDTO.getPosts().size());
                }
            }
        }
        return plan;
    }

    @Transactional(rollbackFor = Exception.class)
    public void publishPlan(Long planId) {
        SecSecurityPlan plan = getPlanById(planId);
        if (plan.getStatus() != 0) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "仅草稿状态的方案可发布");
        }
        plan.setStatus(1);
        secSecurityPlanMapper.updateById(plan);
        log.info("发布安保方案成功，planId: {}", planId);
    }

    @Transactional(rollbackFor = Exception.class)
    public void executePlan(Long planId) {
        SecSecurityPlan plan = getPlanById(planId);
        if (plan.getStatus() != 1) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "仅已发布状态的方案可执行");
        }
        plan.setStatus(2);
        secSecurityPlanMapper.updateById(plan);

        Map<String, Object> taskMessage = new HashMap<>();
        taskMessage.put("planId", planId);
        taskMessage.put("eventId", plan.getEventId());
        taskMessage.put("planName", plan.getPlanName());
        taskMessage.put("executeTime", System.currentTimeMillis());
        mqUtil.sendAsync(RocketMQConfig.buildDestination(MqConstant.DISPATCH_TOPIC, "security_plan_execute"), taskMessage);
        log.info("执行安保方案并下发任务消息，planId: {}", planId);
    }

    @Transactional(rollbackFor = Exception.class)
    public void archivePlan(Long planId) {
        SecSecurityPlan plan = getPlanById(planId);
        if (plan.getStatus() != 2) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "仅执行中状态的方案可归档");
        }
        plan.setStatus(3);
        secSecurityPlanMapper.updateById(plan);
        log.info("归档安保方案成功，planId: {}", planId);
    }

    public Map<String, Object> getPlanDetail(Long planId) {
        SecSecurityPlan plan = getPlanById(planId);
        Map<String, Object> result = new HashMap<>();
        result.put("id", plan.getId());
        result.put("eventId", plan.getEventId());
        result.put("planName", plan.getPlanName());
        result.put("planType", plan.getPlanType());
        result.put("status", plan.getStatus());
        result.put("createTime", plan.getCreateTime());
        result.put("updateTime", plan.getUpdateTime());

        LambdaQueryWrapper<SecTaskGroup> groupWrapper = new LambdaQueryWrapper<>();
        groupWrapper.eq(SecTaskGroup::getPlanId, planId);
        groupWrapper.orderByAsc(SecTaskGroup::getCreateTime);
        List<SecTaskGroup> taskGroups = secTaskGroupMapper.selectList(groupWrapper);

        LambdaQueryWrapper<SecPost> postWrapper = new LambdaQueryWrapper<>();
        postWrapper.eq(SecPost::getPlanId, planId);
        postWrapper.orderByAsc(SecPost::getCreateTime);
        List<SecPost> allPosts = secPostMapper.selectList(postWrapper);
        Map<Long, List<SecPost>> postGroupMap = allPosts.stream()
                .collect(Collectors.groupingBy(SecPost::getGroupId));

        List<Map<String, Object>> taskGroupList = new ArrayList<>();
        for (SecTaskGroup group : taskGroups) {
            Map<String, Object> groupMap = new HashMap<>();
            groupMap.put("id", group.getId());
            groupMap.put("planId", group.getPlanId());
            groupMap.put("groupName", group.getGroupName());
            groupMap.put("groupLeader", group.getGroupLeader());
            groupMap.put("groupLeaderId", group.getGroupLeaderId());
            groupMap.put("description", group.getDescription());
            groupMap.put("posts", postGroupMap.getOrDefault(group.getId(), new ArrayList<>()));
            taskGroupList.add(groupMap);
        }
        result.put("taskGroups", taskGroupList);
        return result;
    }

    public PageResult<SecSecurityPlan> listPlans(Long eventId, Integer status, int page, int size) {
        LambdaQueryWrapper<SecSecurityPlan> wrapper = new LambdaQueryWrapper<>();
        if (eventId != null) {
            wrapper.eq(SecSecurityPlan::getEventId, eventId);
        }
        if (status != null) {
            wrapper.eq(SecSecurityPlan::getStatus, status);
        }
        wrapper.orderByDesc(SecSecurityPlan::getCreateTime);

        Page<SecSecurityPlan> pageParam = new Page<>(page, size);
        IPage<SecSecurityPlan> result = secSecurityPlanMapper.selectPage(pageParam, wrapper);
        return PageResult.of(result.getTotal(), result.getRecords(), page, size);
    }

    @Transactional(rollbackFor = Exception.class)
    public void deletePlan(Long planId) {
        SecSecurityPlan plan = getPlanById(planId);
        if (plan.getStatus() == 2) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "执行中的方案不可删除");
        }

        LambdaQueryWrapper<SecPost> postWrapper = new LambdaQueryWrapper<>();
        postWrapper.eq(SecPost::getPlanId, planId);
        secPostMapper.delete(postWrapper);

        LambdaQueryWrapper<SecTaskGroup> groupWrapper = new LambdaQueryWrapper<>();
        groupWrapper.eq(SecTaskGroup::getPlanId, planId);
        secTaskGroupMapper.delete(groupWrapper);

        secSecurityPlanMapper.deleteById(planId);
        log.info("删除安保方案成功，planId: {}", planId);
    }

    private SecSecurityPlan getPlanById(Long planId) {
        SecSecurityPlan plan = secSecurityPlanMapper.selectById(planId);
        if (plan == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "安保方案不存在");
        }
        return plan;
    }
}
