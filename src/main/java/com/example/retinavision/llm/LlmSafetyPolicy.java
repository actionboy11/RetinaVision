package com.example.retinavision.llm;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class LlmSafetyPolicy {
    private static final List<String> HIGH_RISK_TERMS = List.of(
            "诊断为", "确诊", "排除", "明确患有", "无需复查",
            "可以确诊", "肯定不是", "无需就医", "直接用药", "不需要医生"
    );
    private static final Map<PromptScenario, String> DISCLAIMERS = Map.of(
            PromptScenario.REPORT_DRAFT_GENERATION, "AI辅助分析，不等同于独立医学诊断。",
            PromptScenario.RAG_KNOWLEDGE_CHAT, "医疗知识助手仅供资料检索和理解参考，不构成诊断、治疗建议或报告签发依据。",
            PromptScenario.CASE_TREND_SUMMARY, "趋势分析仅供医生复核参考，不构成诊断、治疗建议或报告签发依据。",
            PromptScenario.CLINICAL_ASSISTANT_AGENT, "智能助手仅提供只读辅助查询，不构成诊断、治疗建议或报告签发依据。",
            PromptScenario.PATIENT_ASSISTANT_AGENT, "患者智能助手仅用于查看本人检查进度和理解已签发报告，不构成诊断或治疗建议。",
            PromptScenario.PATIENT_SIGNED_REPORT_EXPLANATION, "AI 通俗解释仅帮助理解医生已签发报告，不构成新的诊断或治疗建议。",
            PromptScenario.AGENT_SKILL_ROUTER, "Skill 路由只用于选择只读业务能力。"
    );

    public String disclaimer(PromptScenario scenario) {
        return DISCLAIMERS.get(scenario);
    }

    public void requireSafe(PromptScenario scenario, String... values) {
        for (String value : values) {
            if (value == null) {
                continue;
            }
            for (String term : HIGH_RISK_TERMS) {
                if (value.contains(term)) {
                    throw new LlmException(label(scenario) + "包含不合规诊断措辞，请调整提示词或重试");
                }
            }
        }
    }

    public String requireText(String value, int limit, String missingMessage) {
        if (value == null || value.isBlank()) {
            throw new LlmException(missingMessage);
        }
        return value.length() > limit ? value.substring(0, limit) : value;
    }

    private String label(PromptScenario scenario) {
        return switch (scenario) {
            case REPORT_DRAFT_GENERATION -> "AI 草稿";
            case RAG_KNOWLEDGE_CHAT -> "知识助手回答";
            case CASE_TREND_SUMMARY -> "AI 趋势摘要";
            case CLINICAL_ASSISTANT_AGENT, PATIENT_ASSISTANT_AGENT -> "智能助手回答";
            case PATIENT_SIGNED_REPORT_EXPLANATION -> "正式报告通俗解释";
            case AGENT_SKILL_ROUTER -> "Skill 路由结果";
        };
    }
}
