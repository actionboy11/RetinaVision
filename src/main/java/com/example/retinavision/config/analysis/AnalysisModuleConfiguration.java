package com.example.retinavision.config.analysis;

import com.example.retinavision.ai.analysis.AiInferenceClient;
import com.example.retinavision.application.analysis.handler.ExecuteAnalysisTaskHandler;
import com.example.retinavision.application.analysis.usecase.ExecuteAnalysisTaskUseCase;
import com.example.retinavision.mapper.analysis.AnalysisOutboxMapper;
import com.example.retinavision.config.analysis.AnalysisOutboxProperties;
import com.example.retinavision.mq.analysis.outbox.AnalysisOutboxPublisher;
import com.example.retinavision.mq.analysis.outbox.MyBatisAnalysisTaskEventOutbox;
import com.example.retinavision.infrastructure.analysis.inference.HttpAiInferenceGateway;
import com.example.retinavision.infrastructure.analysis.persistence.MyBatisImageQualityProjection;
import com.example.retinavision.infrastructure.analysis.persistence.MyBatisSourceImageReader;
import com.example.retinavision.infrastructure.analysis.persistence.LegacyAnalysisTaskRepository;
import com.example.retinavision.infrastructure.analysis.persistence.MyBatisAnalysisResultStore;
import com.example.retinavision.infrastructure.analysis.persistence.MyBatisTaskAuditLog;
import com.example.retinavision.infrastructure.analysis.persistence.LegacyReportDraftGateway;
import com.example.retinavision.infrastructure.analysis.storage.LocalArtifactStore;
import com.example.retinavision.mapper.analysis.AnalysisResultMapper;
import com.example.retinavision.mapper.imaging.ImageMapper;
import com.example.retinavision.mapper.analysis.LogMapper;
import com.example.retinavision.mapper.analysis.TaskMapper;
import com.example.retinavision.mq.analysis.AnalysisTaskMessagePublisher;
import com.example.retinavision.service.reporting.AnalysisReportService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.time.Clock;

@Configuration(proxyBeanMethods = false)
@EnableScheduling
@EnableConfigurationProperties(AnalysisOutboxProperties.class)
public class AnalysisModuleConfiguration {

    @Bean
    @ConditionalOnMissingBean(Clock.class)
    Clock analysisClock() {
        return Clock.systemDefaultZone();
    }

    @Bean
    LegacyAnalysisTaskRepository analysisTaskRepository(
            TaskMapper taskMapper,
            Clock clock) {
        return new LegacyAnalysisTaskRepository(taskMapper, clock);
    }

    @Bean
    MyBatisSourceImageReader sourceImageReader(ImageMapper imageMapper) {
        return new MyBatisSourceImageReader(imageMapper);
    }

    @Bean
    LocalArtifactStore artifactStore(
            @Value("${retina.upload.image-root:uploads/images}") String imageRoot,
            @Value("${retina.upload.result-root:uploads/results}") String resultRoot) {
        return new LocalArtifactStore(imageRoot, resultRoot);
    }

    @Bean
    HttpAiInferenceGateway aiInferenceGateway(
            AiInferenceClient client,
            LocalArtifactStore artifactStore) {
        return new HttpAiInferenceGateway(client, artifactStore);
    }

    @Bean
    MyBatisAnalysisResultStore analysisResultStore(
            AnalysisResultMapper resultMapper,
            ImageMapper imageMapper,
            ObjectMapper objectMapper,
            Clock clock) {
        return new MyBatisAnalysisResultStore(
                resultMapper, imageMapper, objectMapper, clock);
    }

    @Bean
    MyBatisImageQualityProjection imageQualityProjection(
            ImageMapper imageMapper,
            Clock clock) {
        return new MyBatisImageQualityProjection(imageMapper, clock);
    }

    @Bean
    MyBatisTaskAuditLog taskAuditLog(LogMapper logMapper) {
        return new MyBatisTaskAuditLog(logMapper);
    }

    @Bean
    MyBatisAnalysisTaskEventOutbox analysisTaskEventOutbox(
            AnalysisOutboxMapper mapper,
            ObjectMapper objectMapper,
            Clock clock) {
        return new MyBatisAnalysisTaskEventOutbox(mapper, objectMapper, clock);
    }

    @Bean
    AnalysisOutboxPublisher analysisOutboxPublisher(
            AnalysisOutboxMapper mapper,
            AnalysisTaskMessagePublisher messagePublisher,
            ObjectMapper objectMapper,
            AnalysisOutboxProperties properties,
            Clock clock) {
        return new AnalysisOutboxPublisher(
                mapper, messagePublisher, objectMapper, properties, clock);
    }

    @Bean
    LegacyReportDraftGateway reportDraftPort(
            TaskMapper taskMapper,
            AnalysisReportService reportService) {
        return new LegacyReportDraftGateway(taskMapper, reportService);
    }

    @Bean
    ExecuteAnalysisTaskUseCase executeAnalysisTaskUseCase(
            LegacyAnalysisTaskRepository taskRepository,
            MyBatisSourceImageReader sourceImageReader,
            HttpAiInferenceGateway aiInferenceGateway,
            LocalArtifactStore artifactStore,
            MyBatisAnalysisResultStore resultStore,
            MyBatisImageQualityProjection imageQualityProjection,
            MyBatisTaskAuditLog auditLog,
            LegacyReportDraftGateway reportDraftPort,
            Clock clock) {
        return new ExecuteAnalysisTaskHandler(
                taskRepository,
                sourceImageReader,
                aiInferenceGateway,
                artifactStore,
                resultStore,
                imageQualityProjection,
                auditLog,
                reportDraftPort,
                clock);
    }
}
