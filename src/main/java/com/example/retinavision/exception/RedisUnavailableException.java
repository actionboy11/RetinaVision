package com.example.retinavision.exception;

import com.example.retinavision.constant.ErrorMessageSignal;

public class RedisUnavailableException extends BaseException {
    public RedisUnavailableException(String message) {
        super(ErrorMessageSignal.SERVICE_UNAVAILABLE, message);
    }

    public RedisUnavailableException(String message, Throwable cause) {
        this(message);
        // 初始化异常链
        initCause(cause);
    }
}
