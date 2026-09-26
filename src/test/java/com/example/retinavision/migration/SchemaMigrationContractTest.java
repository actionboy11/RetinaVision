package com.example.retinavision.migration;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class SchemaMigrationContractTest {

    @Test
    void baselineMedicalLoopAnalysisOutboxAndPromptMigrationsArePackaged() throws IOException {
        String baseline = resource("/db/migration/V1__baseline_schema.sql");
        String hardening = resource("/db/migration/V2__medical_loop_hardening.sql");
        String outbox = resource("/db/migration/V5__analysis_outbox.sql");
        String prompts = resource("/db/migration/V6__prompt_engineering.sql");

        assertThat(baseline).contains("CREATE TABLE", "analysis_task", "analysis_result", "task_log");
        assertThat(hardening).contains("analysis_feedback", "analysis_correction", "analysis_review",
                "analysis_report", "quality_task_id");
        assertThat(hardening)
                .as("MySQL migrations must conditionally add columns through information_schema")
                .contains("information_schema.COLUMNS")
                .doesNotContain("ADD COLUMN IF NOT EXISTS");
        assertThat(outbox).contains(
                "CREATE TABLE analysis_outbox",
                "UNIQUE KEY uk_analysis_outbox_event_key (event_key)",
                "INDEX idx_analysis_outbox_poll (status, next_attempt_at, id)");
        assertThat(prompts).contains(
                "CREATE TABLE prompt_template",
                "CREATE TABLE prompt_template_version",
                "CREATE TABLE llm_call_log",
                "REPORT_DRAFT_GENERATION",
                "RAG_KNOWLEDGE_CHAT",
                "CASE_TREND_SUMMARY");
    }

    @Test
    void promptEvaluationMigrationKeepsSyntheticRunsAndReleaseState() throws IOException {
        String evaluation = resource("/db/migration/V7__prompt_evaluation.sql");
        assertThat(evaluation).contains("CREATE TABLE prompt_evaluation_run", "released_at",
                "doctor_decision", "baseline_version_id", "candidate_version_id");
    }

    @Test
    void ragCandidateMigrationStartsInactiveAndSnapshotsRetrievalConfiguration() throws IOException {
        String rag = resource("/db/migration/V8__rag_grounded_evaluation.sql");
        String configuration = resource("/db/migration/V9__rag_evaluation_configuration.sql");
        assertThat(rag).contains("RAG_KNOWLEDGE_CHAT", "evidence", "chunkId", "quote",
                "0\nFROM prompt_template");
        assertThat(configuration).contains("embedding_model", "score_threshold");
    }

    @Test
    void springAiAgentMigrationPackagesReadOnlyConversationAuditAndPrompt() throws IOException {
        String agent = resource("/db/migration/V10__spring_ai_agent.sql");
        assertThat(agent).contains(
                "CREATE TABLE agent_chat_session",
                "CREATE TABLE agent_chat_message",
                "CREATE TABLE agent_tool_call_log",
                "CLINICAL_ASSISTANT_AGENT",
                "只读工具");
    }

    @Test
    void assignmentEvaluationGovernanceAndAgentV2MigrationsArePackaged() throws IOException {
        String assignment = resource("/db/migration/V11__case_doctor_assignment.sql");
        String governance = resource("/db/migration/V12__evaluation_review_governance.sql");
        String agentV2 = resource("/db/migration/V13__clinical_agent_prompt_v2.sql");

        assertThat(assignment).contains("assigned_doctor_id", "idx_case_assigned_doctor_status");
        assertThat(governance).contains("review_decision", "review_score", "review_note");
        assertThat(agentV2).contains("CLINICAL_ASSISTANT_AGENT", "version = 2", "匿名患者编号");
    }

    @Test
    void patientIdentityMigrationKeepsLegacyCasesAndAddsWorkflowState() throws IOException {
        String patientIdentity = resource("/db/migration/V14__patient_identity_and_case_workflow.sql");

        assertThat(patientIdentity).contains(
                "CREATE TABLE patient_profile",
                "patient_no",
                "account_user_id",
                "legacy_patient_code",
                "ADD COLUMN patient_id",
                "ADD COLUMN workflow_status",
                "SELECT DISTINCT patient_code",
                "PATIENT_ASSISTANT_AGENT");
    }

    @Test
    void doctorAgentSkillMigrationAddsVersionedSkillsContextAuditAndStructuredMessages() throws IOException {
        String skills = resource("/db/migration/V15__doctor_agent_skills.sql");

        assertThat(skills).contains(
                "CREATE TABLE agent_skill",
                "CREATE TABLE agent_skill_version",
                "CREATE TABLE agent_query_context",
                "CREATE TABLE agent_skill_execution_log",
                "CREATE TABLE agent_skill_evaluation_run",
                "structured_content_json",
                "DOCTOR_WORKLOAD_OVERVIEW",
                "ASSIGNED_CASE_SEARCH",
                "CASE_CLINICAL_SUMMARY",
                "CASE_FOLLOWUP_ANALYSIS",
                "MEDICAL_KNOWLEDGE_QA");
    }

    @Test
    void agentSessionBindsEachSkillVersionIndependently() throws IOException {
        String binding = resource("/db/migration/V16__agent_session_skill_versions.sql");

        assertThat(binding).contains(
                "CREATE TABLE agent_session_skill_version",
                "UNIQUE KEY uk_agent_session_skill (session_id, skill_code)",
                "CONSTRAINT fk_agent_session_skill_binding_version",
                "FOREIGN KEY (skill_version_id) REFERENCES agent_skill_version(id)");
        assertThat(binding).doesNotContain("CONSTRAINT fk_agent_session_skill_version ");
    }

    private String resource(String path) throws IOException {
        try (InputStream input = getClass().getResourceAsStream(path)) {
            assertThat(input).as("migration resource %s", path).isNotNull();
            return new String(input.readAllBytes(), StandardCharsets.UTF_8)
                    .replace("\r\n", "\n");
        }
    }
}
