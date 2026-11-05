package com.example.retinavision.service.analysis;

import com.example.retinavision.pojo.analysis.command.ExecuteAnalysisTaskCommand;
import com.example.retinavision.pojo.analysis.dto.ExecutionDisposition;

public interface ExecuteAnalysisTaskUseCase {

    ExecutionDisposition execute(ExecuteAnalysisTaskCommand command);
}
