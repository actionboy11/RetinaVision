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
    static final ArchRule domain_is_framework_free = noClasses()
            .that().resideInAPackage("..analysis.domain..")
            .should().dependOnClassesThat()
            .resideInAnyPackage(
                    "org.springframework..",
                    "com.baomidou.mybatisplus..",
                    "com.fasterxml.jackson..",
                    "com.rabbitmq..",
                    "java.nio.file..");

    @ArchTest
    static final ArchRule application_does_not_depend_on_infrastructure = noClasses()
            .that().resideInAPackage("..analysis.application..")
            .should().dependOnClassesThat()
            .resideInAPackage("..analysis.infrastructure..");

    @ArchTest
    static final ArchRule application_does_not_use_provider_or_filesystem_types = noClasses()
            .that().resideInAPackage("..analysis.application..")
            .should().dependOnClassesThat()
            .resideInAnyPackage(
                    "..retinavision.ai..",
                    "..retinavision.mq..",
                    "java.nio.file..");

    @ArchTest
    static final ArchRule infrastructure_is_not_used_by_legacy_controllers = noClasses()
            .that().resideInAPackage("..controller..")
            .should().dependOnClassesThat()
            .resideInAPackage("..analysis.infrastructure..");
}
