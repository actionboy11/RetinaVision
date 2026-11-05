package com.example.retinavision.architecture;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertTrue;

class LayerFunctionalPackageArchitectureTest {

    private static final String BASE_PACKAGE = "com.example.retinavision";

    private static final Set<String> APPROVED_FUNCTIONS = Set.of(
            "auth", "user", "patient", "cases", "imaging", "analysis",
            "clinical", "reporting", "agent", "knowledge", "governance",
            "operations", "shared");

    private static final Set<String> TRACKED_ROOTS = Set.of(
            "agent", "ai", "analysis", "common", "config", "constant",
            "controller", "enumeration", "exception", "filter", "llm",
            "mapper", "mq", "pojo", "rag", "redis", "result", "service",
            "utils");

    private static final Set<String> TECHNICAL_ROOTS = Set.of(
            "ai", "config", "controller", "enumeration", "filter", "llm",
            "mapper", "mq", "pojo", "rag", "redis", "service", "utils");

    private final JavaClasses productionClasses = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages(BASE_PACKAGE);

    @Test
    void technical_roots_are_explicitly_tracked_during_migration() {
        Set<String> unexpectedRoots = new HashSet<>();

        for (JavaClass javaClass : productionClasses) {
            String relativePackage = relativePackage(javaClass);
            if (relativePackage.isEmpty()) {
                continue;
            }
            String root = relativePackage.split("\\.")[0];
            if (!TRACKED_ROOTS.contains(root)) {
                unexpectedRoots.add(root);
            }
        }

        assertTrue(unexpectedRoots.isEmpty(),
                () -> "Untracked production package roots: " + unexpectedRoots);
    }

    @Test
    void functional_names_are_from_the_approved_taxonomy() {
        List<String> violations = productionClasses.stream()
                .map(this::relativePackage)
                .filter(packageName -> !packageName.isEmpty())
                .map(packageName -> packageName.split("\\."))
                .filter(parts -> parts.length >= 2)
                .filter(parts -> TECHNICAL_ROOTS.contains(parts[0]))
                .filter(parts -> !isLegacyTechnicalPackage(parts))
                .filter(parts -> !APPROVED_FUNCTIONS.contains(parts[1]))
                .map(parts -> String.join(".", parts))
                .distinct()
                .sorted()
                .toList();

        assertTrue(violations.isEmpty(),
                () -> "Unapproved functional package names: " + violations);
    }

    @Test
    void service_classes_are_grouped_by_function() {
        List<String> violations = productionClasses.stream()
                .filter(javaClass -> javaClass.getPackageName().equals(BASE_PACKAGE + ".service")
                        || javaClass.getPackageName().equals(BASE_PACKAGE + ".service.impl"))
                .map(JavaClass::getName)
                .sorted()
                .toList();

        assertTrue(violations.isEmpty(),
                () -> "Service classes still stored outside functional groups: " + violations);
    }

    @Test
    void controller_and_auth_entry_points_are_grouped_by_function() {
        Set<String> rootPackages = Set.of(
                BASE_PACKAGE + ".controller",
                BASE_PACKAGE + ".filter",
                BASE_PACKAGE + ".utils");

        List<String> violations = productionClasses.stream()
                .filter(javaClass -> rootPackages.contains(javaClass.getPackageName()))
                .map(JavaClass::getName)
                .sorted()
                .toList();

        assertTrue(violations.isEmpty(),
                () -> "HTTP/auth entry points still stored outside functional groups: " + violations);
    }

    @Test
    void persistence_models_are_grouped_by_function() {
        Set<String> legacyPackages = Set.of(
                BASE_PACKAGE + ".mapper",
                BASE_PACKAGE + ".enumeration",
                BASE_PACKAGE + ".pojo.DTO",
                BASE_PACKAGE + ".pojo.Entity",
                BASE_PACKAGE + ".pojo.VO");

        List<String> violations = productionClasses.stream()
                .filter(javaClass -> legacyPackages.contains(javaClass.getPackageName()))
                .map(JavaClass::getName)
                .sorted()
                .toList();

        assertTrue(violations.isEmpty(),
                () -> "Persistence/model classes still stored outside functional groups: " + violations);
    }

    @Test
    void legacy_analysis_package_is_empty() {
        List<String> violations = productionClasses.stream()
                .filter(javaClass -> javaClass.getPackageName().equals(BASE_PACKAGE + ".analysis")
                        || javaClass.getPackageName().startsWith(BASE_PACKAGE + ".analysis."))
                .map(JavaClass::getName)
                .sorted()
                .toList();

        assertTrue(violations.isEmpty(),
                () -> "Legacy analysis packages still contain classes: " + violations);
    }

    private String relativePackage(JavaClass javaClass) {
        String packageName = javaClass.getPackageName();
        if (packageName.equals(BASE_PACKAGE)) {
            return "";
        }
        return packageName.substring(BASE_PACKAGE.length() + 1);
    }

    private boolean isLegacyTechnicalPackage(String[] parts) {
        if (parts.length == 1) {
            return true;
        }
        if (parts[0].equals("service") && parts[1].equals("impl")) {
            return true;
        }
        if (parts[0].equals("ai") && parts[1].equals("dto")) {
            return true;
        }
        return parts[0].equals("pojo")
                && Set.of("DTO", "Entity", "VO").contains(parts[1]);
    }
}
