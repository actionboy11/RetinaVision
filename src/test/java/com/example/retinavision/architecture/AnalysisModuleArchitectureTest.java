package com.example.retinavision.architecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
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
    static final ArchRule analysis_application_and_ports_do_not_depend_on_infrastructure = noClasses()
            .that().resideInAnyPackage("..application.analysis..", "..port.analysis..")
            .should().dependOnClassesThat()
            .resideInAPackage("..infrastructure.analysis..");

    @ArchTest
    static final ArchRule analysis_use_cases_and_ports_do_not_use_provider_or_filesystem_types = noClasses()
            .that().resideInAPackage("..port.analysis..")
            .or().resideInAPackage("..application.analysis..")
            .should().dependOnClassesThat()
            .resideInAnyPackage(
                    "..retinavision.ai..",
                    "..retinavision.mq..",
                    "java.nio.file..");

    @ArchTest
    static final ArchRule analysis_infrastructure_is_not_used_by_controllers = noClasses()
            .that().resideInAPackage("..controller..")
            .should().dependOnClassesThat()
            .resideInAPackage("..infrastructure.analysis..");

    @ArchTest
    static final ArchRule inference_gateway_resides_in_analysis_infrastructure = classes()
            .that().haveSimpleName("HttpAiInferenceGateway")
            .should().resideInAPackage("..infrastructure.analysis.inference");

    @ArchTest
    static final ArchRule persistence_adapters_reside_in_analysis_infrastructure = classes()
            .that().resideInAPackage("..infrastructure.analysis.persistence..")
            .should().resideInAPackage("..infrastructure.analysis.persistence");

    @ArchTest
    static final ArchRule artifact_store_resides_in_analysis_infrastructure = classes()
            .that().haveSimpleName("LocalArtifactStore")
            .should().resideInAPackage("..infrastructure.analysis.storage");
}
