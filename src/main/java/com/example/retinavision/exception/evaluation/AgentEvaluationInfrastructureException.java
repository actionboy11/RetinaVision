package com.example.retinavision.exception.evaluation;

public class AgentEvaluationInfrastructureException extends RuntimeException {
    public AgentEvaluationInfrastructureException(String message) {
        super(message);
    }

    public AgentEvaluationInfrastructureException(String message, Throwable cause) {
        super(message, cause);
    }
}
