package com.example.retinavision.architecture;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LayeredPackageStructureTest {

    private static final String BASE_PACKAGE = "com.example.retinavision";

    private final JavaClasses productionClasses = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages(BASE_PACKAGE);

    @Test
    void exception_subclasses_reside_under_exception_package() {
        List<String> violations = productionClasses.stream()
                .filter(javaClass -> javaClass.isAssignableTo(Throwable.class))
                .filter(javaClass -> !javaClass.getPackageName().startsWith(BASE_PACKAGE + ".exception"))
                .map(JavaClass::getName)
                .sorted()
                .toList();

        assertTrue(violations.isEmpty(),
                () -> "Exception classes outside exception package: " + violations);
    }

    @Test
    void global_handler_resides_in_exception_handler() {
        JavaClass handler = productionClasses.get("com.example.retinavision.exception.handler.GlobalExceptionHandler");

        assertEquals(BASE_PACKAGE + ".exception.handler", handler.getPackageName());
    }

    @Test
    void analysis_service_api_contains_only_interfaces() {
        String apiPackage = BASE_PACKAGE + ".service.analysis.api";
        Set<String> expectedInterfaces = Set.of(
                apiPackage + ".AiTaskQuotaService",
                apiPackage + ".AnalysisResultService",
                apiPackage + ".AnalysisTaskExecutionService",
                apiPackage + ".AnalysisTaskRetryService",
                apiPackage + ".TaskService");
        Set<String> actualTypes = productionClasses.stream()
                .filter(javaClass -> javaClass.getPackageName().equals(apiPackage))
                .filter(javaClass -> !javaClass.getName().contains("$"))
                .map(JavaClass::getName)
                .collect(Collectors.toSet());

        assertEquals(expectedInterfaces, actualTypes);
        assertTrue(productionClasses.stream()
                .filter(javaClass -> javaClass.getPackageName().equals(apiPackage))
                .filter(javaClass -> !javaClass.getName().contains("$"))
                .allMatch(JavaClass::isInterface));
    }

    @Test
    void analysis_service_root_contains_no_types() {
        String serviceRoot = BASE_PACKAGE + ".service.analysis";
        List<String> violations = productionClasses.stream()
                .filter(javaClass -> javaClass.getPackageName().equals(serviceRoot))
                .filter(javaClass -> !javaClass.getSimpleName().equals("package-info"))
                .map(JavaClass::getName)
                .sorted()
                .toList();

        assertTrue(violations.isEmpty(),
                () -> "Types in analysis service root: " + violations);
    }

    @Test
    void agent_service_api_contains_only_interfaces() {
        String apiPackage = BASE_PACKAGE + ".service.agent.api";
        Set<String> expectedInterfaces = Set.of(
                apiPackage + ".AgentChatService",
                apiPackage + ".AgentClinicalReferenceService",
                apiPackage + ".DoctorAgentQueryService",
                apiPackage + ".PatientAgentQueryService");
        Set<String> actualTypes = productionClasses.stream()
                .filter(javaClass -> javaClass.getPackageName().equals(apiPackage))
                .filter(javaClass -> !javaClass.getName().contains("$"))
                .map(JavaClass::getName)
                .collect(Collectors.toSet());

        assertEquals(expectedInterfaces, actualTypes);
        assertTrue(productionClasses.stream()
                .filter(javaClass -> javaClass.getPackageName().equals(apiPackage))
                .filter(javaClass -> !javaClass.getName().contains("$"))
                .allMatch(JavaClass::isInterface));
    }

    @Test
    void agent_service_root_contains_no_types() {
        String serviceRoot = BASE_PACKAGE + ".service.agent";
        List<String> violations = productionClasses.stream()
                .filter(javaClass -> javaClass.getPackageName().equals(serviceRoot))
                .map(JavaClass::getName)
                .sorted()
                .toList();

        assertTrue(violations.isEmpty(),
                () -> "Types in agent service root: " + violations);
    }

    @Test
    void agent_application_does_not_depend_on_agent_infrastructure() {
        List<String> violations = productionClasses.stream()
                .filter(javaClass -> javaClass.getPackageName().startsWith(BASE_PACKAGE + ".application.agent"))
                .flatMap(javaClass -> javaClass.getDirectDependenciesFromSelf().stream())
                .filter(dependency -> dependency.getTargetClass().getPackageName()
                        .startsWith(BASE_PACKAGE + ".infrastructure.agent"))
                .map(Object::toString)
                .sorted()
                .toList();

        assertTrue(violations.isEmpty(),
                () -> "Agent application depends on infrastructure: " + violations);
    }

    @Test
    void agent_adapters_reside_under_infrastructure() {
        Set<String> expectedPackages = Set.of(
                BASE_PACKAGE + ".infrastructure.agent.routing.LlmAgentSkillRouter",
                BASE_PACKAGE + ".infrastructure.agent.context.PersistentAgentQueryContextService",
                BASE_PACKAGE + ".infrastructure.agent.context.PersistentPatientAgentQueryContextService");
        Set<String> existingNames = productionClasses.stream()
                .map(JavaClass::getName)
                .filter(name -> name.endsWith(".LlmAgentSkillRouter")
                        || name.endsWith(".PersistentAgentQueryContextService")
                        || name.endsWith(".PersistentPatientAgentQueryContextService"))
                .collect(Collectors.toSet());

        assertEquals(expectedPackages, existingNames);
    }

    @Test
    void governance_service_api_contains_only_interfaces() {
        String apiPackage = BASE_PACKAGE + ".service.governance.api";
        Set<String> expectedInterfaces = Set.of(
                apiPackage + ".AgentSkillAdministrationService",
                apiPackage + ".AgentSkillExecutionQueryService",
                apiPackage + ".LlmCallLogService",
                apiPackage + ".PromptTemplateService",
                apiPackage + ".QualityControlService");
        Set<String> actualTypes = productionClasses.stream()
                .filter(javaClass -> javaClass.getPackageName().equals(apiPackage))
                .filter(javaClass -> !javaClass.getName().contains("$"))
                .map(JavaClass::getName)
                .collect(Collectors.toSet());

        assertEquals(expectedInterfaces, actualTypes);
        assertTrue(productionClasses.stream()
                .filter(javaClass -> javaClass.getPackageName().equals(apiPackage))
                .filter(javaClass -> !javaClass.getName().contains("$"))
                .allMatch(JavaClass::isInterface));
    }

    @Test
    void governance_service_root_contains_no_types() {
        String serviceRoot = BASE_PACKAGE + ".service.governance";
        List<String> violations = productionClasses.stream()
                .filter(javaClass -> javaClass.getPackageName().equals(serviceRoot))
                .map(JavaClass::getName)
                .sorted()
                .toList();

        assertTrue(violations.isEmpty(),
                () -> "Types in governance service root: " + violations);
    }

    @Test
    void evaluation_components_do_not_reside_under_service() {
        List<String> violations = productionClasses.stream()
                .filter(javaClass -> javaClass.getPackageName()
                        .startsWith(BASE_PACKAGE + ".service.governance.evaluation"))
                .map(JavaClass::getName)
                .sorted()
                .toList();

        assertTrue(violations.isEmpty(),
                () -> "Evaluation components under service: " + violations);
    }
}
