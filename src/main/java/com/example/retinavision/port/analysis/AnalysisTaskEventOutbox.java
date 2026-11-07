package com.example.retinavision.port.analysis;

import com.example.retinavision.pojo.analysis.event.AnalysisTaskRequestedEvent;

public interface AnalysisTaskEventOutbox {

    void append(
            AnalysisTaskRequestedEvent event,
            String eventType,
            int eventVersion);
}
