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

    private String resource(String path) throws IOException {
        try (InputStream input = getClass().getResourceAsStream(path)) {
            assertThat(input).as("migration resource %s", path).isNotNull();
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
