package com.example.retinavision.mapper;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

class PatientAgentQueryMapperContractTest {

    @Test
    void everyPatientSelectScopesThroughAccountBoundProfile() throws IOException {
        String mapper = resource("/mapper/PatientAgentQueryMapper.xml");
        Matcher selects = Pattern.compile("<select\\b[\\s\\S]*?</select>").matcher(mapper);
        int count = 0;
        while (selects.find()) {
            count++;
            assertThat(selects.group())
                    .contains("JOIN patient_profile p ON p.id = c.patient_id")
                    .contains("p.account_user_id = #{userId}");
        }
        assertThat(count).isGreaterThanOrEqualTo(6);
    }

    @Test
    void listAndReportQueriesUsePatientSafeFiltersAndStableOrdering() throws IOException {
        String mapper = resource("/mapper/PatientAgentQueryMapper.xml");

        assertThat(mapper).contains(
                "id=\"countMyCases\"",
                "id=\"selectMyCases\"",
                "criteria.reuploadOnly",
                "criteria.signedReportOnly",
                "ORDER BY c.updated_at DESC, c.id DESC",
                "LIMIT #{offset}, #{pageSize}",
                "report.status = 'SIGNED'",
                "ORDER BY report.signed_at DESC, report.id DESC");
    }

    @Test
    void caseReferencesResolveInsidePatientScopedQueries() throws IOException {
        String mapper = resource("/mapper/PatientAgentQueryMapper.xml");

        assertThat(mapper).contains(
                "c.case_no = #{caseReference}",
                "c.id = #{caseId}",
                "c.status != 'DELETED'");
    }

    private String resource(String path) throws IOException {
        try (InputStream input = getClass().getResourceAsStream(path)) {
            assertThat(input).isNotNull();
            return new String(input.readAllBytes(), StandardCharsets.UTF_8).replace("\r\n", "\n");
        }
    }
}
