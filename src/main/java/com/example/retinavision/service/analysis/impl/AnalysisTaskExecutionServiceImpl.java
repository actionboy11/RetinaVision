package com.example.retinavision.service.analysis.impl;

import com.example.retinavision.service.analysis.ExecuteAnalysisTaskUseCase;
import com.example.retinavision.pojo.analysis.command.ExecuteAnalysisTaskCommand;
import com.example.retinavision.mq.analysis.AnalysisTaskMessageAdapter;
import com.example.retinavision.mq.analysis.AnalysisTaskMessage;
import com.example.retinavision.service.analysis.AnalysisTaskExecutionService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Service
public class AnalysisTaskExecutionServiceImpl implements AnalysisTaskExecutionService {

    private final ExecuteAnalysisTaskUseCase useCase;
    private final AnalysisTaskMessageAdapter messageAdapter;

    public AnalysisTaskExecutionServiceImpl(
            ExecuteAnalysisTaskUseCase useCase,
            AnalysisTaskMessageAdapter messageAdapter) {
        this.useCase = useCase;
        this.messageAdapter = messageAdapter;
    }

    @Override
    @Transactional
    public ExecutionDisposition process(AnalysisTaskMessage message) {
        if (message == null
                || message.getTaskId() == null
                || message.getTaskId() <= 0) {
            return ExecutionDisposition.IGNORED;
        }

        long taskId = message.getTaskId();
        String traceId = "task-" + taskId + "-" + UUID.randomUUID();
        Optional<ExecuteAnalysisTaskCommand> command =
                messageAdapter.toCommand(message, traceId);
        if (command.isEmpty()) {
            return ExecutionDisposition.IGNORED;
        }

        return ExecutionDisposition.valueOf(
                useCase.execute(command.orElseThrow()).name());
    }
}
