package com.example.retinavision.analysis.infrastructure.config;

import com.example.retinavision.ai.AiInferenceClient;
import com.example.retinavision.analysis.application.ExecuteAnalysisTaskUseCase;
import com.example.retinavision.analysis.application.port.out.AiInferencePort;
import com.example.retinavision.analysis.application.port.out.AnalysisResultStore;
import com.example.retinavision.analysis.application.port.out.AnalysisTaskRepository;
import com.example.retinavision.analysis.application.port.out.ArtifactStore;
import com.example.retinavision.analysis.application.port.out.ReportDraftPort;
import com.example.retinavision.analysis.application.port.out.SourceImageReader;
import com.example.retinavision.analysis.application.port.out.TaskAuditLog;
import com.example.retinavision.mapper.AnalysisResultMapper;
import com.example.retinavision.mapper.ImageMapper;
import com.example.retinavision.mapper.LogMapper;
import com.example.retinavision.mapper.TaskMapper;
import com.example.retinavision.service.AnalysisReportService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class AnalysisModuleConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(AnalysisModuleConfiguration.class)
            .withBean(TaskMapper.class, () -> mock(TaskMapper.class))
            .withBean(ImageMapper.class, () -> mock(ImageMapper.class))
            .withBean(AnalysisResultMapper.class, () -> mock(AnalysisResultMapper.class))
            .withBean(LogMapper.class, () -> mock(LogMapper.class))
            .withBean(AiInferenceClient.class, () -> mock(AiInferenceClient.class))
            .withBean(AnalysisReportService.class, () -> mock(AnalysisReportService.class))
            .withBean(ObjectMapper.class, ObjectMapper::new);

    @Test
    void wiresOneBeanForEveryExecutionPortAndTheUseCase() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(AnalysisTaskRepository.class);
            assertThat(context).hasSingleBean(SourceImageReader.class);
            assertThat(context).hasSingleBean(AiInferencePort.class);
            assertThat(context).hasSingleBean(ArtifactStore.class);
            assertThat(context).hasSingleBean(AnalysisResultStore.class);
            assertThat(context).hasSingleBean(TaskAuditLog.class);
            assertThat(context).hasSingleBean(ReportDraftPort.class);
            assertThat(context).hasSingleBean(ExecuteAnalysisTaskUseCase.class);
            assertThat(context).hasSingleBean(Clock.class);
            assertThat(context.getBean(Clock.class).getZone())
                    .isEqualTo(Clock.systemDefaultZone().getZone());
        });
    }

    @Test
    void preservesAUserProvidedClock() {
        Clock custom = Clock.fixed(
                Instant.parse("2026-07-28T08:00:00Z"), ZoneOffset.UTC);

        contextRunner.withBean(Clock.class, () -> custom).run(context -> {
            assertThat(context).hasSingleBean(Clock.class);
            assertThat(context.getBean(Clock.class)).isSameAs(custom);
        });
    }
}
