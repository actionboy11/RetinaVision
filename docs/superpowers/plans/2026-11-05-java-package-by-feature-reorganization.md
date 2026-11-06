# Java Package-by-Feature Reorganization Implementation Plan

> **Status: Superseded.** Do not execute this plan. The approved direction changed to technical top-level packages with functional subpackages; a replacement plan will be written after the replacement design is approved.

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Reorganize the RetinaVision Java backend into feature-first packages that are easy to navigate while preserving every existing class name and runtime behavior.

**Architecture:** Keep one Spring Boot/Maven application and move existing classes in sequential, compilable feature batches. Each feature owns its Controller, Service, Mapper, models, and optional technical subpackages; `analysis` is normalized from `application/domain/infrastructure` into the same feature-first structure without rewriting its state machine, ports, or adapters.

**Tech Stack:** Java 17, Spring Boot 3.5, Spring Security, MyBatis-Plus/MyBatis XML, Spring AMQP, Redis, Spring AI, JUnit 5, Mockito, ArchUnit, Maven.

**Spec:** `docs/superpowers/specs/2026-11-05-java-package-by-feature-reorganization-design.md`

## Global Constraints

- Only move packages and update references; preserve every existing class name and business implementation.
- Do not change HTTP paths, request/response fields, enum values, error codes, SQL, schema, Flyway migrations, queue names, routing keys, or message payloads.
- Do not merge duplicate services, rename methods, widen visibility to solve package-private access, or introduce new frameworks.
- Keep `analysis` state transitions, Outbox, publisher confirms, ACK/retry/dead-letter behavior, AI invocation, and idempotency unchanged.
- Keep doctor/patient/admin/researcher role boundaries and clinical object-level authorization unchanged.
- Preserve all existing user changes; never reset or overwrite unrelated frontend, AI, or working-tree files.
- Use `cases` as the package segment because `case` is a Java keyword.
- Mapper XML resource filenames stay in `src/main/resources/mapper/`; update only namespaces and fully qualified Java type names.
- Every task ends with targeted tests, full `mvn test`, and a separate functional commit.

## Review Focus

- MyBatis startup with moved Mapper interfaces: Mapper XML namespace/result types must resolve and the Spring context must start.
- RabbitMQ payload compatibility: moved message classes must keep identical JSON fields and listener behavior.
- Redis or persisted type metadata: no old fully qualified class name may remain where historical data depends on it; stop and design compatibility if found.
- Package-private access: moving a collaborator must not be “fixed” by widening visibility or rewriting the implementation.
- Security behavior: JWT filter ordering, role checks, and doctor/patient object ownership must remain identical after imports move.

---

## Target Package Map

| Feature | Target root | Primary existing code moved here |
|---|---|---|
| Shared | `com.example.retinavision.shared` | `result`, `exception`, stable constants/common types |
| Platform | `com.example.retinavision.platform` | generic Redis and LLM clients/configuration used by several features |
| Authentication | `com.example.retinavision.auth` | login/register, JWT, blacklist, login throttling, authentication filter |
| Users | `com.example.retinavision.user` | user administration, doctor directory, role and user persistence |
| Patients | `com.example.retinavision.patient` | patient profile and patient-owned inspection workflow |
| Cases | `com.example.retinavision.cases` | case CRUD/query, ownership/access checks, timeline |
| Imaging | `com.example.retinavision.imaging` | image metadata, upload/preview, image storage access |
| Analysis | `com.example.retinavision.analysis` | tasks, results, state machine, Outbox, MQ, Python AI client, artifact storage |
| Clinical | `com.example.retinavision.clinical` | feedback, correction, review, review queue/reminder |
| Reporting | `com.example.retinavision.reporting` | report draft, PDF, signing data and patient explanation |
| Agent | `com.example.retinavision.agent` | online chat, router, contexts, Native Skills, tool calling and runtime query services |
| Knowledge | `com.example.retinavision.knowledge` | ingestion, RAG, embedding, Qdrant, citations and knowledge chat |
| Governance | `com.example.retinavision.governance` | Prompt/Skill management, Agent/Prompt/RAG evaluation, audit and quality control |
| Operations | `com.example.retinavision.operations` | dashboard status, statistics, dead-letter inspection/recovery |

## Task 1: Freeze the Baseline and Add Migration Guardrails

**Files:**
- Create: `src/test/java/com/example/retinavision/architecture/PackageOrganizationArchitectureTest.java`
- Modify: `src/test/java/com/example/retinavision/architecture/AnalysisModuleArchitectureTest.java`
- Modify: `docs/superpowers/specs/2026-11-05-java-package-by-feature-reorganization-design.md`
- Create: `docs/superpowers/plans/2026-11-05-java-package-by-feature-reorganization.md`

**Interfaces:**
- Consumes: current production packages under `com.example.retinavision`.
- Produces: an ArchUnit migration baseline that allows existing legacy packages temporarily and a final rule ready to be enabled in Task 8.

- [ ] **Step 1: Record the protected working-tree baseline**

Run:

```powershell
git status --short
git diff --name-only
```

Save the output in the task notes; do not stage unrelated frontend or user files.

- [ ] **Step 2: Run the current Java baseline**

Run:

```powershell
mvn test
```

Expected: all existing Java tests pass before package movement. If they do not, record pre-existing failures and stop package migration until the baseline is understood.

- [ ] **Step 3: Add migration-safe architecture assertions**

Add tests named:

```java
legacy_top_level_packages_are_explicitly_tracked()
feature_roots_use_approved_names()
```

The first assertion contains the exact temporary legacy roots: `controller`, `service`, `mapper`, `pojo`, `enumeration`, `filter`, `redis`, `utils`, `result`, `exception`, `llm`, `rag`, `ai`, `mq`, `config`, `constant`, and `common`. The second accepts only the target feature roots plus `RetinaVisionApplication` while allowing the tracked legacy roots until Task 8.

- [ ] **Step 4: Preserve existing Analysis behavior rules during migration**

Keep the intent of the four existing `AnalysisModuleArchitectureTest` rules. Do not add a new prohibition on historical Controller-to-Mapper or cross-feature Mapper dependencies.

- [ ] **Step 5: Verify guardrails**

Run:

```powershell
mvn -Dtest=PackageOrganizationArchitectureTest,AnalysisModuleArchitectureTest test
mvn test
```

Expected: PASS.

- [ ] **Step 6: Commit the approved design and migration baseline**

```powershell
git add docs/superpowers/specs/2026-11-05-java-package-by-feature-reorganization-design.md docs/superpowers/plans/2026-11-05-java-package-by-feature-reorganization.md src/test/java/com/example/retinavision/architecture
git commit -m "docs: plan Java feature package reorganization"
```

## Task 2: Move Shared, Platform, Authentication, and User Packages

**Files:**
- Move: `src/main/java/com/example/retinavision/result/**` → `src/main/java/com/example/retinavision/shared/response/**`
- Move: `src/main/java/com/example/retinavision/exception/**` → `src/main/java/com/example/retinavision/shared/exception/**`
- Move: generic classes from `constant`, `common`, `redis`, and cross-feature `llm` → `shared/**` or `platform/**`
- Move: `UserLoginController`, `UserLoginService`, `UserLoginServiceImpl`, `JwtBlacklistService`, `RedisJwtBlacklistService`, `LoginAttemptService`, `RedisLoginAttemptService`, `JwtAuthenticationFilter`, `JwtUtil`, and `SecurityConfig` → `auth/controller|service|service/impl|filter|util|config`
- Move: `UserAdministrationController`, `UserDirectoryController`, `UserAdministrationService`, `UserAdministrationServiceImpl`, `DoctorDirectoryService`, `DoctorDirectoryServiceImpl`, and `UserRegisterMapper` → `user/controller|service|service/impl|mapper`
- Move models: `UserEntity`, `UserLoginDTO`, `UserRegisterDTO`, `UserLoginVO`, `CurrentUserVO`, `AdminUserItemVO`, `DoctorOptionVO`, `UpdateUserRoleDTO`, and `UserRole` → the owning feature’s `model/entity|dto|vo|enums`
- Modify: `src/main/java/com/example/retinavision/RetinaVisionApplication.java`
- Modify: affected Java tests and all Java imports

**Interfaces:**
- Consumes: existing authentication and user APIs and default Spring bean names.
- Produces: stable imports under `auth`, `user`, `platform`, and `shared`; all later tasks use `auth.model.vo.CurrentUserVO` and `shared.response.Result/PageResult`.

- [ ] **Step 1: Search for serialized or reflected old class names**

Run:

```powershell
rg -n "Class\.forName|JsonTypeInfo|activateDefaultTyping|com\.example\.retinavision\.(result|exception|filter|redis|utils|pojo\.(DTO|VO|Entity))" src/main src/test
```

Expected: only source/XML/import references that can be mechanically updated. If runtime data stores type IDs, stop and add a compatibility design before moving that type.

- [ ] **Step 2: Move shared/platform files and update package declarations**

Move files without changing class bodies. Keep `Result<T>`, `PageResult<T>`, exception constructors, Redis key values, LLM properties, and client behavior identical.

- [ ] **Step 3: Move authentication and user files**

Move each Controller/Service/Mapper/model into its target subpackage. Update imports in all production code; keep every annotation and method signature unchanged.

- [ ] **Step 4: Update scanning and tests**

Replace the single `@MapperScan("com.example.retinavision.mapper")` with an explicit list containing the legacy Mapper package plus Mapper packages already migrated in this task:

```java
@MapperScan(basePackages = {
        "com.example.retinavision.mapper",
        "com.example.retinavision.user.mapper"
})
```

Do not scan `com.example.retinavision` recursively because MyBatis would treat unrelated service interfaces as Mappers. Retain `AnalysisModuleConfiguration`’s class-based scan until Task 4, append each later feature’s exact `.mapper` package as it migrates, and remove the legacy Mapper package only in Task 8. Update authentication/user test packages and imports.

- [ ] **Step 5: Verify security and shared contracts**

Run:

```powershell
mvn -Dtest=JwtAuthenticationFilterTest,RedisJwtBlacklistServiceTest,RedisLoginAttemptServiceTest,UserLoginServiceImplTest,UserAdministrationServiceImplTest test
mvn test
```

Expected: PASS; no security matcher, token, role, response wrapper, or error behavior changes.

- [ ] **Step 6: Commit only this batch**

```powershell
git add src/main/java src/test/java src/main/resources/mapper
git commit -m "refactor: organize auth user and shared Java packages"
```

## Task 3: Move Patient, Cases, and Imaging Packages

**Files:**
- Move patient: `PatientProfileController`, `PatientCaseController`, `PatientProfileService*`, `PatientCaseService*`, `SecurePatientNumberGenerator`, `PatientProfileMapper`, and patient profile/account/progress models → `patient/**`
- Move cases: `CaseController`, `CaseService*`, `ClinicalAccessService*`, `CaseAnalysisTimelineService*`, `CaseMapper`, case DTO/VO/entity/enums → `cases/**`
- Move imaging: `ImageController`, `ImageService*`, `ImageMapper`, `ImageFileEntity`, `ImageFileItemVO`, `ImageQualitySummaryVO`, `ImageStatus`, `ImageQualityStatus` → `imaging/**`
- Modify: `src/main/resources/mapper/CaseMapper.xml`
- Modify: affected Java imports and tests

**Interfaces:**
- Consumes: `auth.model.vo.CurrentUserVO`, shared response/exception types, existing Mapper SQL.
- Produces: patient, case, and imaging feature APIs used by analysis, clinical, reporting, and Agent runtime.

- [ ] **Step 1: Move patient classes and update imports**

Keep patient number generation, profile ownership, account linking, and patient-visible projections unchanged.

- [ ] **Step 2: Move case classes and update imports**

Use `com.example.retinavision.cases`; do not use the reserved segment `case`. Keep `ClinicalAccessService` method signatures and not-found behavior unchanged.

- [ ] **Step 3: Move imaging classes and update imports**

Keep multipart limits, path normalization, file-root checks, preview/download response behavior, and Object IDs unchanged.

- [ ] **Step 4: Update MyBatis XML types**

Update `CaseMapper.xml` namespace/result types and every other XML reference to moved case, patient, and imaging classes. Do not modify SQL text.

- [ ] **Step 5: Verify object access and file safety**

Run:

```powershell
mvn -Dtest=PatientProfileServiceImplTest,PatientCaseServiceImplTest,CaseServiceImplTest,ClinicalAccessServiceImplTest,ImageControllerAccessTest,ImageServiceImplTest test
mvn test
```

Expected: PASS; cross-doctor and cross-patient access remains rejected exactly as before.

- [ ] **Step 6: Commit only this batch**

```powershell
git add src/main/java src/test/java src/main/resources/mapper
git commit -m "refactor: organize patient case and imaging Java packages"
```

## Task 4: Normalize the Analysis Package

**Files:**
- Move: `analysis/application/ExecuteAnalysisTask*` and legacy task/result services → `analysis/service|service/impl`
- Move: `analysis/application/port/out/**` → `analysis/service/port/**`
- Move: `analysis/domain/model/**` and application command/model types → `analysis/model/domain|command|event|dto`
- Move: global `TaskController`, `ResultAnalysisController`, `AnalysisResultComparisonController` → `analysis/controller`
- Move: `TaskMapper`, `AnalysisResultMapper`, `LogMapper`, task/result/log entities/DTO/VO/enums → `analysis/mapper|model/**`
- Move: `analysis/infrastructure/outbox/**` → `analysis/mq/outbox/**`
- Move: `analysis/infrastructure/mq/**` and global `mq/**` analysis listeners/messages → `analysis/mq/**`
- Move: `analysis/infrastructure/ai/**` and global `ai/**` inference classes → `analysis/client/**`
- Move: `analysis/infrastructure/storage/**` → `analysis/storage/**`
- Move: persistence/image/reporting adapters → `analysis/mapper/adapter`, `analysis/service/adapter`, or the owning feature package without renaming classes
- Move: `analysis/infrastructure/config/**`, `RabbitMqConfig`, analysis properties → `analysis/config/**`
- Modify: `src/main/resources/mapper/AnalysisOutboxMapper.xml`, `TaskMapper.xml`, `StatisticsMapper.xml`, and every XML referencing task/result/log types
- Modify: `AnalysisModuleArchitectureTest.java`

**Interfaces:**
- Consumes: case/image services and Mappers, platform AI/HTTP facilities, RabbitMQ configuration.
- Produces: the same task creation/execution/result/retry APIs and identical MQ/Outbox behavior under the uniform analysis directory.

- [ ] **Step 1: Move analysis model and service interfaces**

Move the state machine, transition types, commands, execution handler/use case, and ports first. Preserve constructors, records, enum constants, and package-private visibility by moving tightly coupled classes together.

- [ ] **Step 2: Update the Analysis architecture test to new packages**

Replace old path rules with equivalent rules:

```java
analysis_model_is_framework_free
analysis_service_does_not_depend_on_client_storage_or_mq_implementations
analysis_model_does_not_use_provider_or_filesystem_types
analysis_technical_implementations_are_not_used_by_unrelated_controllers
```

Keep any necessary baseline exceptions explicit; do not refactor production dependencies to satisfy a new rule.

- [ ] **Step 3: Move Controllers, legacy services, Mappers, and models**

Move all task/result API classes and legacy compatibility facades. Keep facade delegation, transactions, task status writes, and log creation unchanged.

- [ ] **Step 4: Move Outbox, MQ, client, storage, and configuration classes**

Keep exchange/queue/routing-key values, publisher confirm handling, manual ACK decisions, retry limits, HTTP endpoints, multipart field `file`, artifact URL validation, and file paths unchanged.

- [ ] **Step 5: Update Mapper XML and all complete class-name references**

Update namespaces and Java types only. Confirm no source/resource references remain under `analysis.application`, `analysis.domain`, or `analysis.infrastructure`.

Run:

```powershell
rg -n "com\.example\.retinavision\.analysis\.(application|domain|infrastructure)" src/main src/test
```

Expected: no active-code matches.

- [ ] **Step 6: Verify analysis behavior**

Run:

```powershell
mvn -Dtest=AnalysisModuleArchitectureTest,AnalysisTaskTest,ExecuteAnalysisTaskHandlerTest,AnalysisTaskExecutionServiceImplTest,AnalysisOutboxPublisherTest,AnalysisTaskListenerTest test
mvn test
```

Expected: PASS; state transitions, idempotent claim, Outbox and MQ tests retain their previous assertions.

- [ ] **Step 7: Commit only this batch**

```powershell
git add src/main/java src/test/java src/main/resources/mapper
git commit -m "refactor: normalize analysis Java package structure"
```

## Task 5: Move Clinical and Reporting Packages

**Files:**
- Move clinical Controllers: `DoctorReviewController`, clinical portions of `ResultClinicalWorkflowController` → `clinical/controller`
- Move clinical services: `ResultReviewService*`, `ResultHumanWorkflowService*`, `ClinicalTaskLogService*`, `DoctorReviewReminderService*` → `clinical/service|service/impl`
- Move clinical Mappers/models: correction, feedback, review Mappers/entities/DTO/VO/enums → `clinical/mapper|model/**`
- Move reporting services: `AnalysisReportService*`, `AiReportDraftService*`, `PatientReportExplanationService*`, `ReportPdfRenderer`, `JdkReportPdfRenderer`, `ReportPdfDocument` → `reporting/service|service/impl`
- Move reporting Mapper/models: `AnalysisReportMapper`, report entity/DTO/VO/status → `reporting/mapper|model/**`
- Modify: affected Mapper XML, imports, and tests

**Interfaces:**
- Consumes: cases access service, analysis results/tasks, auth current user, platform LLM client.
- Produces: unchanged review, correction, signing, PDF, and patient report explanation APIs.

- [ ] **Step 1: Move clinical classes**

Keep review preconditions, correction versioning, assigned-doctor checks, audit timestamps, and reminder behavior unchanged.

- [ ] **Step 2: Move reporting classes**

Keep draft generation, signed-report visibility, PDF bytes/headers, and patient explanation safety unchanged. If `ResultClinicalWorkflowController` serves both responsibilities, place it under `clinical/controller` in this mechanical pass rather than splitting the class.

- [ ] **Step 3: Update XML and imports**

Update only fully qualified Java names. Do not change report/review SQL or endpoint annotations.

- [ ] **Step 4: Verify clinical and report boundaries**

Run:

```powershell
mvn -Dtest=ResultClinicalWorkflowControllerTest,ResultReviewServiceImplTest,ResultHumanWorkflowServiceImplTest,DoctorReviewReminderServiceImplTest,PatientReportExplanationServiceImplTest,JdkReportPdfRendererTest test
mvn test
```

Expected: PASS; unsigned reports remain unavailable to patients and other doctors’ cases remain inaccessible.

- [ ] **Step 5: Commit only this batch**

```powershell
git add src/main/java src/test/java src/main/resources/mapper
git commit -m "refactor: organize clinical and reporting Java packages"
```

## Task 6: Move Knowledge and Online Agent Runtime Packages

**Files:**
- Move knowledge: `KnowledgeController`, `KnowledgeChatService*`, `KnowledgeIngestionService*`, knowledge Mappers/models/enums, and `rag/**` runtime retrieval classes → `knowledge/controller|service|service/impl|mapper|model|retrieval|client|policy`
- Move online Agent: `AgentController`, `AgentChatService*`, `AgentClinicalReferenceService*`, `DoctorAgentQueryService*`, `PatientAgentQueryService*`, query Mappers, chat/session/context Mappers/models, and current non-evaluation `agent/**` → `agent/controller|service|service/impl|mapper|model|router|context|tool|policy`
- Keep runtime Skill catalog/session binding classes under `agent`
- Leave `agent/evaluation/**`, Agent evaluation Controller/Mapper/models, Skill administration, Prompt management, and audit classes for Task 7
- Modify: `Knowledge*.xml`, `DoctorAgentQueryMapper.xml`, `PatientAgentQueryMapper.xml`, Agent chat/context Mapper XML, imports, and tests

**Interfaces:**
- Consumes: cases/patient/analysis/reporting query APIs, auth current user, platform LLM client.
- Produces: unchanged RAG chat and online doctor/patient Agent behavior; governance evaluation can import these public runtime interfaces in Task 7.

- [ ] **Step 1: Separate runtime RAG classes from evaluation classes**

Move embedding, Qdrant, audience policy, retrieval, grounding validation, and knowledge chat to `knowledge`. Leave `RagEvaluationService`/scorer and evaluation persistence for governance.

- [ ] **Step 2: Move knowledge Controller, services, persistence, and models**

Keep audience filtering, chunk IDs, citation validation, Qdrant collection/configuration, and fallback answers unchanged.

- [ ] **Step 3: Move online Agent runtime classes**

Group existing classes without renaming: Router classes under `agent.router`, context classes under `agent.context`, Tool Calling classes under `agent.tool`, unsafe request classes under `agent.policy`, orchestrators and runtime catalogs under `agent.service`, and request/response structures under `agent.model`.

- [ ] **Step 4: Move Agent query/chat persistence and XML types**

Update namespaces/result types for doctor/patient queries, chat sessions/messages, context and tool-call records. Do not modify SQL filters such as `assigned_doctor_id`, ordering, or page-size limits.

- [ ] **Step 5: Verify RAG, multi-role Agent, and security contracts**

Run:

```powershell
mvn -Dtest=KnowledgeControllerTest,KnowledgeChatServiceImplTest,KnowledgeIngestionServiceImplTest,QdrantDocumentRetrieverTest,AgentChatServiceImplTest,DoctorAgentSkillOrchestratorTest,PatientAgentSkillOrchestratorTest,PatientAgentSecurityContractTest,DefaultAgentToolFactoryTest test
mvn test
```

Expected: PASS; doctor/patient Skills, current-page references, knowledge citations, tool whitelist, and unsafe-request rejection remain unchanged.

- [ ] **Step 6: Commit only this batch**

```powershell
git add src/main/java src/test/java src/main/resources/mapper
git commit -m "refactor: organize knowledge and Agent runtime packages"
```

## Task 7: Move Governance and Operations Packages

**Files:**
- Move governance Controllers: `AgentEvaluationController`, `AgentSkillController`, `AgentSkillExecutionController`, `PromptManagementController`, `PromptEvaluationController`, `RagEvaluationController`, `QualityControlController` → `governance/**/controller`
- Move current `agent/evaluation/**` → `governance/agent/evaluation/**`
- Move Skill administration/execution query, Prompt template/evaluation, RAG evaluation, LLM call audit, quality-control services → `governance/**/service|service/impl`
- Move corresponding Mappers/entities/DTO/VO → `governance/**/mapper|model/**`
- Move operations Controllers/services/Mappers/models: `SystemStatus*`, `Statistics*`, `DeadLetter*`, queue/status/statistics models → `operations/**`
- Modify: all governance/operations Mapper XML, imports, and tests

**Interfaces:**
- Consumes: public Agent runtime interfaces, knowledge retrieval contracts, analysis recovery APIs, platform LLM client.
- Produces: unchanged administration, evaluation, approval, audit, quality-control, dashboard, statistics, and dead-letter endpoints.

- [ ] **Step 1: Move Agent evaluation and governance code**

Move Fixture, Runner, Scorer, eligibility, administration, dataset/run/result bindings and Controller/API types under `governance.agent`. Preserve the single-thread executor, run states, thresholds, cancellation, fixture isolation, and approval rules.

- [ ] **Step 2: Move Prompt, Skill, RAG, audit, and quality-control code**

Organize subfeatures under `governance/prompt`, `governance/skill`, `governance/rag`, `governance/audit`, and `governance/quality`. Runtime Agent may continue importing existing Skill model/Mapper classes if moving them would otherwise require a facade; document the baseline dependency instead of rewriting it.

- [ ] **Step 3: Move operations code**

Move system health, statistics, queue projections, dead-letter inspection and recovery. Keep operational recovery calls delegated to analysis services and preserve all endpoint paths.

- [ ] **Step 4: Update Mapper XML and full class names**

Update governance and operations namespaces/result types, including nested Mapper row types such as `QualityControlMapper$OverviewRow`. Do not change SQL.

- [ ] **Step 5: Verify governance and operations behavior**

Run:

```powershell
mvn -Dtest=AgentEvaluationControllerTest,AgentEvaluationServiceTest,AgentEvaluationScorerTest,AgentSkillAdministrationServiceTest,PromptManagementControllerTest,PromptEvaluationServiceTest,RagEvaluationServiceTest,QualityControlServiceImplTest,SystemStatusServiceImplTest,DeadLetterRecoveryServiceImplTest test
mvn test
```

Expected: PASS; evaluation thresholds, human approval gate, sensitive audit redaction, dashboard metrics, and dead-letter recovery remain unchanged.

- [ ] **Step 6: Commit only this batch**

```powershell
git add src/main/java src/test/java src/main/resources/mapper
git commit -m "refactor: organize governance and operations Java packages"
```

## Task 8: Remove Legacy Package Roots and Finish Documentation

**Files:**
- Modify: `src/test/java/com/example/retinavision/architecture/PackageOrganizationArchitectureTest.java`
- Modify: `src/test/java/com/example/retinavision/architecture/AnalysisModuleArchitectureTest.java`
- Modify: `src/main/java/com/example/retinavision/RetinaVisionApplication.java`
- Modify: `README.md`
- Modify: `docs/BACKEND.md`
- Modify: `docs/architecture/adr/0001-analysis-module-boundary.md`
- Remove: empty legacy directories under `controller`, `service`, `mapper`, `pojo`, `enumeration`, `filter`, `redis`, `utils`, `result`, `exception`, `llm`, `rag`, `ai`, `mq`, `constant`, and `common`

**Interfaces:**
- Consumes: all feature packages completed in Tasks 2–7.
- Produces: final enforceable package organization and accurate backend documentation.

- [ ] **Step 1: Search for legacy production packages and stale complete names**

Run:

```powershell
rg -n "^package com\.example\.retinavision\.(controller|service|mapper|pojo|enumeration|filter|redis|utils|result|exception|llm|rag|ai|mq|constant|common)(\.|;)" src/main/java
rg -n "com\.example\.retinavision\.analysis\.(application|domain|infrastructure)" src/main src/test
```

Expected: no production matches. Investigate every match rather than blanket replacing documentation or migration history.

- [ ] **Step 2: Tighten PackageOrganizationArchitectureTest**

Replace the temporary legacy allowlist with a rule that production classes may reside only in:

```text
auth, user, patient, cases, imaging, analysis, clinical, reporting,
agent, knowledge, governance, operations, platform, shared
```

Allow the root `RetinaVisionApplication` class explicitly. Keep historical cross-module dependencies outside the scope of this rule.

- [ ] **Step 3: Verify Mapper scanning and Spring context**

Run the existing context-load and Mapper tests. Confirm all feature Mappers are detected exactly once and no duplicate/unknown statements are logged.

- [ ] **Step 4: Update documentation**

Update directory trees and architecture explanations to describe feature-first packages. Amend ADR 0001 to record that the original Analysis layering was later normalized for repository navigability while its behavioral boundaries remained tested.

- [ ] **Step 5: Run full repository verification**

Run:

```powershell
mvn test
mvn clean package
powershell -ExecutionPolicy Bypass -File .\scripts\verify-repository.ps1
```

Expected: all commands exit `0`. If the repository script also runs frontend/Python checks, report their exact results separately.

- [ ] **Step 6: Review the final diff for accidental behavior edits**

Run:

```powershell
git diff --stat HEAD~7..HEAD
git diff --summary HEAD~7..HEAD
git status --short
```

Inspect every non-package/import/XML-type change. Unrelated user files must remain unstaged and unmodified by this work.

- [ ] **Step 7: Commit final guardrails and documentation**

```powershell
git add src/main/java src/test/java README.md docs/BACKEND.md docs/architecture/adr/0001-analysis-module-boundary.md
git commit -m "docs: finalize Java feature package organization"
```

## Final Acceptance Checklist

- [ ] Java production code is organized under the 14 approved feature/platform/shared roots.
- [ ] `analysis` no longer contains `application/domain/infrastructure` directories.
- [ ] Existing class names, method signatures, HTTP contracts, SQL, database schema, queue settings, and payloads are unchanged.
- [ ] All Mapper XML namespaces and type references resolve.
- [ ] Security, object ownership, Agent safety, RAG citation, report visibility, task state, Outbox, MQ and dead-letter tests pass.
- [ ] No unrelated frontend, Python, local configuration, credentials, uploads, or user changes are staged.
- [ ] Each migration batch exists as a separate commit.
- [ ] `mvn test`, `mvn clean package`, and repository verification pass with recorded output.


