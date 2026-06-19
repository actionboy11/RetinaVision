package com.example.retinavision.ai;

public enum AiFailureCategory {
    CONNECTION_FAILED,
    READ_TIMEOUT,
    AI_4XX,
    AI_5XX,
    INCOMPLETE_RESPONSE,
    UNTRUSTED_ARTIFACT_URL,
    ARTIFACT_DOWNLOAD_FAILED,
    UNKNOWN
}
