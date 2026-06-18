package com.example.retinavision.ai;

public class AiInferenceException extends RuntimeException {
    public AiInferenceException(String message) {
        super(message);
    }

    public AiInferenceException(String message, Throwable cause) {
        super(message, cause);
    }
}

