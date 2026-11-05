package com.example.retinavision.architecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

@AnalyzeClasses(
        packages = "com.example.retinavision",
        importOptions = ImportOption.DoNotIncludeTests.class)
class AnalysisModuleArchitectureTest {

    @ArchTest
    static final ArchRule analysis_domain_models_are_framework_free = noClasses()
            .that().resideInAPackage("..pojo.analysis.domain..")
            .should().dependOnClassesThat()
            .resideInAnyPackage(
                    "org.springframework..",
                    "com.baomidou.mybatisplus..",
                    "com.fasterxml.jackson..",
                    "com.rabbitmq..",
                    "io.lettuce..",
                    "redis.clients..",
                    "java.nio.file..",
                    "java.net..",
                    "org.apache.hc..",
                    "okhttp3..");

    @ArchTest
    static final ArchRule analysis_services_do_not_depend_on_concrete_mq_ai_or_storage_packages = noClasses()
            .that().resideInAPackage("..service.analysis..")
            .and().doNotHaveFullyQualifiedName("com.example.retinavision.service.analysis.AnalysisTaskExecutionService")
            .and().doNotHaveFullyQualifiedName("com.example.retinavision.service.analysis.impl.AnalysisTaskExecutionServiceImpl")
            .and().doNotHaveFullyQualifiedName("com.example.retinavision.service.analysis.impl.AnalysisTaskRetryServiceImpl")
            .and().doNotHaveFullyQualifiedName("com.example.retinavision.service.analysis.impl.TaskServiceImpl")
            .should().dependOnClassesThat()
            .resideInAnyPackage(
                    "..mq.analysis..",
                    "..ai.analysis..",
                    "..service.analysis.storage..");

    @ArchTest
    static final ArchRule analysis_domain_models_do_not_use_provider_or_filesystem_types = noClasses()
            .that().resideInAPackage("..pojo.analysis..")
            .should().dependOnClassesThat()
            .resideInAnyPackage(
                    "..retinavision.ai..",
                    "..retinavision.mq..",
                    "java.nio.file..");

    @ArchTest
    static final ArchRule analysis_technical_adapters_are_not_used_by_unrelated_controllers = noClasses()
            .that().resideInAPackage("..controller..")
            .should().dependOnClassesThat()
            .resideInAnyPackage(
                    "..ai.analysis..",
                    "..config.analysis..",
                    "..mq.analysis..",
                    "..service.analysis.adapter..",
                    "..service.analysis.storage..");
}
