package com.example.retinavision.analysis.infrastructure.config;

import com.example.retinavision.ai.AiInferenceClient;
import com.example.retinavision.analysis.application.ExecuteAnalysisTaskHandler;
import com.example.retinavision.analysis.application.ExecuteAnalysisTaskUseCase;
import com.example.retinavision.analysis.infrastructure.outbox.AnalysisOutboxMapper;
import com.example.retinavision.analysis.infrastructure.outbox.AnalysisOutboxProperties;
import com.example.retinavision.analysis.infrastructure.outbox.AnalysisOutboxPublisher;
import com.example.retinavision.analysis.infrastructure.outbox.MyBatisAnalysisTaskEventOutbox;
import com.example.retinavision.analysis.infrastructure.ai.HttpAiInferenceGateway;
import com.example.retinavision.analysis.infrastructure.image.MyBatisSourceImageReader;
import com.example.retinavision.analysis.infrastructure.persistence.LegacyAnalysisTaskRepository;
import com.example.retinavision.analysis.infrastructure.persistence.MyBatisAnalysisResultStore;
import com.example.retinavision.analysis.infrastructure.persistence.MyBatisTaskAuditLog;
import com.example.retinavision.analysis.infrastructure.reporting.LegacyReportDraftGateway;
import com.example.retinavision.analysis.infrastructure.storage.LocalArtifactStore;
import com.example.retinavision.mapper.AnalysisResultMapper;
import com.example.retinavision.mapper.ImageMapper;
import com.example.retinavision.mapper.LogMapper;
import com.example.retinavision.mapper.TaskMapper;
import com.example.retinavision.mq.AnalysisTaskMessagePublisher;
import com.example.retinavision.service.AnalysisReportService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.mybatis.spring.annotation.MapperScan;
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
@MapperScan(basePackageClasses = AnalysisOutboxMapper.class)
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
            MyBatisTaskAuditLog auditLog,
            LegacyReportDraftGateway reportDraftPort,
            Clock clock) {
        return new ExecuteAnalysisTaskHandler(
                taskRepository,
                sourceImageReader,
                aiInferenceGateway,
                artifactStore,
                resultStore,
                auditLog,
                reportDraftPort,
                clock);
    }
}
