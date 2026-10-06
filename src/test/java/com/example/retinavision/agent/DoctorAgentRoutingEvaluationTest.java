package com.example.retinavision.agent;

import com.example.retinavision.enumeration.UserRole;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DoctorAgentRoutingEvaluationTest {
    @Test
    void runtimeRegistryKeepsDirectAndToolCallingBoundariesExplicit() {
        AgentSkillRegistry registry = new AgentSkillRegistry();

        assertThat(registry.executionMode(AgentSkillCode.DOCTOR_TASK_SEARCH))
                .isEqualTo(AgentSkillExecutionMode.DIRECT);
        assertThat(registry.executionMode(AgentSkillCode.DOCTOR_CLINICAL_QUEUE))
                .isEqualTo(AgentSkillExecutionMode.DIRECT);
        assertThat(registry.executionMode(AgentSkillCode.MEDICAL_KNOWLEDGE_QA))
                .isEqualTo(AgentSkillExecutionMode.TOOL_CALLING);
        assertThat(registry.validateAndNormalize(AgentSkillCode.DOCTOR_TASK_SEARCH, UserRole.DOCTOR, Map.of()))
                .containsEntry("taskType", "VESSEL_SEGMENTATION")
                .containsEntry("status", "ANY")
                .containsEntry("dateWindow", "ANY");
    }

    @Test
    void candidateCatalogDefinitionCarriesVersionedRoutingMaterial() {
        AgentSkillDefinition candidate = new AgentSkillDefinition(AgentSkillCode.DOCTOR_TASK_SEARCH,
                "分析任务查询", "查询医生本人患者的分析任务", 2,
                "[\"查询失败任务\"]", "只读查询并返回结构化任务卡片");

        assertThat(List.of(candidate)).singleElement().satisfies(definition -> {
            assertThat(definition.version()).isEqualTo(2);
            assertThat(definition.routingExamplesJson()).contains("查询失败任务");
            assertThat(definition.workflowPrompt()).contains("只读");
        });
    }
}
