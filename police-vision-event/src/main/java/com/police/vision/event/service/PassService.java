package com.police.vision.event.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.police.vision.common.entity.PageResult;
import com.police.vision.common.exception.BusinessException;
import com.police.vision.common.result.Result;
import com.police.vision.common.result.ResultCode;
import com.police.vision.common.util.SnowflakeIdUtil;
import com.police.vision.event.dto.PassCreateDTO;
import com.police.vision.event.dto.PassVerifyDTO;
import com.police.vision.event.entity.SecPass;
import com.police.vision.event.mapper.SecPassMapper;
import com.police.vision.event.util.QrCodeUtil;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class PassService {

    private final SecPassMapper secPassMapper;

    private static final String PASS_JWT_SECRET = "police-vision-pass-secret-key-2024-security-event-token-abcdef";
    private static final SecretKey PASS_SECRET_KEY = Keys.hmacShaKeyFor(PASS_JWT_SECRET.getBytes(StandardCharsets.UTF_8));
    private static final int PASS_STATUS_VALID = 1;
    private static final int PASS_STATUS_REVOKED = 2;
    private static final int DEFAULT_VALID_DAYS = 7;
    private static final int QR_CODE_SIZE = 300;
    private static final DateTimeFormatter PASS_NO_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    @Transactional(rollbackFor = Exception.class)
    public Result<Map<String, Object>> generatePass(PassCreateDTO dto) {
        log.info("生成电子通行证开始，eventId：{}，holderName：{}", dto.getEventId(), dto.getHolderName());
        try {
            int validDays = dto.getValidDays() != null ? dto.getValidDays() : DEFAULT_VALID_DAYS;

            Long passId = SnowflakeIdUtil.nextId();
            String passNo = generatePassNo();

            LocalDateTime issueTime = LocalDateTime.now();
            LocalDateTime expireTime = issueTime.plusDays(validDays);

            String jwtToken = generatePassJwtToken(
                    passId,
                    passNo,
                    dto.getHolderName(),
                    dto.getHolderIdcard(),
                    dto.getEventId(),
                    dto.getPassType(),
                    issueTime,
                    expireTime
            );

            String qrCodeBase64 = QrCodeUtil.generateQrCodeBase64(jwtToken, QR_CODE_SIZE, QR_CODE_SIZE);

            SecPass secPass = new SecPass();
            BeanUtils.copyProperties(dto, secPass);
            secPass.setId(passId);
            secPass.setPassNo(passNo);
            secPass.setJwtToken(jwtToken);
            secPass.setQrCode(qrCodeBase64);
            secPass.setIssueTime(issueTime);
            secPass.setExpireTime(expireTime);
            secPass.setStatus(PASS_STATUS_VALID);
            secPass.setVerifyCount(0);
            secPassMapper.insert(secPass);

            Map<String, Object> result = new HashMap<>();
            result.put("passId", passId);
            result.put("passNo", passNo);
            result.put("jwtToken", jwtToken);
            result.put("qrCodeBase64", qrCodeBase64);
            result.put("expireTime", expireTime);

            log.info("生成电子通行证成功，passId：{}，passNo：{}", passId, passNo);
            return Result.success(result);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("生成电子通行证失败", e);
            throw new BusinessException(ResultCode.DATABASE_ERROR, "生成电子通行证失败：" + e.getMessage());
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public Result<Map<String, Object>> verifyPass(PassVerifyDTO dto) {
        log.info("验证电子通行证开始");
        try {
            if (!StringUtils.hasText(dto.getJwtToken())) {
                throw new BusinessException(ResultCode.PARAM_ERROR, "JWT令牌不能为空");
            }

            Claims claims = parsePassJwtToken(dto.getJwtToken());
            if (claims == null) {
                return Result.success(buildVerifyResult(null, false, "JWT令牌无效或已过期", 0L, 0));
            }

            Long passId = claims.get("passId", Long.class);
            if (passId == null) {
                return Result.success(buildVerifyResult(null, false, "JWT令牌无效", 0L, 0));
            }

            SecPass secPass = secPassMapper.selectById(passId);
            if (secPass == null) {
                return Result.success(buildVerifyResult(null, false, "通行证不存在", 0L, 0));
            }

            return doVerifyPass(secPass);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("验证电子通行证失败", e);
            throw new BusinessException(ResultCode.FAIL, "验证电子通行证失败：" + e.getMessage());
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public Result<Map<String, Object>> verifyPassByQrCode(String qrCode) {
        log.info("通过二维码验证电子通行证开始");
        try {
            if (!StringUtils.hasText(qrCode)) {
                throw new BusinessException(ResultCode.PARAM_ERROR, "二维码内容不能为空");
            }

            SecPass secPass = null;

            try {
                Claims claims = parsePassJwtToken(qrCode);
                if (claims != null) {
                    Long passId = claims.get("passId", Long.class);
                    if (passId != null) {
                        secPass = secPassMapper.selectById(passId);
                    }
                }
            } catch (Exception e) {
                log.debug("二维码内容不是JWT令牌，尝试按passNo查询");
            }

            if (secPass == null) {
                LambdaQueryWrapper<SecPass> wrapper = new LambdaQueryWrapper<>();
                wrapper.eq(SecPass::getPassNo, qrCode);
                secPass = secPassMapper.selectOne(wrapper);
            }

            if (secPass == null) {
                LambdaQueryWrapper<SecPass> wrapper = new LambdaQueryWrapper<>();
                wrapper.eq(SecPass::getQrCode, qrCode);
                secPass = secPassMapper.selectOne(wrapper);
            }

            if (secPass == null) {
                return Result.success(buildVerifyResult(null, false, "通行证不存在", 0L, 0));
            }

            return doVerifyPass(secPass);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("通过二维码验证电子通行证失败", e);
            throw new BusinessException(ResultCode.FAIL, "通过二维码验证电子通行证失败：" + e.getMessage());
        }
    }

    public Result<PageResult<SecPass>> getPassList(Long eventId, String holderName, String status, int page, int size) {
        log.debug("分页查询通行证列表，eventId：{}，holderName：{}，status：{}", eventId, holderName, status);
        try {
            LambdaQueryWrapper<SecPass> wrapper = new LambdaQueryWrapper<>();
            if (eventId != null) {
                wrapper.eq(SecPass::getEventId, eventId);
            }
            if (StringUtils.hasText(holderName)) {
                wrapper.like(SecPass::getHolderName, holderName);
            }
            if (StringUtils.hasText(status)) {
                wrapper.eq(SecPass::getStatus, Integer.parseInt(status));
            }
            wrapper.orderByDesc(SecPass::getCreateTime);

            Page<SecPass> pageParam = new Page<>(page, size);
            IPage<SecPass> result = secPassMapper.selectPage(pageParam, wrapper);
            PageResult<SecPass> pageResult = PageResult.of(result.getTotal(), result.getRecords(), page, size);
            return Result.success(pageResult);
        } catch (Exception e) {
            log.error("分页查询通行证列表失败", e);
            throw new BusinessException(ResultCode.DATABASE_ERROR, "分页查询通行证列表失败：" + e.getMessage());
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public Result<Void> revokePass(Long passId) {
        log.info("作废电子通行证开始，passId：{}", passId);
        try {
            SecPass secPass = secPassMapper.selectById(passId);
            if (secPass == null) {
                throw new BusinessException(ResultCode.DATA_NOT_FOUND, "通行证不存在");
            }
            if (PASS_STATUS_REVOKED == secPass.getStatus()) {
                throw new BusinessException(ResultCode.PARAM_ERROR, "通行证已作废，无需重复操作");
            }
            secPass.setStatus(PASS_STATUS_REVOKED);
            secPassMapper.updateById(secPass);
            log.info("作废电子通行证成功，passId：{}", passId);
            return Result.success();
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("作废电子通行证失败，passId：{}", passId, e);
            throw new BusinessException(ResultCode.DATABASE_ERROR, "作废电子通行证失败：" + e.getMessage());
        }
    }

    public Result<SecPass> getPassDetail(Long passId) {
        log.debug("查询通行证详情，passId：{}", passId);
        try {
            SecPass secPass = secPassMapper.selectById(passId);
            if (secPass == null) {
                throw new BusinessException(ResultCode.DATA_NOT_FOUND, "通行证不存在");
            }
            return Result.success(secPass);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("查询通行证详情失败，passId：{}", passId, e);
            throw new BusinessException(ResultCode.DATABASE_ERROR, "查询通行证详情失败：" + e.getMessage());
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public Result<List<Map<String, Object>>> batchGeneratePass(List<PassCreateDTO> dtoList) {
        log.info("批量生成电子通行证开始，数量：{}", dtoList != null ? dtoList.size() : 0);
        try {
            if (dtoList == null || dtoList.isEmpty()) {
                throw new BusinessException(ResultCode.PARAM_ERROR, "通行证列表不能为空");
            }

            List<Map<String, Object>> resultList = new ArrayList<>();
            for (PassCreateDTO dto : dtoList) {
                Result<Map<String, Object>> result = generatePass(dto);
                if (result != null && result.getData() != null) {
                    resultList.add(result.getData());
                }
            }

            log.info("批量生成电子通行证成功，数量：{}", resultList.size());
            return Result.success(resultList);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("批量生成电子通行证失败", e);
            throw new BusinessException(ResultCode.DATABASE_ERROR, "批量生成电子通行证失败：" + e.getMessage());
        }
    }

    public Result<byte[]> downloadQrCode(Long passId) {
        log.info("下载通行证二维码开始，passId：{}", passId);
        try {
            SecPass secPass = secPassMapper.selectById(passId);
            if (secPass == null) {
                throw new BusinessException(ResultCode.DATA_NOT_FOUND, "通行证不存在");
            }

            String qrCodeBase64 = secPass.getQrCode();
            if (!StringUtils.hasText(qrCodeBase64)) {
                throw new BusinessException(ResultCode.FAIL, "二维码图片不存在");
            }

            byte[] qrCodeBytes = Base64.getDecoder().decode(qrCodeBase64);
            return Result.success(qrCodeBytes);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("下载通行证二维码失败，passId：{}", passId, e);
            throw new BusinessException(ResultCode.FAIL, "下载通行证二维码失败：" + e.getMessage());
        }
    }

    private String generatePassNo() {
        String timestamp = LocalDateTime.now().format(PASS_NO_FORMATTER);
        Random random = new Random();
        int randomNum = random.nextInt(1000000);
        return "PASS" + timestamp + String.format("%06d", randomNum);
    }

    private String generatePassJwtToken(Long passId, String passNo, String holderName, String holderIdcard,
                                        Long eventId, String passType,
                                        LocalDateTime issueTime, LocalDateTime expireTime) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("passId", passId);
        claims.put("passNo", passNo);
        claims.put("holderName", holderName);
        claims.put("holderIdcard", holderIdcard);
        claims.put("eventId", eventId);
        claims.put("passType", passType);

        Date issuedAt = Date.from(issueTime.atZone(ZoneId.systemDefault()).toInstant());
        Date expiration = Date.from(expireTime.atZone(ZoneId.systemDefault()).toInstant());

        return Jwts.builder()
                .claims(claims)
                .issuedAt(issuedAt)
                .expiration(expiration)
                .signWith(PASS_SECRET_KEY)
                .compact();
    }

    private Claims parsePassJwtToken(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(PASS_SECRET_KEY)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (ExpiredJwtException e) {
            log.warn("通行证Token已过期：{}", e.getMessage());
            return null;
        } catch (JwtException e) {
            log.warn("通行证Token无效：{}", e.getMessage());
            return null;
        }
    }

    private Result<Map<String, Object>> doVerifyPass(SecPass secPass) {
        LocalDateTime now = LocalDateTime.now();
        boolean isValid = true;
        String message = "验证通过";
        long remainingSeconds = 0;

        if (PASS_STATUS_VALID != secPass.getStatus()) {
            isValid = false;
            message = secPass.getStatus() == PASS_STATUS_REVOKED ? "通行证已作废" : "通行证状态异常";
        } else if (now.isAfter(secPass.getExpireTime())) {
            isValid = false;
            message = "通行证已过期";
        } else {
            Duration duration = Duration.between(now, secPass.getExpireTime());
            remainingSeconds = duration.getSeconds();
        }

        int verifyCount = secPass.getVerifyCount() == null ? 0 : secPass.getVerifyCount();
        if (isValid) {
            secPass.setVerifyCount(verifyCount + 1);
            secPassMapper.updateById(secPass);
            verifyCount = verifyCount + 1;
        }

        Map<String, Object> verifyResult = buildVerifyResult(secPass, isValid, message, remainingSeconds, verifyCount);
        return Result.success(verifyResult);
    }

    private Map<String, Object> buildVerifyResult(SecPass secPass, boolean isValid,
                                                   String message, Long remainingSeconds, int verifyCount) {
        Map<String, Object> result = new HashMap<>();
        result.put("valid", isValid);
        result.put("message", message);

        if (secPass != null) {
            result.put("passId", secPass.getId());
            result.put("passNo", secPass.getPassNo());
            result.put("holderName", secPass.getHolderName());
            result.put("holderIdcard", secPass.getHolderIdcard());
            result.put("holderPhone", secPass.getHolderPhone());
            result.put("passType", secPass.getPassType());
            result.put("eventId", secPass.getEventId());
            result.put("status", secPass.getStatus());
            result.put("verifyCount", verifyCount);
            result.put("issueTime", secPass.getIssueTime());
            result.put("expireTime", secPass.getExpireTime());
            result.put("remainingSeconds", remainingSeconds != null ? remainingSeconds : 0L);
        }
        return result;
    }
}
