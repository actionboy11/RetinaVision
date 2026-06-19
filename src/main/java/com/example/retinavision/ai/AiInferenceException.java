package com.example.retinavision.ai;

public class AiInferenceException extends RuntimeException {
    private final AiFailureCategory category;

    public AiInferenceException(String message) {
        this(AiFailureCategory.UNKNOWN, message, null);
    }

    public AiInferenceException(String message, Throwable cause) {
        this(AiFailureCategory.UNKNOWN, message, cause);
    }

    public AiInferenceException(AiFailureCategory category, String message) {
        this(category, message, null);
    }

    public AiInferenceException(AiFailureCategory category, String message, Throwable cause) {
        super(message, cause);
        this.category = category;
    }

    public AiFailureCategory getCategory() {
        return category;
    }
}

