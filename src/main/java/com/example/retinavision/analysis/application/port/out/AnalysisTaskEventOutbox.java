package com.example.retinavision.analysis.application.port.out;

import com.example.retinavision.analysis.application.model.AnalysisTaskRequestedEvent;

public interface AnalysisTaskEventOutbox {

    void append(
            AnalysisTaskRequestedEvent event,
            String eventType,
            int eventVersion);
}
