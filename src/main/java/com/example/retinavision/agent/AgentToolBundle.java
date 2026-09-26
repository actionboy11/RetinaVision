package com.example.retinavision.agent;

import com.example.retinavision.pojo.VO.AgentCitationVO;
import com.example.retinavision.pojo.VO.AgentToolCallSummaryVO;
import org.springframework.ai.tool.ToolCallback;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class AgentToolBundle {
    private ToolCallback[] callbacks;
    private final List<AgentToolCallSummaryVO> summaries = new ArrayList<>();
    private final List<AgentCitationVO> citations = new ArrayList<>();

    public AgentToolBundle(ToolCallback[] callbacks) {
        this.callbacks = callbacks;
    }

    public ToolCallback[] callbacks() {
        return callbacks;
    }

    public void setCallbacks(ToolCallback[] callbacks) {
        this.callbacks = callbacks;
    }

    public List<AgentToolCallSummaryVO> summaries() {
        return Collections.unmodifiableList(summaries);
    }

    public List<AgentCitationVO> citations() {
        return Collections.unmodifiableList(citations);
    }

    public void addSummary(AgentToolCallSummaryVO summary) {
        summaries.add(summary);
    }

    public void addCitation(AgentCitationVO citation) {
        citations.add(citation);
    }
}
