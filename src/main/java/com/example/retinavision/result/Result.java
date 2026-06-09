package com.example.retinavision.result;

import lombok.Data;

import java.io.Serializable;

/**
 * Backend unified API response.
 *
 * @param <T> response data type
 */
@Data
public class Result<T> implements Serializable {
    // 前端 ApiResponse 约定 code=0 表示成功。
    private Integer code;
    // 前端统一读取 message 字段展示错误信息，不能返回 msg。
    private String message;
    private T data;

    public static <T> Result<T> success() {
        Result<T> result = new Result<T>();
        result.code = 0;
        result.message = "success";
        return result;
    }

    public static <T> Result<T> success(T object) {
        Result<T> result = new Result<T>();
        result.data = object;
        result.code = 0;
        result.message = "success";
        return result;
    }

    public static <T> Result<T> error(String message) {
        Result<T> result = new Result<T>();
        result.message = message;
        result.code = 1;
        return result;
    }
}
