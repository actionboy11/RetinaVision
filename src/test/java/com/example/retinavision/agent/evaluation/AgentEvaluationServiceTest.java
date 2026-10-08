package com.example.retinavision.agent.evaluation;

import com.example.retinavision.exception.BaseException;
import com.example.retinavision.llm.LlmProperties;
import com.example.retinavision.mapper.*;
import com.example.retinavision.pojo.Entity.AgentEvaluationDatasetEntity;
import com.example.retinavision.pojo.Entity.AgentEvaluationRunBindingEntity;
import com.example.retinavision.pojo.Entity.AgentEvaluationRunEntity;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Map;
import java.util.concurrent.Executor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AgentEvaluationServiceTest {

    @Test
    void rejectsStartWhileAnotherRealModelRunIsActive() {
        Fixture fixture = fixture();
        when(fixture.runs.selectCount(any())).thenReturn(1L);

        assertThatThrownBy(() -> fixture.service().start(command()))
                .isInstanceOf(BaseException.class).hasMessageContaining("正在运行");
        verify(fixture.runs, never()).insert(any(AgentEvaluationRunEntity.class));
    }

    @Test
    void freezesDatasetSkillPromptAndModelBeforeSubmittingRunner() {
        Fixture fixture = fixture();
        when(fixture.runs.selectCount(any())).thenReturn(0L);
        when(fixture.runs.insert(any(AgentEvaluationRunEntity.class))).thenAnswer(invocation -> {
            AgentEvaluationRunEntity run = invocation.getArgument(0);
            run.setId(901L);
            return 1;
        });

        AgentEvaluationRunEntity started = fixture.service().start(command());

        assertThat(started.getStatus()).isEqualTo("QUEUED");
        assertThat(started.getProvider()).isEqualTo("qwen");
        assertThat(started.getModel()).isEqualTo("qwen-plus");
        ArgumentCaptor<AgentEvaluationRunBindingEntity> binding =
                ArgumentCaptor.forClass(AgentEvaluationRunBindingEntity.class);
        verify(fixture.bindings, times(2)).insert(binding.capture());
        assertThat(binding.getAllValues()).extracting(AgentEvaluationRunBindingEntity::getBindingType)
                .containsExactlyInAnyOrder("SKILL", "PROMPT");
        verify(fixture.runner).run(901L);
    }

    private AgentEvaluationStartCommand command() {
        return new AgentEvaluationStartCommand(7L, "DOCTOR", "ALIYUN_PRIMARY",
                Map.of("ASSIGNED_CASE_SEARCH", 31L), Map.of("AGENT_SKILL_ROUTER", 88L), 1);
    }

    private Fixture fixture() {
        AgentEvaluationDatasetMapper datasets = mock(AgentEvaluationDatasetMapper.class);
        AgentEvaluationRunMapper runs = mock(AgentEvaluationRunMapper.class);
        AgentEvaluationRunBindingMapper bindings = mock(AgentEvaluationRunBindingMapper.class);
        AgentEvaluationRunner runner = mock(AgentEvaluationRunner.class);
        AgentEvaluationDatasetEntity dataset = new AgentEvaluationDatasetEntity();
        dataset.setId(7L); dataset.setVersion(1); dataset.setTargetRole("DOCTOR");
        dataset.setStatus("ACTIVE"); dataset.setExpectedCaseCount(100);
        when(datasets.selectById(7L)).thenReturn(dataset);
        LlmProperties properties = new LlmProperties();
        properties.setProvider("qwen"); properties.setModel("qwen-plus");
        Executor direct = Runnable::run;
        return new Fixture(datasets, runs, bindings, runner, properties, direct);
    }

    private record Fixture(AgentEvaluationDatasetMapper datasets, AgentEvaluationRunMapper runs,
                           AgentEvaluationRunBindingMapper bindings, AgentEvaluationRunner runner,
                           LlmProperties properties, Executor executor) {
        AgentEvaluationService service() {
            return new AgentEvaluationService(datasets, runs, bindings, runner, properties, executor);
        }
    }
}
