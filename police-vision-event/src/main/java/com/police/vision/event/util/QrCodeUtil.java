package com.police.vision.event.util;

import com.google.zxing.*;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.common.HybridBinarizer;
import com.police.vision.common.exception.BusinessException;
import com.police.vision.common.result.ResultCode;
import lombok.extern.slf4j.Slf4j;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

@Slf4j
public class QrCodeUtil {

    private static final String CHARSET = "UTF-8";
    private static final String IMAGE_FORMAT = "PNG";

    private QrCodeUtil() {}

    public static String generateQrCodeBase64(String content, int width, int height) {
        try {
            Map<EncodeHintType, Object> hints = new HashMap<>();
            hints.put(EncodeHintType.CHARACTER_SET, CHARSET);
            hints.put(EncodeHintType.MARGIN, 1);
            hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M);

            BitMatrix bitMatrix = new MultiFormatWriter().encode(
                    content,
                    BarcodeFormat.QR_CODE,
                    width,
                    height,
                    hints
            );

            BufferedImage image = MatrixToImageWriter.toBufferedImage(bitMatrix);

            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            ImageIO.write(image, IMAGE_FORMAT, outputStream);

            byte[] imageBytes = outputStream.toByteArray();
            return Base64.getEncoder().encodeToString(imageBytes);
        } catch (WriterException e) {
            log.error("生成二维码失败，内容：{}", content, e);
            throw new BusinessException(ResultCode.FAIL, "生成二维码失败：" + e.getMessage());
        } catch (IOException e) {
            log.error("二维码图片编码失败", e);
            throw new BusinessException(ResultCode.FAIL, "二维码图片编码失败：" + e.getMessage());
        }
    }

    public static String decodeQrCode(BufferedImage image) {
        try {
            LuminanceSource source = new BufferedImageLuminanceSource(image);
            BinaryBitmap bitmap = new BinaryBitmap(new HybridBinarizer(source));

            Map<DecodeHintType, Object> hints = new HashMap<>();
            hints.put(DecodeHintType.CHARACTER_SET, CHARSET);

            Result result = new MultiFormatReader().decode(bitmap, hints);
            return result.getText();
        } catch (NotFoundException e) {
            log.warn("未识别到二维码内容", e);
            throw new BusinessException(ResultCode.PARAM_ERROR, "未识别到二维码内容");
        }
    }
}
