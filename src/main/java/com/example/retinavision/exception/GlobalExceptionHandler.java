package com.example.retinavision.exception;

import com.example.retinavision.constant.ErrorMessageContant;
import com.example.retinavision.constant.ErrorMessageSignal;
import com.example.retinavision.result.Result;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@RestControllerAdvice // 全局异常处理
public class GlobalExceptionHandler {

    // 处理自定义的 RateLimitException，返回 429 状态码和重试时间间隔
    //ResponseEntity 可以灵活设置 HTTP 状态码和响应头，适合需要返回特定状态码的异常处理。
    @ExceptionHandler(RateLimitException.class)
    public ResponseEntity<Result<Boolean>> handleRateLimitException(RateLimitException exception) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header(HttpHeaders.RETRY_AFTER, String.valueOf(exception.getRetryAfterSeconds()))
                .body(Result.error(exception.getCode(), exception.getMessage()));
    }

    // 处理自定义的 RedisUnavailableException，返回 503 状态码
    @ExceptionHandler(RedisUnavailableException.class)
    public ResponseEntity<Result<Boolean>> handleRedisUnavailableException(RedisUnavailableException exception) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(Result.error(exception.getCode(), exception.getMessage()));
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<Result<Boolean>> handleConflictException(ConflictException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Result.error(exception.getCode(), exception.getMessage()));
    }

    @ExceptionHandler(BaseException.class)   // 处理 BaseException 及其子类
    // 优先级高：精确匹配 BaseException 及其子类
    public Result<Boolean> handleBaseException(BaseException exception) {
        // 业务异常统一返回前端约定的 JSON，避免注册页直接显示 HTTP 500。
        return Result.error(exception.getCode(), exception.getMessage());
    }

    // 优先级低：兜底处理所有其他异常
    @ExceptionHandler(Exception.class)   // 处理 Exception 及其子类
    public Result<Boolean> handleException(Exception exception) {
        // 兜底异常也返回统一 JSON，便于前端展示错误信息；后端日志仍可用于定位真实原因。
        exception.printStackTrace();
        return Result.error(ErrorMessageSignal.SERVER_ERROR, ErrorMessageContant.SERVER_ERROR_MSG);
    }
}
