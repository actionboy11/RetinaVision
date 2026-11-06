package com.example.retinavision.pojo.agent.vo;

import com.example.retinavision.pojo.agent.AgentAction;
import com.example.retinavision.pojo.agent.AgentPagination;
import com.example.retinavision.pojo.agent.AgentStructuredData;

import com.example.retinavision.pojo.agent.AgentAction;
import com.example.retinavision.pojo.agent.AgentPagination;
import com.example.retinavision.pojo.agent.AgentStructuredData;

import java.util.List;

public record AgentChatResponseVO(Long sessionId, String answer,
                                  AgentSkillSummaryVO skill,
                                  AgentStructuredData data,
                                  AgentPagination pagination,
                                  List<AgentAction> actions,
                                  List<AgentToolCallSummaryVO> toolCalls,
                                  List<AgentCitationVO> citations,
                                  String disclaimer) {
    public AgentChatResponseVO(Long sessionId, String answer,
                               List<AgentToolCallSummaryVO> toolCalls,
                               List<AgentCitationVO> citations,
                               String disclaimer) {
        this(sessionId, answer, null, null, null, List.of(), toolCalls, citations, disclaimer);
    }
}
