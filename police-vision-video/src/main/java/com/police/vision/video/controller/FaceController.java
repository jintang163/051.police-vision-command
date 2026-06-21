package com.police.vision.video.controller;

import com.alibaba.fastjson2.JSON;
import com.police.vision.common.result.Result;
import com.police.vision.video.entity.FaceRecord;
import com.police.vision.video.entity.TargetPerson;
import com.police.vision.video.service.FaceRecognitionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@Tag(name = "人脸识别", description = "人脸特征提取、以图搜图、重点人员布控")
@RestController
@RequestMapping("/face")
@RequiredArgsConstructor
public class FaceController {

    private final FaceRecognitionService faceRecognitionService;

    @Operation(summary = "提取人脸特征向量")
    @PostMapping("/feature")
    public Result<float[]> extractFaceFeature(@RequestParam("image") MultipartFile image) throws IOException {
        float[] feature = faceRecognitionService.extractFaceFeature(image.getBytes());
        return Result.success(feature);
    }

    @Operation(summary = "以图搜图 - 人脸检索")
    @PostMapping("/search")
    public Result<List<Map<String, Object>>> searchFace(
            @RequestParam("image") MultipartFile image,
            @Parameter(description = "相似度阈值，默认0.8") @RequestParam(required = false, defaultValue = "0.8") float threshold) throws IOException {
        float[] feature = faceRecognitionService.extractFaceFeature(image.getBytes());
        List<Map<String, Object>> results = faceRecognitionService.searchFaceByFeature(feature, threshold);
        return Result.success(results);
    }

    @Operation(summary = "按特征向量检索人脸")
    @PostMapping("/search/feature")
    public Result<List<Map<String, Object>>> searchFaceByFeature(
            @RequestBody Map<String, Object> request,
            @Parameter(description = "相似度阈值，默认0.8") @RequestParam(required = false, defaultValue = "0.8") float threshold) {
        List<Float> featureList = (List<Float>) request.get("feature");
        float[] feature = new float[featureList.size()];
        for (int i = 0; i < featureList.size(); i++) {
            feature[i] = featureList.get(i);
        }
        List<Map<String, Object>> results = faceRecognitionService.searchFaceByFeature(feature, threshold);
        return Result.success(results);
    }

    @Operation(summary = "添加重点人员布控")
    @PostMapping("/target")
    public Result<Void> addTargetPerson(
            @RequestParam("image") MultipartFile image,
            @RequestParam("personName") String personName,
            @RequestParam("idCardNo") String idCardNo,
            @RequestParam("controlLevel") Integer controlLevel,
            @RequestParam(required = false) String remark) throws IOException {
        float[] feature = faceRecognitionService.extractFaceFeature(image.getBytes());
        TargetPerson person = new TargetPerson();
        person.setPersonName(personName);
        person.setIdCardNo(idCardNo);
        person.setFaceFeature(JSON.toJSONString(feature));
        person.setControlLevel(controlLevel);
        person.setStatus(1);
        person.setRemark(remark);
        faceRecognitionService.addFaceToTarget(person);
        return Result.success();
    }

    @Operation(summary = "更新重点人员布控")
    @PutMapping("/target")
    public Result<Void> updateTargetPerson(@RequestBody TargetPerson person) {
        faceRecognitionService.addFaceToTarget(person);
        return Result.success();
    }

    @Operation(summary = "获取重点人员详情")
    @GetMapping("/target/{personId}")
    public Result<TargetPerson> getTargetPerson(@PathVariable String personId) {
        return Result.success(faceRecognitionService.getTargetPerson(personId));
    }

    @Operation(summary = "获取重点人员列表")
    @GetMapping("/target/list")
    public Result<List<TargetPerson>> getTargetPersonList(
            @Parameter(description = "状态：0停用 1启用") @RequestParam(required = false, defaultValue = "1") Integer status) {
        return Result.success(faceRecognitionService.getTargetPersonByStatus(status));
    }

    @Operation(summary = "处理人脸匹配记录")
    @PostMapping("/match")
    public Result<Void> handleFaceMatch(@RequestBody FaceRecord record) {
        faceRecognitionService.handleFaceMatch(record);
        return Result.success();
    }

    @Operation(summary = "保存人脸识别记录")
    @PostMapping("/record")
    public Result<Void> saveFaceRecord(@RequestBody FaceRecord record) {
        faceRecognitionService.saveFaceRecord(record);
        return Result.success();
    }
}
