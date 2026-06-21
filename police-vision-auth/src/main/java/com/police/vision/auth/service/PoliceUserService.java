package com.police.vision.auth.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.police.vision.auth.entity.PoliceUser;
import com.police.vision.auth.mapper.PoliceUserMapper;
import com.police.vision.common.entity.PageParam;
import com.police.vision.common.entity.PageResult;
import com.police.vision.common.exception.BusinessException;
import com.police.vision.common.result.ResultCode;
import io.seata.spring.annotation.GlobalTransactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class PoliceUserService {

    private final PoliceUserMapper policeUserMapper;
    private final PasswordEncoder passwordEncoder;

    public PageResult<PoliceUser> getPoliceList(PageParam param, Long deptId, String keyword, Integer status) {
        Page<PoliceUser> page = new Page<>(param.getPageNum(), param.getPageSize());
        Page<PoliceUser> resultPage = policeUserMapper.selectPoliceList(page, param, deptId, keyword, status);
        return PageResult.of(resultPage.getTotal(), resultPage.getRecords(), param.getPageNum(), param.getPageSize());
    }

    public PoliceUser getPoliceById(Long id) {
        PoliceUser user = policeUserMapper.selectById(id);
        if (user == null) {
            throw new BusinessException(ResultCode.USER_NOT_EXIST);
        }
        user.setPassword(null);
        return user;
    }

    public PoliceUser getPoliceByNo(String policeNo) {
        return policeUserMapper.selectByPoliceNo(policeNo);
    }

    @GlobalTransactional(rollbackFor = Exception.class)
    public PoliceUser createPolice(PoliceUser user) {
        PoliceUser existUser = policeUserMapper.selectByPoliceNo(user.getPoliceNo());
        if (existUser != null) {
            throw new BusinessException(ResultCode.USER_EXIST);
        }
        if (StringUtils.hasText(user.getPassword())) {
            user.setPassword(passwordEncoder.encode(user.getPassword()));
        } else {
            user.setPassword(passwordEncoder.encode("123456"));
        }
        user.setStatus(1);
        policeUserMapper.insert(user);
        log.info("创建警员成功：policeNo={}, name={}", user.getPoliceNo(), user.getName());
        return user;
    }

    @GlobalTransactional(rollbackFor = Exception.class)
    public PoliceUser updatePolice(PoliceUser user) {
        PoliceUser existUser = policeUserMapper.selectById(user.getId());
        if (existUser == null) {
            throw new BusinessException(ResultCode.USER_NOT_EXIST);
        }
        if (StringUtils.hasText(user.getPassword())) {
            user.setPassword(passwordEncoder.encode(user.getPassword()));
        } else {
            user.setPassword(null);
        }
        policeUserMapper.updateById(user);
        log.info("更新警员信息成功：id={}, policeNo={}", user.getId(), user.getPoliceNo());
        return policeUserMapper.selectById(user.getId());
    }

    @GlobalTransactional(rollbackFor = Exception.class)
    public void deletePolice(Long id) {
        PoliceUser user = policeUserMapper.selectById(id);
        if (user == null) {
            throw new BusinessException(ResultCode.USER_NOT_EXIST);
        }
        policeUserMapper.deleteById(id);
        log.info("删除警员成功：id={}, policeNo={}", id, user.getPoliceNo());
    }

    public void updatePoliceStatus(Long id, Integer status) {
        PoliceUser user = new PoliceUser();
        user.setId(id);
        user.setStatus(status);
        policeUserMapper.updateById(user);
        log.info("更新警员状态成功：id={}, status={}", id, status);
    }

    public void updatePoliceLocation(Long id, java.math.BigDecimal longitude, java.math.BigDecimal latitude) {
        PoliceUser user = new PoliceUser();
        user.setId(id);
        user.setLongitude(longitude);
        user.setLatitude(latitude);
        policeUserMapper.updateById(user);
    }

    public List<PoliceUser> getPoliceByDeptId(Long deptId) {
        return policeUserMapper.selectByDeptId(deptId);
    }

    public List<PoliceUser> getAvailablePolice() {
        LambdaQueryWrapper<PoliceUser> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PoliceUser::getStatus, 1)
                .isNotNull(PoliceUser::getLongitude)
                .isNotNull(PoliceUser::getLatitude)
                .last("LIMIT 500");
        return policeUserMapper.selectList(wrapper);
    }

    public List<String> getRolesByUserId(Long userId) {
        return policeUserMapper.selectRoleCodesByUserId(userId);
    }

    public List<String> getPermissionsByUserId(Long userId) {
        return policeUserMapper.selectPermissionCodesByUserId(userId);
    }
}
