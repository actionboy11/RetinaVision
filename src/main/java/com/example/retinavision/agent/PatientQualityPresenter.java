package com.example.retinavision.agent;

import org.springframework.stereotype.Component;

import java.util.Locale;

@Component
public class PatientQualityPresenter {

    public Presentation present(String rawStatus, String rawReason) {
        String status = normalize(rawStatus);
        return switch (status) {
            case "NOT_CHECKED", "CHECKING" -> new Presentation(
                    PatientQualityDisplayStatus.CHECKING, "图像正在检查，请稍候");
            case "PASS" -> new Presentation(
                    PatientQualityDisplayStatus.ACCEPTABLE, "图像已接收，等待医生处理");
            case "WARNING", "FAIL" -> reupload(rawReason);
            default -> unavailable();
        };
    }

    private Presentation reupload(String rawReason) {
        String reason = normalize(rawReason);
        String message;
        if (containsAny(reason, "BLUR", "SHARPNESS", "FOCUS")) {
            message = "图像较模糊，建议重新上传";
        } else if (containsAny(reason, "FIELD", "CROP", "REGION", "INCOMPLETE")) {
            message = "拍摄区域不完整，建议重新上传";
        } else if (containsAny(reason, "BRIGHT", "DARK", "LIGHT", "EXPOSURE")) {
            message = "亮度不合适，建议重新上传";
        } else {
            return unavailable();
        }
        return new Presentation(PatientQualityDisplayStatus.REUPLOAD_RECOMMENDED, message);
    }

    private Presentation unavailable() {
        return new Presentation(PatientQualityDisplayStatus.UNAVAILABLE,
                "当前无法确认图像质量，请稍后重试或联系负责医生");
    }

    private boolean containsAny(String value, String... tokens) {
        for (String token : tokens) {
            if (value.contains(token)) return true;
        }
        return false;
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    public record Presentation(PatientQualityDisplayStatus status, String message) {
    }
}
