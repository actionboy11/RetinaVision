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
        Set<String> contractsMovingInThisSlice = Set.of(
                "AiQuotaReservation",
                "AiTaskQuotaService",
                "AnalysisResultService",
                "AnalysisTaskExecutionService",
                "AnalysisTaskRetryService",
                "TaskService");
        List<String> violations = productionClasses.stream()
                .filter(javaClass -> javaClass.getPackageName().equals(serviceRoot))
                .filter(javaClass -> contractsMovingInThisSlice.contains(javaClass.getSimpleName()))
                .map(JavaClass::getName)
                .sorted()
                .toList();

        assertTrue(violations.isEmpty(),
                () -> "Types in analysis service root: " + violations);
    }
}
