package com.example.retinavision.migration;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class SchemaMigrationContractTest {

    @Test
    void baselineMedicalLoopAndAnalysisOutboxMigrationsArePackaged() throws IOException {
        String baseline = resource("/db/migration/V1__baseline_schema.sql");
        String hardening = resource("/db/migration/V2__medical_loop_hardening.sql");
        String outbox = resource("/db/migration/V5__analysis_outbox.sql");

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
    }

    private String resource(String path) throws IOException {
        try (InputStream input = getClass().getResourceAsStream(path)) {
            assertThat(input).as("migration resource %s", path).isNotNull();
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
