package com.example.retinavision.agent;

import com.example.retinavision.enumeration.UserRole;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AgentSkillRegistryTest {

    private final AgentSkillRegistry registry = new AgentSkillRegistry();

    @Test
    void fixesExecutionModeAndRoleInJava() {
        assertThat(registry.executionMode(AgentSkillCode.DOCTOR_TASK_SEARCH))
                .isEqualTo(AgentSkillExecutionMode.DIRECT);
        assertThat(registry.executionMode(AgentSkillCode.DOCTOR_CLINICAL_QUEUE))
                .isEqualTo(AgentSkillExecutionMode.DIRECT);
        assertThat(registry.executionMode(AgentSkillCode.MEDICAL_KNOWLEDGE_QA))
                .isEqualTo(AgentSkillExecutionMode.TOOL_CALLING);
        assertThat(registry.isAvailable(AgentSkillCode.DOCTOR_TASK_SEARCH, UserRole.DOCTOR)).isTrue();
        assertThat(registry.isAvailable(AgentSkillCode.DOCTOR_TASK_SEARCH, UserRole.ADMIN)).isFalse();
    }

    @Test
    void normalizesTaskDefaultsAndRejectsIllegalArguments() {
        assertThat(registry.validateAndNormalize(AgentSkillCode.DOCTOR_TASK_SEARCH, UserRole.DOCTOR, Map.of()))
                .containsEntry("taskType", "VESSEL_SEGMENTATION")
                .containsEntry("status", "ANY")
                .containsEntry("dateWindow", "ANY");

        assertThat(registry.validateAndNormalize(AgentSkillCode.DOCTOR_TASK_SEARCH, UserRole.DOCTOR,
                Map.of("taskType", "IMAGE_QUALITY_CHECK")))
                .containsEntry("taskType", "IMAGE_QUALITY_CHECK");

        assertThatThrownBy(() -> registry.validateAndNormalize(AgentSkillCode.DOCTOR_TASK_SEARCH,
                UserRole.DOCTOR, Map.of("status", "DELETED")))
                .hasMessageContaining("参数");
        assertThatThrownBy(() -> registry.validateAndNormalize(AgentSkillCode.DOCTOR_TASK_SEARCH,
                UserRole.ADMIN, Map.of()))
                .hasMessageContaining("无权");
    }
}
