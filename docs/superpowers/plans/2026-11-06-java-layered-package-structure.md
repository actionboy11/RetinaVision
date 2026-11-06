# Java Layered Package Structure Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Reorganize the RetinaVision Java backend so directories clearly separate business Service APIs/implementations, application orchestration, external ports, infrastructure adapters, and exceptions without changing runtime behavior.

**Architecture:** Preserve the modular monolith and existing functional domains while introducing top-level `application`, `port`, and `infrastructure` packages. Keep Controller-facing business interfaces under `service/<domain>/api`, implementations under `service/<domain>/impl`, and centralize exceptions under `exception/<domain>`. This is an organizational refactor: internal FQCNs change, but HTTP contracts, serialized payloads, Spring bean names, method signatures, database structures, and behavior remain unchanged.

**Tech Stack:** Java 21, Spring Boot 3.5, Spring Security, Spring AI, MyBatis-Plus, ArchUnit, JUnit 5, Maven

**Spec:** `docs/superpowers/specs/2026-11-06-java-layered-package-structure-design.md`

## Global Constraints

- Preserve every existing class name and public method signature; only package declarations, imports, test packages, and architecture documentation may change.
- Do not change HTTP paths, request/response payloads, database tables, Flyway migrations, configuration keys, RabbitMQ topology, Agent behavior, or analysis state transitions.
- Do not add interfaces merely to satisfy directory symmetry; concrete application use cases belong under `application`.
- `service/<domain>/api` contains business Service interfaces only; `service/<domain>/impl` contains their implementations only.
- `application` must not depend on `infrastructure`; `port` must not depend on `infrastructure`.
- Production classes and their focused tests move together.
- Complete each task with a green focused test run and a separate commit.
- Preserve unrelated user changes in the main checkout; all work occurs in the isolated `refactor/java-layered-package-structure` worktree.

## Review Focus

- Spring bean discovery after package moves: `RetinaVisionApplicationTests` must load the context without missing or duplicate beans.
- Constructor/configuration wiring for non-annotated Handler and adapters: `AnalysisModuleConfigurationTest` must resolve every moved type.
- Agent role isolation after router/context moves: doctor and patient security/routing contract tests must remain green.
- Stale package references in tests, architecture rules, comments, and documentation: repository-wide searches for each removed package must return no Java references.
- Architecture rules introduced before a slice is migrated: rules must be enabled slice-by-slice so intermediate commits remain buildable.

---

### Task 1: Package Roots, Architecture Harness, and Centralized Exceptions

**Files:**
- Create: `src/test/java/com/example/retinavision/architecture/LayeredPackageStructureTest.java`
- Modify: `src/test/java/com/example/retinavision/architecture/LayerFunctionalPackageArchitectureTest.java`
- Move: `src/main/java/com/example/retinavision/exception/BaseException.java` -> `src/main/java/com/example/retinavision/exception/shared/BaseException.java`
- Move: `src/main/java/com/example/retinavision/exception/ConflictException.java` -> `src/main/java/com/example/retinavision/exception/shared/ConflictException.java`
- Move: `src/main/java/com/example/retinavision/exception/RateLimitException.java` -> `src/main/java/com/example/retinavision/exception/shared/RateLimitException.java`
- Move: `src/main/java/com/example/retinavision/exception/RedisUnavailableException.java` -> `src/main/java/com/example/retinavision/exception/shared/RedisUnavailableException.java`
- Move: `src/main/java/com/example/retinavision/exception/AccountNotFoundException.java` -> `src/main/java/com/example/retinavision/exception/auth/AccountNotFoundException.java`
- Move: `src/main/java/com/example/retinavision/exception/GlobalExceptionHandler.java` -> `src/main/java/com/example/retinavision/exception/handler/GlobalExceptionHandler.java`
- Move: `src/main/java/com/example/retinavision/ai/analysis/AiInferenceException.java` -> `src/main/java/com/example/retinavision/exception/analysis/AiInferenceException.java`
- Move: `src/main/java/com/example/retinavision/pojo/analysis/domain/IllegalTaskTransitionException.java` -> `src/main/java/com/example/retinavision/exception/analysis/IllegalTaskTransitionException.java`
- Move: `src/main/java/com/example/retinavision/llm/shared/LlmException.java` -> `src/main/java/com/example/retinavision/exception/llm/LlmException.java`
- Move: `src/main/java/com/example/retinavision/pojo/governance/evaluation/AgentEvaluationInfrastructureException.java` -> `src/main/java/com/example/retinavision/exception/evaluation/AgentEvaluationInfrastructureException.java`
- Move: `src/main/java/com/example/retinavision/service/agent/policy/AgentUnsafeRequestException.java` -> `src/main/java/com/example/retinavision/exception/agent/AgentUnsafeRequestException.java`
- Modify: all production and test files returned by `rg -l 'com\.example\.retinavision\.(exception\.(Base|Conflict|RateLimit|RedisUnavailable|AccountNotFound|Global)|ai\.analysis\.AiInferenceException|pojo\.analysis\.domain\.IllegalTaskTransitionException|llm\.shared\.LlmException|pojo\.governance\.evaluation\.AgentEvaluationInfrastructureException|service\.agent\.policy\.AgentUnsafeRequestException)' src/main/java src/test/java`

**Interfaces:**
- Consumes: existing exception constructors and `ErrorMessageSignal` behavior.
- Produces: centralized exception FQCNs and an ArchUnit harness that later tasks extend.

- [ ] **Step 1: Add failing root and exception-location architecture tests**

Add tests named `new_layer_roots_are_allowed`, `exception_subclasses_reside_under_exception_package`, and `global_handler_resides_in_exception_handler`. The exception rule must exclude JDK/library exceptions and assert only RetinaVision exception subclasses.

- [ ] **Step 2: Run the architecture tests and verify they fail on current scattered exceptions**

Run: `mvn -Dtest=LayerFunctionalPackageArchitectureTest,LayeredPackageStructureTest test`

Expected: FAIL because the allowlist lacks `application`, `port`, and `infrastructure`, and project exceptions still reside outside `exception..`.

- [ ] **Step 3: Add the new root packages to the architecture allowlist**

Update `LayerFunctionalPackageArchitectureTest` so `application`, `port`, and `infrastructure` are recognized technical roots. Do not enable final Service restrictions yet.

- [ ] **Step 4: Move exception classes and update imports without changing constructors or handlers**

Keep `IllegalTaskTransitionException(AnalysisTaskStatus, AnalysisTaskStatus)` unchanged even though it creates the documented domain/exception cross-reference. Keep `GlobalExceptionHandler` annotations and handler methods unchanged.

- [ ] **Step 5: Run exception, security, and architecture tests**

Run: `mvn -Dtest=LayerFunctionalPackageArchitectureTest,LayeredPackageStructureTest,GlobalExceptionHandlerTest,JwtAuthenticationFilterTest,LlmSafetyPolicyTest test`

Expected: PASS.

- [ ] **Step 6: Verify no old exception imports remain**

Run the exact `rg` expression from the Files section against `src/main/java` and `src/test/java`.

Expected: no matches.

- [ ] **Step 7: Commit**

```bash
git add src/main/java src/test/java
git commit -m "refactor: centralize backend exceptions"
```

---

### Task 2: Analysis Service API and Data Contracts

**Files:**
- Move: `src/main/java/com/example/retinavision/service/analysis/AiTaskQuotaService.java` -> `src/main/java/com/example/retinavision/service/analysis/api/AiTaskQuotaService.java`
- Move: `src/main/java/com/example/retinavision/service/analysis/AnalysisResultService.java` -> `src/main/java/com/example/retinavision/service/analysis/api/AnalysisResultService.java`
- Move: `src/main/java/com/example/retinavision/service/analysis/AnalysisTaskExecutionService.java` -> `src/main/java/com/example/retinavision/service/analysis/api/AnalysisTaskExecutionService.java`
- Move: `src/main/java/com/example/retinavision/service/analysis/AnalysisTaskRetryService.java` -> `src/main/java/com/example/retinavision/service/analysis/api/AnalysisTaskRetryService.java`
- Move: `src/main/java/com/example/retinavision/service/analysis/TaskService.java` -> `src/main/java/com/example/retinavision/service/analysis/api/TaskService.java`
- Move: `src/main/java/com/example/retinavision/service/analysis/AiQuotaReservation.java` -> `src/main/java/com/example/retinavision/pojo/analysis/dto/AiQuotaReservation.java`
- Modify: `src/main/java/com/example/retinavision/service/analysis/impl/*.java`
- Modify: all consumers returned by `rg -l 'service\.analysis\.(AiTaskQuotaService|AnalysisResultService|AnalysisTaskExecutionService|AnalysisTaskRetryService|TaskService|AiQuotaReservation)' src/main/java src/test/java`
- Modify: `src/test/java/com/example/retinavision/architecture/LayeredPackageStructureTest.java`

**Interfaces:**
- Consumes: centralized exception packages from Task 1.
- Produces: stable analysis Service APIs under `service.analysis.api` and the quota DTO under `pojo.analysis.dto`.

- [ ] **Step 1: Add failing Analysis Service layout rules**

Add `analysis_service_api_contains_only_interfaces` and `analysis_service_root_contains_no_types`. Restrict these rules to the Analysis slice so other domains can migrate later.

- [ ] **Step 2: Run the focused architecture test and verify failure**

Run: `mvn -Dtest=LayeredPackageStructureTest test`

Expected: FAIL because Analysis interfaces and `AiQuotaReservation` still live directly under `service.analysis`.

- [ ] **Step 3: Move the five Service interfaces and quota record**

Update package declarations and all imports. Do not rename `AiQuotaReservation` or alter its record components.

- [ ] **Step 4: Update Analysis implementations and Controller/MQ/configuration consumers**

Each existing `service.analysis.impl` class must implement the same interface from `service.analysis.api`. No implementation body changes are allowed.

- [ ] **Step 5: Run Analysis Service and architecture tests**

Run: `mvn -Dtest=LayeredPackageStructureTest,AnalysisTaskExecutionServiceImplTest,AnalysisTaskRetryServiceImplTest,RedisAiTaskQuotaServiceTest,TaskServiceImplTest test`

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add src/main/java src/test/java
git commit -m "refactor: separate analysis service APIs"
```

---

### Task 3: Analysis Application, Ports, and Infrastructure

**Files:**
- Move: `src/main/java/com/example/retinavision/service/analysis/ExecuteAnalysisTaskUseCase.java` -> `src/main/java/com/example/retinavision/application/analysis/usecase/ExecuteAnalysisTaskUseCase.java`
- Move: `src/main/java/com/example/retinavision/service/analysis/ExecuteAnalysisTaskHandler.java` -> `src/main/java/com/example/retinavision/application/analysis/handler/ExecuteAnalysisTaskHandler.java`
- Move: every file under `src/main/java/com/example/retinavision/service/analysis/port/` -> `src/main/java/com/example/retinavision/port/analysis/`
- Move: every implementation under `src/main/java/com/example/retinavision/service/analysis/adapter/` -> `src/main/java/com/example/retinavision/infrastructure/analysis/persistence/`
- Move: `src/main/java/com/example/retinavision/service/analysis/storage/LocalArtifactStore.java` -> `src/main/java/com/example/retinavision/infrastructure/analysis/storage/LocalArtifactStore.java`
- Move: `src/main/java/com/example/retinavision/ai/analysis/HttpAiInferenceGateway.java` -> `src/main/java/com/example/retinavision/infrastructure/analysis/inference/HttpAiInferenceGateway.java`
- Modify: `src/main/java/com/example/retinavision/config/analysis/AnalysisModuleConfiguration.java`
- Modify: `src/main/java/com/example/retinavision/mq/analysis/AnalysisTaskListener.java`
- Modify: `src/main/java/com/example/retinavision/service/analysis/impl/AnalysisTaskExecutionServiceImpl.java`
- Modify: `src/test/java/com/example/retinavision/architecture/AnalysisModuleArchitectureTest.java`
- Move: `src/test/java/com/example/retinavision/service/analysis/ExecuteAnalysisTaskHandlerTest.java` -> `src/test/java/com/example/retinavision/application/analysis/handler/ExecuteAnalysisTaskHandlerTest.java`
- Move: tests under `src/test/java/com/example/retinavision/service/analysis/adapter/` -> `src/test/java/com/example/retinavision/infrastructure/analysis/persistence/`
- Move: `src/test/java/com/example/retinavision/service/analysis/storage/LocalArtifactStoreTest.java` -> `src/test/java/com/example/retinavision/infrastructure/analysis/storage/LocalArtifactStoreTest.java`
- Move: `src/test/java/com/example/retinavision/ai/analysis/HttpAiInferenceGatewayTest.java` -> `src/test/java/com/example/retinavision/infrastructure/analysis/inference/HttpAiInferenceGatewayTest.java`
- Modify: all files returned by `rg -l 'service\.analysis\.(port|adapter|storage)|service\.analysis\.ExecuteAnalysisTask|ai\.analysis\.HttpAiInferenceGateway' src/main/java src/test/java`

**Interfaces:**
- Consumes: Analysis Service API and quota contract from Task 2.
- Produces: `application.analysis.usecase.ExecuteAnalysisTaskUseCase`, all `port.analysis` interfaces, and concrete Analysis infrastructure adapters.

- [ ] **Step 1: Rewrite Analysis architecture tests against target packages and verify they fail before moves**

Rules must assert: Application/Port do not depend on `infrastructure..`; Controller does not depend on `infrastructure.analysis..`; `HttpAiInferenceGateway` and persistence/storage adapters reside under `infrastructure.analysis..`.

- [ ] **Step 2: Run Analysis architecture tests to confirm failure**

Run: `mvn -Dtest=AnalysisModuleArchitectureTest test`

Expected: FAIL because target packages are empty and old Analysis infrastructure remains under Service/AI.

- [ ] **Step 3: Move UseCase, Handler, and all Port interfaces**

Preserve `ExecutionDisposition execute(ExecuteAnalysisTaskCommand command)` and every Port signature exactly.

- [ ] **Step 4: Move persistence adapters, LocalArtifactStore, and HttpAiInferenceGateway**

Only update package/import statements. Keep `AiInferenceClient` and provider DTOs in `ai.analysis`.

- [ ] **Step 5: Update AnalysisModuleConfiguration and all construction sites**

Confirm every manually constructed Handler/adapter still receives the same constructor arguments and the same bean names.

- [ ] **Step 6: Move focused tests and update test packages/imports**

Do not weaken assertions or convert focused tests to context-only tests.

- [ ] **Step 7: Run Analysis tests**

Run: `mvn -Dtest=AnalysisModuleArchitectureTest,AnalysisModuleConfigurationTest,ExecuteAnalysisTaskHandlerTest,HttpAiInferenceGatewayTest,LocalArtifactStoreTest,AnalysisTaskListenerTest test`

Expected: PASS.

- [ ] **Step 8: Verify old Analysis packages are absent**

Run: `rg -n 'service\.analysis\.(port|adapter|storage)|service\.analysis\.ExecuteAnalysisTask|ai\.analysis\.HttpAiInferenceGateway' src/main/java src/test/java`

Expected: no Java package/import matches.

- [ ] **Step 9: Commit**

```bash
git add src/main/java src/test/java
git commit -m "refactor: separate analysis application boundaries"
```

---

### Task 4: Agent Service, Application, and Infrastructure

**Files:**
- Move: `service/agent/AgentChatService.java`, `AgentClinicalReferenceService.java`, `DoctorAgentQueryService.java`, `PatientAgentQueryService.java` -> `service/agent/api/`
- Keep/modify: `service/agent/impl/AgentChatServiceImpl.java`, `AgentClinicalReferenceServiceImpl.java`, `DoctorAgentQueryServiceImpl.java`, `PatientAgentQueryServiceImpl.java`
- Move: `service/agent/AgentQueryContextService.java`, `PatientAgentQueryContextService.java` -> `application/agent/context/api/`
- Move: `service/agent/AgentContextCommandParser.java`, `PatientAgentContextCommandParser.java` -> `application/agent/context/`
- Move: `service/agent/PersistentAgentQueryContextService.java`, `PersistentPatientAgentQueryContextService.java` -> `infrastructure/agent/context/`
- Move: `service/agent/AgentSkillRouter.java`, `AgentSkillVersionResolver.java` -> `application/agent/routing/api/`
- Move: `service/agent/AgentSkillCatalogService.java`, `AgentSkillRegistry.java`, `AgentSkillVersionBindingService.java` -> `application/agent/routing/`
- Move: `llm/agent/LlmAgentSkillRouter.java` -> `infrastructure/agent/routing/LlmAgentSkillRouter.java`
- Move: `service/agent/AgentToolFactory.java` -> `application/agent/tooling/api/AgentToolFactory.java`
- Move: `service/agent/AgentToolBundle.java`, `DefaultAgentToolFactory.java` -> `application/agent/tooling/`
- Move: `service/agent/DoctorAgentSkillOrchestrator.java`, `PatientAgentSkillOrchestrator.java` -> `application/agent/orchestration/`
- Move: `service/agent/PatientQualityPresenter.java` -> `application/agent/presentation/`
- Move: `service/agent/policy/AgentUnsafeRequestPolicy.java` -> `application/agent/policy/AgentUnsafeRequestPolicy.java`
- Modify: Agent/LLM/Governance production files returned by `rg -l 'service\.agent\.|llm\.agent\.LlmAgentSkillRouter' src/main/java src/test/java`
- Move: focused tests to mirror the new packages, including router, catalog, registry, context, tooling, orchestration, presenter, and policy tests.
- Modify: `src/test/java/com/example/retinavision/architecture/LayeredPackageStructureTest.java`

**Interfaces:**
- Consumes: centralized Agent/LLM exceptions from Task 1 and stable Service conventions from Task 2.
- Produces: Agent business APIs under `service.agent.api`, internal application contracts under `application.agent`, and LLM/persistence adapters under `infrastructure.agent`.

- [ ] **Step 1: Add failing Agent layout/dependency rules**

Assert that `service.agent` root has no direct types, `service.agent.api` contains interfaces only, `application.agent` does not depend on `infrastructure.agent`, and concrete LLM/persistent context adapters reside under Infrastructure.

- [ ] **Step 2: Run Agent architecture test and verify failure**

Run: `mvn -Dtest=LayeredPackageStructureTest test`

Expected: FAIL on current mixed Agent root.

- [ ] **Step 3: Move Agent business Service APIs and update implementations**

Preserve every method signature and Spring annotation.

- [ ] **Step 4: Move context, routing, tooling, orchestration, presentation, and policy components**

Use the exact destinations in Files. Do not alter routing, argument normalization, role filtering, or safety behavior.

- [ ] **Step 5: Move LlmAgentSkillRouter and persistent context implementations to Infrastructure**

Keep their implemented interfaces and constructor arguments unchanged.

- [ ] **Step 6: Move/update focused tests to mirror production packages**

Preserve security contract tests as independent regression coverage.

- [ ] **Step 7: Run Agent tests**

Run: `mvn -Dtest=LayeredPackageStructureTest,AgentChatServiceImplTest,LlmAgentSkillRouterTest,DoctorAgentSkillOrchestratorTest,PatientAgentSkillOrchestratorTest,PatientAgentSecurityContractTest,DoctorAgentRoutingEvaluationTest,PatientAgentRoutingEvaluationTest,PersistentAgentQueryContextServiceTest,PersistentPatientAgentQueryContextServiceTest test`

Expected: PASS, including doctor/patient routing and security assertions.

- [ ] **Step 8: Verify old Agent root imports are gone**

Run targeted `rg` for each moved FQCN and verify only migration documentation mentions old paths.

- [ ] **Step 9: Commit**

```bash
git add src/main/java src/test/java
git commit -m "refactor: separate agent application boundaries"
```

---

### Task 5: Governance Services and Evaluation Application

**Files:**
- Move: `service/governance/AgentSkillAdministrationService.java`, `AgentSkillExecutionQueryService.java`, `LlmCallLogService.java`, `PromptTemplateService.java`, `QualityControlService.java` -> `service/governance/api/`
- Modify: every class under `service/governance/impl/` to import the new API package.
- Move: `service/governance/evaluation/AgentEvaluationService.java`, `AgentEvaluationAdministrationService.java` -> `application/governance/evaluation/usecase/`
- Move: `AgentEvaluationRunner.java` -> `application/governance/evaluation/runner/`
- Move: `AgentEvaluationScorer.java` -> `application/governance/evaluation/scoring/`
- Move: `AgentEvaluationCaseExecutor.java`, `DefaultAgentEvaluationCaseExecutor.java` -> `application/governance/evaluation/execution/`
- Move: `AgentEvaluationEligibilityService.java` -> `application/governance/evaluation/policy/`
- Move: `AgentEvaluationFixtureRuntime.java`, all `Fixture*Service.java`, and `InMemory*ContextService.java` -> `application/governance/evaluation/fixture/`
- Modify: `controller/governance/AgentEvaluationController.java`, `PromptManagementController.java`, and all affected governance/agent/LLM imports.
- Move: tests under `service/governance/evaluation/` to mirror `application/governance/evaluation/` destinations.
- Modify: `src/test/java/com/example/retinavision/architecture/LayeredPackageStructureTest.java`

**Interfaces:**
- Consumes: Agent application contracts from Task 4.
- Produces: clean Governance Service APIs and concrete evaluation use cases callable by Controllers.

- [ ] **Step 1: Add failing Governance layout rules**

Assert interface-only `service.governance.api`, no direct types under `service.governance`, and no evaluation Runner/Fixture/Scorer under Service.

- [ ] **Step 2: Run focused architecture test and verify failure**

Run: `mvn -Dtest=LayeredPackageStructureTest test`

Expected: FAIL on current Governance/evaluation layout.

- [ ] **Step 3: Move Governance Service interfaces and update implementations/controllers**

Keep Controller request mappings and implementation methods unchanged.

- [ ] **Step 4: Move evaluation use cases and internal components**

`AgentEvaluationController` may directly inject the two concrete classes in `application.governance.evaluation.usecase`; it must not inject Runner, Fixture, Scorer, or Infrastructure classes.

- [ ] **Step 5: Move evaluation tests and update imports**

Keep fixture isolation and version eligibility assertions unchanged.

- [ ] **Step 6: Run Governance and evaluation tests**

Run: `mvn -Dtest=LayeredPackageStructureTest,AgentEvaluationControllerTest,PromptManagementControllerTest,AgentEvaluationServiceTest,AgentEvaluationAdministrationServiceTest,AgentEvaluationRunnerTest,AgentEvaluationScorerTest,AgentEvaluationFixtureRuntimeTest,DefaultAgentEvaluationCaseExecutorTest,AgentEvaluationEligibilityServiceTest test`

Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add src/main/java src/test/java
git commit -m "refactor: separate governance evaluation layers"
```

---

### Task 6: Remaining Service Domains and Reporting Port

**Files:**
- Move: every business Service interface directly under `service/auth`, `service/cases`, `service/clinical`, `service/imaging`, `service/knowledge`, `service/operations`, `service/patient`, `service/reporting`, and `service/user` -> the matching `service/<domain>/api/`; exclude `ReportPdfRenderer`, which moves to `port/reporting` below.
- Modify: all matching `service/<domain>/impl/*.java` imports.
- Move: `service/patient/impl/SecurePatientNumberGenerator.java` -> `application/patient/support/SecurePatientNumberGenerator.java`
- Move: `service/reporting/ReportPdfRenderer.java` -> `port/reporting/ReportPdfRenderer.java`
- Move: `service/reporting/ReportPdfDocument.java` -> `port/reporting/model/ReportPdfDocument.java`
- Move: `service/reporting/impl/JdkReportPdfRenderer.java` -> `infrastructure/reporting/pdf/JdkReportPdfRenderer.java`
- Move: `src/test/java/com/example/retinavision/service/patient/impl/SecurePatientNumberGeneratorTest.java` -> `src/test/java/com/example/retinavision/application/patient/support/SecurePatientNumberGeneratorTest.java`
- Move: `src/test/java/com/example/retinavision/service/reporting/impl/JdkReportPdfRendererTest.java` -> `src/test/java/com/example/retinavision/infrastructure/reporting/pdf/JdkReportPdfRendererTest.java`
- Modify: all Controller, configuration, Service, Agent tool, and test imports returned by `rg -l 'service\.(auth|cases|clinical|imaging|knowledge|operations|patient|reporting|user)\.' src/main/java src/test/java`
- Modify: `src/test/java/com/example/retinavision/architecture/LayeredPackageStructureTest.java`

**Interfaces:**
- Consumes: established API/impl convention and Architecture harness.
- Produces: all remaining business Service domains with consistent API/implementation directories and reporting infrastructure boundaries.

- [ ] **Step 1: Extend layout rules to all remaining Service domains and verify failure**

Assert each domain root contains no direct Java type and each `api` contains interfaces only.

- [ ] **Step 2: Move Service interfaces one domain at a time**

Order: auth, cases, clinical, imaging, knowledge, operations, patient, reporting, user. After each domain, run `mvn -DskipTests compile` so import failures remain localized.

- [ ] **Step 3: Move patient support and reporting Port/infrastructure classes**

Preserve PDF renderer behavior and record components exactly.

- [ ] **Step 4: Move focused tests and update all consumers**

Controllers must continue depending on interfaces, not `impl` classes.

- [ ] **Step 5: Run remaining Service tests and application context**

Run: `mvn -Dtest=LayeredPackageStructureTest,UserLoginServiceImplTest,CaseServiceImplTest,ClinicalAccessServiceImplTest,ResultHumanWorkflowServiceImplTest,KnowledgeChatServiceImplTest,SystemStatusServiceImplTest,PatientCaseServiceImplTest,AnalysisReportServiceImplTest,JdkReportPdfRendererTest,UserAdministrationServiceImplTest,RetinaVisionApplicationTests test`

Expected: PASS with no missing or duplicate Spring beans.

- [ ] **Step 6: Commit**

```bash
git add src/main/java src/test/java
git commit -m "refactor: standardize service API packages"
```

---

### Task 7: Final Architecture Rules, Documentation, and Full Verification

**Files:**
- Modify: `src/test/java/com/example/retinavision/architecture/LayeredPackageStructureTest.java`
- Modify: `src/test/java/com/example/retinavision/architecture/LayerFunctionalPackageArchitectureTest.java`
- Modify: `src/test/java/com/example/retinavision/architecture/AnalysisModuleArchitectureTest.java`
- Modify: `docs/PROJECT_ARCHITECTURE_GUIDE.md`
- Modify: `README.md` only if its package examples reference removed paths.
- Remove: empty `service/*/port`, `service/*/adapter`, `service/*/storage`, and obsolete root-level Service source directories after all tracked files have moved.

**Interfaces:**
- Consumes: every migrated package from Tasks 1-6.
- Produces: enforced final dependency rules and accurate architecture documentation.

- [ ] **Step 1: Enable final repository-wide architecture rules**

Rules must assert:

- all `service.<domain>.api` classes are interfaces;
- no direct Java types reside in `service.<domain>` roots;
- no classes under `application..` depend on `infrastructure..`;
- no classes under `port..` depend on `infrastructure..`;
- Controllers do not depend on `service..impl` or internal application Handler/Fixture/support packages;
- RetinaVision exception subclasses reside under `exception..`.

- [ ] **Step 2: Run architecture tests and fix only package/dependency violations**

Run: `mvn -Dtest=LayeredPackageStructureTest,LayerFunctionalPackageArchitectureTest,AnalysisModuleArchitectureTest test`

Expected: PASS. Do not suppress violations with broad exclusions; use only the documented `IllegalTaskTransitionException` domain value dependency exception.

- [ ] **Step 3: Search for stale old packages**

Run:

```powershell
rg -n 'service\.[a-z]+\.(port|adapter|storage)|service\.analysis\.ExecuteAnalysisTask|service\.agent\.(AgentContextCommandParser|AgentSkillRegistry|DoctorAgentSkillOrchestrator|PatientAgentSkillOrchestrator)|llm\.agent\.LlmAgentSkillRouter|ai\.analysis\.HttpAiInferenceGateway' src/main/java src/test/java
```

Expected: no Java source matches.

- [ ] **Step 4: Update architecture documentation and reading routes**

Document the final `application/service/port/infrastructure/exception` meanings, update class links, and replace old package examples. Keep Java details extensive and frontend/Python/Docker summaries unchanged.

- [ ] **Step 5: Run the full backend test suite**

Load the local Docker environment variables without printing values, then run: `mvn test`

Expected: all tests pass, including `RetinaVisionApplicationTests` against the configured development database.

- [ ] **Step 6: Run repository verification**

```powershell
powershell -ExecutionPolicy Bypass -File ./scripts/verify-repository.ps1
powershell -ExecutionPolicy Bypass -File ./scripts/verify-monorepo-layout.ps1
git diff --check master...HEAD
```

Expected: all commands exit 0.

- [ ] **Step 7: Perform final whole-branch review**

Review for accidental behavior changes, missing moved tests, stale imports, secrets, generated artifacts, and overly broad architecture exclusions. Any correction must rerun the focused and full verification from Steps 2, 5, and 6.

- [ ] **Step 8: Commit**

```bash
git add README.md docs src/main/java src/test/java
git commit -m "docs: document layered backend packages"
```
