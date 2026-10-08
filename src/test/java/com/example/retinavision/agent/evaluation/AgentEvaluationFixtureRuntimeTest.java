package com.example.retinavision.agent.evaluation;

import com.example.retinavision.agent.*;
import com.example.retinavision.enumeration.UserRole;
import com.example.retinavision.exception.BaseException;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AgentEvaluationFixtureRuntimeTest {

    @Test
    void reusesDoctorOrchestratorForPaginationAndOrdinalContext() {
        AgentSkillRouter router = (question, current) -> {
            if (question.contains("摘要")) return route(AgentSkillCode.CASE_CLINICAL_SUMMARY, Map.of());
            return route(AgentSkillCode.ASSIGNED_CASE_SEARCH, Map.of());
        };
        AgentEvaluationFixtureRuntime runtime = new AgentEvaluationFixtureRuntime(router, Map.of());

        AgentEvaluationExecution first = runtime.execute(77L, UserRole.DOCTOR, "查询我负责的病例");
        AgentEvaluationExecution next = runtime.execute(77L, UserRole.DOCTOR, "下一页");
        AgentEvaluationExecution selected = runtime.execute(77L, UserRole.DOCTOR, "查看第二个病例");

        assertThat(first.data().type()).isEqualTo("CASE_LIST");
        assertThat(first.pagination().page()).isEqualTo(1);
        assertThat(next.pagination().page()).isEqualTo(2);
        assertThat(selected.data().type()).isEqualTo("CASE_DETAIL");
        assertThat(selected.actualSkill()).isEqualTo(AgentSkillCode.CASE_CLINICAL_SUMMARY);
    }

    @Test
    void returnsFixedAudienceScopedCitationForKnowledgeSkills() {
        AgentSkillRouter router = (question, current) -> question.contains("患者")
                ? route(AgentSkillCode.PATIENT_KNOWLEDGE_QA, Map.of())
                : route(AgentSkillCode.MEDICAL_KNOWLEDGE_QA, Map.of());
        AgentEvaluationFixtureRuntime runtime = new AgentEvaluationFixtureRuntime(router, Map.of());

        AgentEvaluationExecution doctor = runtime.execute(1L, UserRole.DOCTOR, "黄斑是什么");
        AgentEvaluationExecution patient = runtime.execute(2L, UserRole.USER, "患者如何准备检查");

        assertThat(doctor.citations()).singleElement().satisfies(citation ->
                assertThat(citation.chunkId()).isEqualTo("EVAL-CHUNK-DOCTOR-001"));
        assertThat(patient.citations()).singleElement().satisfies(citation ->
                assertThat(citation.chunkId()).isEqualTo("EVAL-CHUNK-PATIENT-001"));
    }

    @Test
    void rejectsCrossRoleSkillBeforeFixtureQueryExecution() {
        AgentEvaluationFixtureRuntime runtime = new AgentEvaluationFixtureRuntime(
                (question, current) -> route(AgentSkillCode.DOCTOR_WORKLOAD_OVERVIEW, Map.of()), Map.of());

        assertThatThrownBy(() -> runtime.execute(2L, UserRole.USER, "查看医生工作量"))
                .isInstanceOf(BaseException.class)
                .hasMessageContaining("无权");
    }

    private AgentSkillRoute route(AgentSkillCode code, Map<String, String> arguments) {
        return new AgentSkillRoute(code, 0.99, arguments);
    }
}
