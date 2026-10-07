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

    @Test
    void fixesPatientSkillRolesModesAndArgumentsInJava() {
        assertThat(registry.executionMode(AgentSkillCode.MY_CASE_LIST))
                .isEqualTo(AgentSkillExecutionMode.DIRECT);
        assertThat(registry.executionMode(AgentSkillCode.MY_CASE_PROGRESS))
                .isEqualTo(AgentSkillExecutionMode.DIRECT);
        assertThat(registry.executionMode(AgentSkillCode.MY_SIGNED_REPORT))
                .isEqualTo(AgentSkillExecutionMode.DIRECT);
        assertThat(registry.executionMode(AgentSkillCode.PATIENT_KNOWLEDGE_QA))
                .isEqualTo(AgentSkillExecutionMode.TOOL_CALLING);

        assertThat(registry.isAvailable(AgentSkillCode.MY_CASE_LIST, UserRole.USER)).isTrue();
        assertThat(registry.isAvailable(AgentSkillCode.MY_CASE_LIST, UserRole.DOCTOR)).isFalse();
        assertThat(registry.isAvailable(AgentSkillCode.MY_CASE_LIST, UserRole.ADMIN)).isFalse();
        assertThat(registry.isAvailable(AgentSkillCode.PATIENT_KNOWLEDGE_QA, UserRole.USER)).isTrue();
        assertThat(registry.isAvailable(AgentSkillCode.PATIENT_KNOWLEDGE_QA, UserRole.DOCTOR)).isFalse();

        assertThat(registry.validateAndNormalize(AgentSkillCode.MY_CASE_LIST, UserRole.USER,
                Map.of("reuploadOnly", "TRUE", "signedReportOnly", "FALSE")))
                .containsExactlyInAnyOrderEntriesOf(Map.of(
                        "reuploadOnly", "TRUE", "signedReportOnly", "FALSE"));
        assertThat(registry.validateAndNormalize(AgentSkillCode.MY_SIGNED_REPORT, UserRole.USER,
                Map.of("caseReference", "C-20261007-001", "resultId", "91", "version", "2",
                        "mode", "EXPLAIN")))
                .containsEntry("mode", "EXPLAIN");

        assertThatThrownBy(() -> registry.validateAndNormalize(AgentSkillCode.MY_CASE_LIST,
                UserRole.USER, Map.of("pageSize", "100")))
                .hasMessageContaining("参数");
        assertThatThrownBy(() -> registry.validateAndNormalize(AgentSkillCode.MY_SIGNED_REPORT,
                UserRole.USER, Map.of("toolName", "deleteCase")))
                .hasMessageContaining("参数");
    }
}
