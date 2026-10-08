package com.example.retinavision.agent.evaluation;

import com.example.retinavision.agent.*;
import com.example.retinavision.enumeration.UserRole;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.retinavision.pojo.VO.CurrentUserVO;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AgentEvaluationFixtureServicesTest {

    @Test
    void doctorFixturesAreAnonymousPagedAndMapperFree() {
        FixtureDoctorAgentQueryService fixtures = new FixtureDoctorAgentQueryService();

        var first = fixtures.searchAssignedCases(
                new DoctorCaseSearchCriteria(SegmentationState.ANY, null), 1, 50, doctor());
        var second = fixtures.searchAssignedCases(
                new DoctorCaseSearchCriteria(SegmentationState.ANY, null), 2, 10, doctor());

        assertThat(first.getPageSize()).isEqualTo(10);
        assertThat(first.getRecords()).hasSize(10);
        assertThat(second.getRecords()).hasSize(10);
        assertThat(first.getRecords()).allSatisfy(item -> {
            assertThat(item.getCaseNo()).startsWith("EVAL-C-");
            assertThat(item.getPatientNo()).startsWith("PT-EVAL-");
        });
        assertNoMapperFields(FixtureDoctorAgentQueryService.class);
    }

    @Test
    void patientFixturesExposeOnlyOwnedAnonymousCasesAndReports() {
        FixturePatientAgentQueryService fixtures = new FixturePatientAgentQueryService();

        var cases = fixtures.listMyCases(new PatientCaseSearchCriteria(false, false), 1, 10, patient());
        var reports = fixtures.listMySignedReports("", 1, 10, patient());

        assertThat(cases.getRecords()).allSatisfy(item -> assertThat(item.getCaseNo()).startsWith("EVAL-C-"));
        assertThat(reports.getRecords()).allSatisfy(item -> assertThat(item.getCaseNo()).startsWith("EVAL-C-"));
        assertThatThrownBy(() -> fixtures.listMyCases(new PatientCaseSearchCriteria(false, false),
                1, 10, doctor())).isInstanceOf(IllegalArgumentException.class);
        assertNoMapperFields(FixturePatientAgentQueryService.class);
    }

    @Test
    void inMemoryContextsPreserveMultiTurnStateWithoutPersistence() {
        InMemoryAgentQueryContextService doctorContexts = new InMemoryAgentQueryContextService();
        InMemoryPatientAgentQueryContextService patientContexts = new InMemoryPatientAgentQueryContextService();
        AgentQueryContextSnapshot doctor = new AgentQueryContextSnapshot(
                AgentSkillCode.ASSIGNED_CASE_SEARCH, SegmentationState.FAILED,
                1, 10, 12, null, null, java.util.List.of(1, 2));
        PatientAgentQueryContextSnapshot patient = new PatientAgentQueryContextSnapshot(
                AgentSkillCode.MY_CASE_LIST, false, false, 1, 10, 3,
                null, null, null, java.util.List.of());

        doctorContexts.save(101L, doctor);
        patientContexts.save(202L, patient);

        assertThat(doctorContexts.load(101L)).contains(doctor);
        assertThat(patientContexts.load(202L)).contains(patient);
        assertThat(doctorContexts.load(999L)).isEmpty();
        assertNoMapperFields(InMemoryAgentQueryContextService.class);
        assertNoMapperFields(InMemoryPatientAgentQueryContextService.class);
    }

    private void assertNoMapperFields(Class<?> type) {
        assertThat(java.util.Arrays.stream(type.getDeclaredFields()).map(Field::getType))
                .noneMatch(fieldType -> fieldType.getSimpleName().endsWith("Mapper")
                        || BaseMapper.class.isAssignableFrom(fieldType));
    }

    private CurrentUserVO doctor() {
        return new CurrentUserVO(9001, "eval-doctor", "评测医生", UserRole.DOCTOR);
    }

    private CurrentUserVO patient() {
        return new CurrentUserVO(9002, "eval-patient", "评测患者", UserRole.USER);
    }
}
