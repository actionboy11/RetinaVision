package com.example.retinavision.mq.analysis;

import com.example.retinavision.pojo.analysis.command.ExecuteAnalysisTaskCommand;
import com.example.retinavision.mq.analysis.AnalysisTaskMessage;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public final class AnalysisTaskMessageAdapter {

    public Optional<ExecuteAnalysisTaskCommand> toCommand(
            AnalysisTaskMessage message,
            String traceId) {
        if (message == null
                || message.getTaskId() == null
                || message.getTaskId() <= 0) {
            return Optional.empty();
        }
        return Optional.of(new ExecuteAnalysisTaskCommand(
                message.getTaskId(),
                traceId));
    }
}
