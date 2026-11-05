package com.example.retinavision.service.analysis.port;

import com.example.retinavision.pojo.analysis.event.AnalysisTaskRequestedEvent;

public interface AnalysisTaskEventOutbox {

    void append(
            AnalysisTaskRequestedEvent event,
            String eventType,
            int eventVersion);
}
