package com.example.retinavision.analysis.application;

import com.example.retinavision.analysis.application.command.ExecuteAnalysisTaskCommand;
import com.example.retinavision.analysis.application.model.ExecutionDisposition;

public interface ExecuteAnalysisTaskUseCase {

    ExecutionDisposition execute(ExecuteAnalysisTaskCommand command);
}
