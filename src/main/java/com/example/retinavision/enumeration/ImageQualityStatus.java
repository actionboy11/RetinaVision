package com.example.retinavision.enumeration;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;
// 图像质量状态枚举
public enum ImageQualityStatus {
    NOT_CHECKED("NOT_CHECKED"), CHECKING("CHECKING"), PASS("PASS"),
    WARNING("WARNING"), FAIL("FAIL"), ERROR("ERROR");

    @EnumValue
    private final String code;

    ImageQualityStatus(String code) {
        this.code = code;
    }

    @JsonValue
    public String getCode() { return code; }
}
