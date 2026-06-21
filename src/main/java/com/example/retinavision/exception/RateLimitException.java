package com.example.retinavision.exception;

import com.example.retinavision.constant.ErrorMessageSignal;

public class RateLimitException extends BaseException {

    private final long retryAfterSeconds;

    public RateLimitException(String message, long retryAfterSeconds) {
        super(ErrorMessageSignal.TOO_MANY_REQUESTS, message);
        this.retryAfterSeconds = Math.max(1, retryAfterSeconds);
    }

    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}
