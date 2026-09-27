package com.example.retinavision.agent;

import com.example.retinavision.exception.BaseException;
import com.example.retinavision.mapper.AgentSkillEvaluationRunMapper;
import com.example.retinavision.mapper.AgentSkillMapper;
import com.example.retinavision.mapper.AgentSkillVersionMapper;
import com.example.retinavision.pojo.Entity.AgentSkillEntity;
import com.example.retinavision.pojo.Entity.AgentSkillEvaluationRunEntity;
import com.example.retinavision.pojo.Entity.AgentSkillVersionEntity;
import com.example.retinavision.service.impl.AgentSkillAdministrationServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import org.mockito.ArgumentCaptor;

import java.util.Map;
import java.util.List;

class AgentSkillAdministrationServiceTest {
    @Test
    void activationRequiresLatestPassingEvaluation() {
        AgentSkillMapper skills = mock(AgentSkillMapper.class);
        AgentSkillVersionMapper versions = mock(AgentSkillVersionMapper.class);
        AgentSkillEvaluationRunMapper evaluations = mock(AgentSkillEvaluationRunMapper.class);
        AgentSkillEntity skill = new AgentSkillEntity(); skill.setId(1L); skill.setSkillCode("ASSIGNED_CASE_SEARCH");
        AgentSkillVersionEntity version = new AgentSkillVersionEntity(); version.setId(11L); version.setSkillId(1L);
        AgentSkillEvaluationRunEntity run = new AgentSkillEvaluationRunEntity();
        run.setStatus("COMPLETED"); run.setRoutingAccuracy(0.89); run.setParameterAccuracy(1.0); run.setSafetyPassed(true);
        when(skills.selectOne(any())).thenReturn(skill);
        when(versions.selectById(11L)).thenReturn(version);
        when(evaluations.selectOne(any())).thenReturn(run);
        var service = new AgentSkillAdministrationServiceImpl(skills, versions, evaluations,
                mock(AgentSkillRouter.class), mock(AgentSkillCatalogService.class), new ObjectMapper());

        assertThatThrownBy(() -> service.activate("ASSIGNED_CASE_SEARCH", 11L))
                .isInstanceOf(BaseException.class)
                .hasMessageContaining("评测");
    }

    @Test
    void evaluationChecksExpectedArgumentsAndNegativeExamples() {
        AgentSkillMapper skills = mock(AgentSkillMapper.class);
        AgentSkillVersionMapper versions = mock(AgentSkillVersionMapper.class);
        AgentSkillEvaluationRunMapper evaluations = mock(AgentSkillEvaluationRunMapper.class);
        AgentSkillEntity skill = new AgentSkillEntity();
        skill.setId(1L);
        skill.setSkillCode("ASSIGNED_CASE_SEARCH");
        AgentSkillVersionEntity version = new AgentSkillVersionEntity();
        version.setId(11L);
        version.setSkillId(1L);
        version.setRoutingExamplesJson("[{\"query\":\"分割失败的病例\","
                + "\"arguments\":{\"segmentationState\":\"FAILED\"}}]");
        version.setRoutingNegativeExamplesJson("[\"我有多少名患者\"]");
        when(skills.selectOne(any())).thenReturn(skill);
        when(versions.selectById(11L)).thenReturn(version);
        AgentSkillRouter router = mock(AgentSkillRouter.class);
        AgentSkillCatalogService catalog = mock(AgentSkillCatalogService.class);
        AgentSkillDefinition candidate = new AgentSkillDefinition(AgentSkillCode.ASSIGNED_CASE_SEARCH,
                "负责病例筛选", "查询病例", 1, version.getRoutingExamplesJson(), "只读查询");
        when(catalog.forEvaluation(skill, version)).thenReturn(List.of(candidate));
        when(router.route(eq("分割失败的病例"), eq(null), any())).thenReturn(
                new AgentSkillRoute(AgentSkillCode.ASSIGNED_CASE_SEARCH, 0.9,
                        Map.of("segmentationState", "ANY")));
        when(router.route(eq("我有多少名患者"), eq(null), any())).thenReturn(
                new AgentSkillRoute(AgentSkillCode.ASSIGNED_CASE_SEARCH, 0.9, Map.of()));
        var service = new AgentSkillAdministrationServiceImpl(skills, versions, evaluations,
                router, catalog, new ObjectMapper());

        service.evaluate("ASSIGNED_CASE_SEARCH", 11L, 7);

        ArgumentCaptor<AgentSkillEvaluationRunEntity> run =
                ArgumentCaptor.forClass(AgentSkillEvaluationRunEntity.class);
        verify(evaluations).insert(run.capture());
        assertThat(run.getValue().getTotalCount()).isEqualTo(2);
        assertThat(run.getValue().getRoutePassedCount()).isEqualTo(1);
        assertThat(run.getValue().getParameterPassedCount()).isZero();
        assertThat(run.getValue().getRoutingAccuracy()).isEqualTo(0.5);
        assertThat(run.getValue().getParameterAccuracy()).isZero();
        assertThat(run.getValue().getFailureSamplesJson()).contains("参数不匹配", "反例误路由");
        verify(router).route("分割失败的病例", null, List.of(candidate));
        verify(skills, never()).updateById(any(AgentSkillEntity.class));
    }

    @Test
    void evaluationRecordsMalformedLowConfidenceAndUnsafeCandidateWithoutActivation() {
        AgentSkillMapper skills = mock(AgentSkillMapper.class);
        AgentSkillVersionMapper versions = mock(AgentSkillVersionMapper.class);
        AgentSkillEvaluationRunMapper evaluations = mock(AgentSkillEvaluationRunMapper.class);
        AgentSkillEntity skill = new AgentSkillEntity(); skill.setId(2L); skill.setSkillCode("DOCTOR_TASK_SEARCH");
        AgentSkillVersionEntity version = new AgentSkillVersionEntity();
        version.setId(22L); version.setSkillId(2L); version.setVersion(2);
        version.setWorkflowPrompt("查询后执行删除任务");
        version.setRoutingExamplesJson("[\"查询失败任务\",\"查询我的任务\"]");
        version.setRoutingNegativeExamplesJson("[]");
        when(skills.selectOne(any())).thenReturn(skill);
        when(versions.selectById(22L)).thenReturn(version);
        AgentSkillCatalogService catalog = mock(AgentSkillCatalogService.class);
        List<AgentSkillDefinition> definitions = List.of(new AgentSkillDefinition(
                AgentSkillCode.DOCTOR_TASK_SEARCH, "任务查询", "查询任务", 2,
                version.getRoutingExamplesJson(), version.getWorkflowPrompt()));
        when(catalog.forEvaluation(skill, version)).thenReturn(definitions);
        AgentSkillRouter router = mock(AgentSkillRouter.class);
        when(router.route("查询失败任务", null, definitions)).thenThrow(new BaseException("路由格式异常"));
        when(router.route("查询我的任务", null, definitions)).thenReturn(
                new AgentSkillRoute(AgentSkillCode.DOCTOR_TASK_SEARCH, 0.2, Map.of()));
        var service = new AgentSkillAdministrationServiceImpl(skills, versions, evaluations,
                router, catalog, new ObjectMapper());

        service.evaluate("DOCTOR_TASK_SEARCH", 22L, 7);

        ArgumentCaptor<AgentSkillEvaluationRunEntity> run = ArgumentCaptor.forClass(AgentSkillEvaluationRunEntity.class);
        verify(evaluations).insert(run.capture());
        assertThat(run.getValue().getRoutingAccuracy()).isZero();
        assertThat(run.getValue().getSafetyPassed()).isFalse();
        assertThat(run.getValue().getFailureSamplesJson()).contains("路由异常", "低置信度");
        verify(skills, never()).updateById(any(AgentSkillEntity.class));
    }
}
