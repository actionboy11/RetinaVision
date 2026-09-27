package com.example.retinavision.mapper;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class DoctorAgentQueryMapperContractTest {

    @Test
    void caseSearchAppliesDoctorClinicalDateAndEyeFiltersInSql() throws IOException {
        String mapper = resource("/mapper/DoctorAgentQueryMapper.xml");

        assertThat(mapper).contains(
                "c.assigned_doctor_id = #{doctorId}",
                "criteria.clinicalState",
                "PENDING_REVIEW",
                "PENDING_REPORT",
                "criteria.dateWindow",
                "LAST_7_DAYS",
                "LAST_30_DAYS",
                "criteria.eyeSide");
    }

    @Test
    void caseProjectionIncludesReviewAndReportStateForStructuredCards() throws IOException {
        String mapper = resource("/mapper/DoctorAgentQueryMapper.xml");

        assertThat(mapper).contains("AS reviewStatus", "AS reportStatus");
    }

    @Test
    void taskQueriesAreDoctorScopedAndUseExpectedStatusSemantics() throws IOException {
        String mapper = resource("/mapper/DoctorAgentQueryMapper.xml");

        assertThat(mapper).contains(
                "id=\"countTasks\"",
                "id=\"selectTasks\"",
                "id=\"selectTaskDetail\"",
                "id=\"selectTaskLogs\"",
                "JOIN medical_case c ON c.id = t.case_id",
                "c.assigned_doctor_id = #{doctorId}",
                "('CREATED','WAITING')",
                "('RUNNING','RETRYING')",
                "ORDER BY t.updated_at DESC, t.id DESC");
    }

    @Test
    void taskReferenceAndLogsStayInsideDoctorScopedSql() throws IOException {
        String mapper = resource("/mapper/DoctorAgentQueryMapper.xml");

        assertThat(mapper).contains(
                "t.task_no = #{taskReference}",
                "t.id = #{taskId}",
                "JOIN analysis_task t ON t.id = l.task_id",
                "ORDER BY l.created_at DESC, l.id DESC",
                "LIMIT 5");
    }

    @Test
    void clinicalQueuesUseDistinctRowsAndStrictReviewSemantics() throws IOException {
        String mapper = resource("/mapper/DoctorAgentQueryMapper.xml");

        assertThat(mapper).contains(
                "id=\"countClinicalQueue\"",
                "id=\"selectClinicalQueue\"",
                "SELECT COUNT(DISTINCT t.id)",
                "SELECT DISTINCT",
                "t.task_type = 'VESSEL_SEGMENTATION'",
                "t.status = 'SUCCESS'",
                "ar.id IS NOT NULL",
                "rv.id IS NULL OR rv.status IN ('PENDING','CHANGES_REQUESTED')",
                "rv.status = 'APPROVED'");
    }

    @Test
    void pendingReportUsesSignedHistoryExistenceAndClinicalSort() throws IOException {
        String mapper = resource("/mapper/DoctorAgentQueryMapper.xml");

        assertThat(mapper).contains(
                "NOT EXISTS",
                "analysis_report signed_report",
                "signed_report.status = 'SIGNED'",
                "COALESCE(t.finished_at, ar.created_at) DESC, t.id DESC",
                "c.assigned_doctor_id = #{doctorId}");
    }

    private String resource(String path) throws IOException {
        try (InputStream input = getClass().getResourceAsStream(path)) {
            assertThat(input).isNotNull();
            return new String(input.readAllBytes(), StandardCharsets.UTF_8).replace("\r\n", "\n");
        }
    }
}
