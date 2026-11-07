package com.example.retinavision.exception.llm;

import com.example.retinavision.constant.shared.ErrorMessageSignal;

import com.example.retinavision.exception.shared.BaseException;

public class LlmException extends BaseException {
    public LlmException(String message) {
        super(ErrorMessageSignal.AI_TASK_ERROR, message);
    }

    public LlmException(String message, Throwable cause) {
        super(ErrorMessageSignal.AI_TASK_ERROR, message);
        initCause(cause);
    }
}
