# Java Layer Functional Subpackages Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Keep RetinaVision’s familiar Java technical-layer directories while dividing every crowded layer into consistent functional subpackages.

**Architecture:** Preserve `controller`, `service`, `mapper`, `pojo`, and other technical roots, then classify their contents with the same `auth/user/patient/cases/imaging/analysis/clinical/reporting/agent/knowledge/governance/operations/shared` taxonomy. Move packages mechanically in seven compilable batches, with Service organization first and all class names, contracts, SQL, message semantics, and business logic unchanged.

**Tech Stack:** Java 17, Spring Boot 3.5, Spring Security, MyBatis-Plus/MyBatis XML, Spring AMQP, Redis, Spring AI, JUnit 5, Mockito, ArchUnit, Maven.

**Spec:** `docs/superpowers/specs/2026-11-05-java-layer-functional-subpackages-design.md`

## Global Constraints

- Preserve all class names, method signatures, annotations, endpoint paths, JSON fields, enum values, SQL, database schema, queue names, routing keys, and message payloads.
- Move packages and update imports/type references only; do not merge Services, split classes, rename APIs, or rewrite dependencies.
- Use these functional names consistently: `auth`, `user`, `patient`, `cases`, `imaging`, `analysis`, `clinical`, `reporting`, `agent`, `knowledge`, `governance`, `operations`, `shared`.
- Use lowercase package segments; use `cases`, never Java keyword `case`.
- Keep Mapper XML files in `src/main/resources/mapper/`; modify only fully qualified Java names.
- Do not widen package-private members to make a move compile; move tightly coupled classes together or stop for a separate design.
- Preserve unrelated user changes and never stage frontend, Python, credentials, uploads, generated files, or local configuration.
- If baseline `mvn test` has unrelated environment failures, record the exact failing tests and require every targeted test to pass with no increase in the full-suite failure set.
- Each task must finish with verification and its own commit.

## Review Focus

- MyBatis XML namespaces and nested result types must resolve after Mapper/POJO moves; context startup must not report duplicate or unknown statements.
- Spring component and Mapper scanning must find every moved class exactly once without treating Service interfaces as Mappers.
- Analysis MQ/Outbox messages must retain identical JSON and ACK/retry/dead-letter behavior after package changes.
- JWT filter ordering, role checks, doctor ownership, and patient ownership must remain unchanged.
- No persisted Redis/DB/MQ value may depend on an old fully qualified class name; if one does, pause and design compatibility before moving it.

---

## Task 1: Baseline, Approved Taxonomy, and Migration Guardrails

**Files:**
- Create: `src/test/java/com/example/retinavision/architecture/LayerFunctionalPackageArchitectureTest.java`
- Modify: `src/test/java/com/example/retinavision/architecture/AnalysisModuleArchitectureTest.java`
- Modify: `docs/superpowers/specs/2026-11-05-java-package-by-feature-reorganization-design.md`
- Modify: `docs/superpowers/plans/2026-11-05-java-package-by-feature-reorganization.md`
- Create: `docs/superpowers/specs/2026-11-05-java-layer-functional-subpackages-design.md`
- Create: `docs/superpowers/plans/2026-11-05-java-layer-functional-subpackages.md`

**Interfaces:**
- Consumes: the current Java source tree and existing ArchUnit rules.
- Produces: an explicit migration baseline and approved functional taxonomy used by Tasks 2–7.

- [ ] **Step 1: Capture protected working-tree state**

Run:

```powershell
git status --short
git diff --name-only
```

Record pre-existing paths. Do not stage them unless explicitly listed in this plan.

- [ ] **Step 2: Run and record the Java baseline**

Run:

```powershell
mvn test
```

Record total/passed/failed/error counts and each failing test. Separate deterministic code failures from unavailable MySQL/Redis/RabbitMQ/LLM infrastructure.

- [ ] **Step 3: Add migration-period package tests**

Add JUnit/ArchUnit tests with these names:

```java
functional_names_are_from_the_approved_taxonomy()
technical_roots_are_explicitly_tracked_during_migration()
```

The test permits existing root-level classes temporarily but rejects unapproved functional names. It does not add new cross-layer dependency rules.

- [ ] **Step 4: Preserve the current Analysis architecture rules as a baseline**

Do not change production packages yet. Add comments or constants identifying the old Analysis paths that Task 5 will replace, while keeping the existing four rules passing against current code.

- [ ] **Step 5: Verify the guardrail tests**

Run:

```powershell
mvn -Dtest=LayerFunctionalPackageArchitectureTest,AnalysisModuleArchitectureTest test
```

Expected: PASS.

- [ ] **Step 6: Commit only design, plan, and guardrails**

Stage the four 2026-11-05 design/plan documents and two architecture tests only, verify with `git diff --cached --name-only`, then commit:

```powershell
git commit -m "docs: plan Java functional subpackage reorganization"
```

## Task 2: Organize Service Interfaces and Implementations

**Files:**
- Move: `src/main/java/com/example/retinavision/service/*.java` → `service/<function>/*.java`
- Move: `src/main/java/com/example/retinavision/service/impl/*.java` → `service/<function>/impl/*.java`
- Modify: all Java imports that reference moved Service types
- Modify: corresponding unit-test package declarations/imports only where needed

**Interfaces:**
- Consumes: every existing Service interface and implementation with unchanged signatures.
- Produces: functional Service groups; later tasks import these final Service packages.

**Ownership map:**

| Function | Service families |
|---|---|
| `auth` | `UserLogin`, `JwtBlacklist`, `LoginAttempt` |
| `user` | `UserAdministration`, `DoctorDirectory` |
| `patient` | `PatientProfile`, `PatientCase`, `PatientReportExplanation`, `SecurePatientNumberGenerator` |
| `cases` | `CaseService`, `ClinicalAccess`, `CaseAnalysisTimeline` |
| `imaging` | `ImageService` |
| `analysis` | `TaskService`, `AnalysisTaskExecution`, `AnalysisTaskRetry`, `AnalysisResult`, `AiTaskQuota`, `AiQuotaReservation` |
| `clinical` | `ResultReview`, `ResultHumanWorkflow`, `ClinicalTaskLog`, `DoctorReviewReminder` |
| `reporting` | `AnalysisReport`, `AiReportDraft`, `ReportPdfRenderer`, `ReportPdfDocument` |
| `agent` | `AgentChat`, `AgentClinicalReference`, `DoctorAgentQuery`, `PatientAgentQuery` |
| `knowledge` | `KnowledgeChat`, `KnowledgeIngestion` |
| `governance` | `AgentSkillAdministration`, `AgentSkillExecutionQuery`, `PromptTemplate`, `LlmCallLog`, `QualityControl` |
| `operations` | `SystemStatus`, `Statistics`, `DeadLetterRecovery` |

- [ ] **Step 1: Move Service interfaces by the ownership map**

Update package declarations only. Keep every method, generic type, annotation, record component, and nested type unchanged.

- [ ] **Step 2: Move implementations next to their interfaces**

Use `service/<function>/impl`. Keep default Spring bean names, `@Transactional`, constructor injection, and implementation bodies unchanged.

- [ ] **Step 3: Update production and test imports**

Use IDE-safe symbol moves or exact import replacement. Search for old packages:

```powershell
rg -n "com\.example\.retinavision\.service(\.impl)?\." src/main src/test
```

Expected: no imports of root-level Service classes; package declarations should exist only in functional subpackages.

- [ ] **Step 4: Verify Service behavior**

Run:

```powershell
mvn -Dtest=UserLoginServiceImplTest,PatientCaseServiceImplTest,CaseServiceImplTest,ImageServiceImplTest,AnalysisTaskExecutionServiceImplTest,ResultReviewServiceImplTest,AgentChatServiceImplTest,KnowledgeChatServiceImplTest,QualityControlServiceImplTest,SystemStatusServiceImplTest test
mvn test
```

Expected: targeted tests PASS; full-suite failures do not exceed Task 1 baseline.

- [ ] **Step 5: Commit the Service batch**

Stage only moved Service source files and import-only Java/test changes shown by `git status`. Verify cached diff contains no method-body changes, then commit:

```powershell
git commit -m "refactor: group Java services by function"
```

## Task 3: Organize Controllers, Authentication Filter, and Utilities

**Files:**
- Move: `controller/*.java` → `controller/<function>/*.java`
- Move: `filter/JwtAuthenticationFilter.java` → `filter/auth/JwtAuthenticationFilter.java`
- Move: `utils/JwtUtil.java`, `utils/ClientIpResolver.java` → `utils/auth/`
- Modify: Controller/filter/util imports and affected tests

**Interfaces:**
- Consumes: final Service packages from Task 2.
- Produces: functionally grouped HTTP entry points with unchanged routes and security behavior.

**Controller map:**

| Function | Controllers |
|---|---|
| `auth` | `UserLoginController` |
| `user` | `UserAdministrationController`, `UserDirectoryController` |
| `patient` | `PatientProfileController`, `PatientCaseController` |
| `cases` | `CaseController` |
| `imaging` | `ImageController` |
| `analysis` | `TaskController`, `ResultAnalysisController`, `AnalysisResultComparisonController` |
| `clinical` | `DoctorReviewController`, `ResultClinicalWorkflowController` |
| `agent` | `AgentController` |
| `knowledge` | `KnowledgeController` |
| `governance` | `AgentEvaluationController`, `AgentSkillController`, `AgentSkillExecutionController`, `PromptManagementController`, `PromptEvaluationController`, `RagEvaluationController`, `QualityControlController` |
| `operations` | `SystemStatusController`, `StatisticsController`, `DeadLetterController` |

- [ ] **Step 1: Move Controllers and update package/import declarations**

Keep every `@RequestMapping`, method annotation, validation annotation, authorization check, binary response header, and response type unchanged.

- [ ] **Step 2: Move JWT filter and authentication utilities**

Update `SecurityConfig` imports only. Preserve filter registration order, bearer-token parsing, blacklist checks, client-IP extraction, and exception behavior.

- [ ] **Step 3: Update Controller and security tests**

Mirror functional package names where test classes directly target moved Controllers; do not rewrite assertions.

- [ ] **Step 4: Verify HTTP/security behavior**

Run:

```powershell
mvn -Dtest=JwtAuthenticationFilterTest,JwtUtilTest,ImageControllerAccessTest,ResultClinicalWorkflowControllerTest,KnowledgeControllerTest,AgentEvaluationControllerTest,PromptManagementControllerTest test
mvn test
```

Expected: targeted tests PASS; routes and security assertions are unchanged.

- [ ] **Step 5: Commit the entry-point batch**

Stage only moved Controller/filter/util files and import-only callers/tests, inspect cached diff, then commit:

```powershell
git commit -m "refactor: group Java entry points by function"
```

## Task 4: Organize Mappers, POJOs, and Enumerations

**Files:**
- Move: `mapper/*.java` → `mapper/<function>/*.java`
- Move: `pojo/DTO/*.java`, `pojo/Entity/*.java`, `pojo/VO/*.java` → `pojo/<function>/dto|entity|vo/*.java`
- Move: `enumeration/*.java` → `enumeration/<function>/*.java`
- Modify: all production/test imports
- Modify: every affected `src/main/resources/mapper/*.xml` namespace/resultType/parameterType
- Modify: `src/main/java/com/example/retinavision/RetinaVisionApplication.java`

**Interfaces:**
- Consumes: final Controller and Service packages from Tasks 2–3.
- Produces: functional persistence/model packages and valid MyBatis statement bindings.

**Mapper ownership:**

| Function | Mapper families |
|---|---|
| `user` | `UserRegisterMapper` |
| `patient` | `PatientProfileMapper`, `PatientAgentQueryMapper` |
| `cases` | `CaseMapper`, `DoctorAgentQueryMapper` |
| `imaging` | `ImageMapper` |
| `analysis` | `TaskMapper`, `AnalysisResultMapper`, `LogMapper` |
| `clinical` | `AnalysisCorrectionMapper`, `AnalysisFeedbackMapper`, `AnalysisReviewMapper` |
| `reporting` | `AnalysisReportMapper` |
| `agent` | chat/session/context/session-skill/tool-call and runtime Skill Mappers |
| `knowledge` | knowledge document/chunk/chat Mappers |
| `governance` | evaluation, Prompt, execution-log, LLM-log and quality-control Mappers |
| `operations` | `StatisticsMapper` |

- [ ] **Step 1: Move Mapper interfaces by ownership**

Preserve method names, annotations, nested projection records/classes, `BaseMapper<T>` types, and parameter annotations.

- [ ] **Step 2: Move DTO/Entity/VO classes by primary owner**

Use lowercase `dto`, `entity`, `vo`. Preserve fields, Lombok/Jackson/MyBatis annotations, validation rules, constructors, records, and serialized names.

- [ ] **Step 3: Move enumerations by owner**

Preserve constant names and order. Shared usage does not move a business enum to `shared`; import it from its owner.

- [ ] **Step 4: Update Mapper scanning safely**

Keep:

```java
@MapperScan("com.example.retinavision.mapper")
```

Because only Mapper interfaces remain beneath this root. Do not scan all of `com.example.retinavision`.

- [ ] **Step 5: Update Mapper XML complete type names only**

For every XML file, update `namespace`, `resultType`, `parameterType`, and nested classes such as `$OverviewRow`. Do not alter SQL text or resource paths.

- [ ] **Step 6: Search for stale model/Mapper package names**

Run:

```powershell
rg -n "com\.example\.retinavision\.(mapper\.[A-Z]|pojo\.(DTO|Entity|VO)\.|enumeration\.[A-Z])" src/main src/test
```

Expected: no active-code matches.

- [ ] **Step 7: Verify persistence and object-level access**

Run:

```powershell
mvn -Dtest=SchemaMigrationContractTest,DoctorAgentQueryMapperContractTest,PatientAgentQueryMapperContractTest,ClinicalAccessServiceImplTest,PatientProfileServiceImplTest,CaseServiceImplTest test
mvn test
```

Expected: targeted tests PASS; no unknown statement, duplicate Mapper, field, enum, or authorization regression.

- [ ] **Step 8: Commit the persistence/model batch**

Stage only moved Mapper/model/enum files, XML type-reference changes, application scan import changes, and affected tests. Verify SQL diff contains no changed SQL tokens, then commit:

```powershell
git commit -m "refactor: group Java persistence models by function"
```

## Task 5: Flatten Existing Analysis Packages into the Technical Layout

**Files:**
- Move: `analysis/application/ExecuteAnalysisTask*` → `service/analysis/`
- Move: `analysis/application/port/out/*` → `service/analysis/port/`
- Move: `analysis/application/command/*` → `pojo/analysis/command/`
- Move: `analysis/application/model/*` → `pojo/analysis/dto|event/`
- Move: `analysis/domain/model/*` → `pojo/analysis/domain/`
- Move: `analysis/infrastructure/outbox/*` → `mq/analysis/outbox/`, `mapper/analysis/`, and `pojo/analysis/entity/` according to class role
- Move: `analysis/infrastructure/mq/*` and analysis-related root `mq/*` → `mq/analysis/`
- Move: `analysis/infrastructure/ai/*` and root `ai/*` → `ai/analysis/`
- Move: storage/image/persistence/reporting adapters → `service/analysis/storage|adapter/` without renaming classes
- Move: `analysis/infrastructure/config/*` and analysis-specific root config → `config/analysis/`
- Modify: `AnalysisOutboxMapper.xml`, Java imports, tests, and `AnalysisModuleArchitectureTest`
- Remove: empty `src/main/java/com/example/retinavision/analysis/`

**Interfaces:**
- Consumes: final Service/Mapper/POJO packages from Tasks 2 and 4.
- Produces: the same state machine, ports, Outbox, MQ, AI and storage behavior under the uniform technical-root organization.

- [ ] **Step 1: Move Analysis state model, commands, events, and ports**

Move tightly coupled package-private classes together. Preserve all constructors, records, enum values, transition logic, and exception types.

- [ ] **Step 2: Move use cases and execution handlers into `service/analysis`**

Keep `ExecuteAnalysisTaskUseCase`, handler delegation, transactions, and port signatures unchanged.

- [ ] **Step 3: Move technical adapters**

Classify by technical root: Mapper/Entity, MQ/Outbox, AI client, storage/adapter, and config. Preserve all annotations, schedules, transaction boundaries, HTTP paths, multipart name `file`, URL authority checks, and file-root checks.

- [ ] **Step 4: Rewrite Analysis ArchUnit rules for new paths**

Use these rule names and equivalent prior behavior:

```java
analysis_domain_models_are_framework_free
analysis_services_do_not_depend_on_concrete_mq_ai_or_storage_packages
analysis_domain_models_do_not_use_provider_or_filesystem_types
analysis_technical_adapters_are_not_used_by_unrelated_controllers
```

Add explicit baseline exceptions instead of rewriting production behavior.

- [ ] **Step 5: Update XML and eliminate old Analysis packages**

Run:

```powershell
rg -n "com\.example\.retinavision\.analysis\.(application|domain|infrastructure)" src/main src/test
```

Expected: no active-code matches.

- [ ] **Step 6: Verify Analysis contracts**

Run:

```powershell
mvn -Dtest=AnalysisModuleArchitectureTest,AnalysisTaskTest,ExecuteAnalysisTaskHandlerTest,AnalysisTaskExecutionServiceImplTest,AnalysisOutboxPublisherTest,AnalysisTaskListenerTest,MyBatisAnalysisTaskEventOutboxTest,HttpAiInferenceGatewayTest,LocalArtifactStoreTest test
mvn test
```

Expected: targeted tests PASS; task transitions, idempotent claim, Outbox publication, ACK/retry/dead-letter, AI and storage assertions are unchanged.

- [ ] **Step 7: Commit the Analysis flattening batch**

Stage only Analysis-related moves, imports, XML and tests. Review every non-package/import line, then commit:

```powershell
git commit -m "refactor: align analysis with functional layer packages"
```

## Task 6: Classify Agent, LLM, RAG, Configuration, Redis, and Remaining Roots

**Files:**
- Move: remaining top-level `agent/*.java` → `service/agent`, `pojo/agent`, `llm/agent`, or `service/agent/policy` by role
- Move: `agent/evaluation/*.java` → `service/governance`, `pojo/governance`, and governance configuration
- Move: `llm/*.java` → `llm/agent|governance|knowledge|reporting|shared`
- Move: `rag/*.java` → `rag/knowledge|governance`
- Move: `config/*.java` → `config/analysis|auth|clinical|governance|shared`
- Move: `redis/*.java` → `redis/auth|analysis|shared`
- Move: remaining `constant`, `common`, and `utils` classes into matching functional/shared subpackages
- Modify: all imports and affected tests
- Remove: empty top-level `agent` and other emptied roots

**Interfaces:**
- Consumes: final technical subpackages from Tasks 2–5.
- Produces: no unclassified online Agent, evaluation, LLM, RAG, config, Redis, constant, common, or utility classes.

- [ ] **Step 1: Classify online Agent classes by technical role**

Move orchestrators/catalogs to `service/agent`, context/request/response structures to `pojo/agent`, model gateways to `llm/agent`, and deterministic unsafe-request policy to `service/agent/policy`. Preserve class names and implementations.

- [ ] **Step 2: Classify Agent evaluation as governance**

Move Runner, Scorer, Fixture, eligibility and administration classes into governance technical packages. Preserve the single-thread executor, run states, thresholds, cancellation behavior, fixture isolation and approval gate.

- [ ] **Step 3: Classify LLM and RAG classes**

Place generic provider clients/properties under `llm/shared`; place Prompt/RAG evaluation under governance; place Qdrant/Embedding/retrieval/citation classes under knowledge; place report-specific draft scoring/generation under reporting. Do not change prompts, model parameters, citation logic, or safety policies.

- [ ] **Step 4: Classify configuration, Redis, constants and common types**

Keep every property prefix, bean name, executor name, Redis key, Lua behavior, queue setting and conditional annotation unchanged.

- [ ] **Step 5: Search for obsolete top-level Agent and unclassified packages**

Run:

```powershell
rg -n "^package com\.example\.retinavision\.agent(\.|;)" src/main/java
rg -n "^package com\.example\.retinavision\.(llm|rag|config|redis|constant|common|utils);" src/main/java
```

Expected: no matches.

- [ ] **Step 6: Verify Agent, RAG, governance, and Redis behavior**

Run:

```powershell
mvn -Dtest=DoctorAgentSkillOrchestratorTest,PatientAgentSkillOrchestratorTest,PatientAgentSecurityContractTest,AgentEvaluationServiceTest,AgentEvaluationScorerTest,LlmAgentSkillRouterTest,LlmOrchestrationServiceImplTest,QdrantDocumentRetrieverTest,RagGroundingValidatorTest,RedisJwtBlacklistServiceTest,RedisAiTaskQuotaServiceTest test
mvn test
```

Expected: targeted tests PASS; routing, parameters, role isolation, citations, evaluation thresholds, safety rejection and Redis semantics remain unchanged.

- [ ] **Step 7: Commit remaining technical-root classification**

Stage only these package moves/imports/tests and commit:

```powershell
git commit -m "refactor: classify Agent and platform Java packages"
```

## Task 7: Mirror Tests, Tighten Architecture Rules, and Update Documentation

**Files:**
- Modify/move: `src/test/java/com/example/retinavision/**` to mirror final functional subpackages where practical
- Modify: `LayerFunctionalPackageArchitectureTest.java`
- Modify: `AnalysisModuleArchitectureTest.java`
- Modify: `README.md`
- Modify: `docs/BACKEND.md`
- Modify: `docs/architecture/adr/0001-analysis-module-boundary.md`
- Remove: all empty legacy directories

**Interfaces:**
- Consumes: all final production packages.
- Produces: enforceable directory rules, navigable tests, and accurate documentation.

- [ ] **Step 1: Mirror production function names in test packages**

Move tests by the primary class under test. Keep test class names, methods, fixtures and assertions unchanged.

- [ ] **Step 2: Tighten root-level architecture assertions**

Assert that classes beneath each technical root reside in an approved functional subpackage, except intentionally small global roots `result` and `exception` and root `RetinaVisionApplication`. Explicitly disallow production classes directly in `controller`, `service`, `service.impl`, `mapper`, `pojo.DTO`, `pojo.Entity`, `pojo.VO`, and `enumeration`.

- [ ] **Step 3: Search for stale package declarations and complete class names**

Run:

```powershell
rg -n "^package com\.example\.retinavision\.(controller|service|mapper|enumeration);" src/main/java
rg -n "^package com\.example\.retinavision\.service\.impl;" src/main/java
rg -n "^package com\.example\.retinavision\.pojo\.(DTO|Entity|VO);" src/main/java
rg -n "com\.example\.retinavision\.analysis\.(application|domain|infrastructure)" src/main src/test
```

Expected: no active-code matches.

- [ ] **Step 4: Update documentation and ADR**

Document the technical-root/functional-subpackage tree. Amend ADR 0001 to state that Analysis behavioral boundaries remain tested after its physical package layout was normalized for navigability.

- [ ] **Step 5: Run final verification**

Run:

```powershell
mvn test
mvn clean package
powershell -ExecutionPolicy Bypass -File .\scripts\verify-repository.ps1
```

Expected: commands exit `0`, or only the exact Task 1 external-environment baseline failures remain. Report Java, frontend and Python results separately if the repository script covers all three.

- [ ] **Step 6: Audit the entire migration diff**

Use `git log`, `git diff --stat`, `git diff --summary`, and whitespace-insensitive review. Confirm non-package/import/XML-type changes are limited to architecture tests and documentation; SQL and method bodies are unchanged.

- [ ] **Step 7: Commit test organization and documentation**

Stage only test moves, architecture rules, README, backend docs and ADR, then commit:

```powershell
git commit -m "docs: finalize Java functional package organization"
```

## Final Acceptance Checklist

- [ ] `service` and `service.impl` no longer contain unclassified business classes.
- [ ] Controller, Mapper, POJO, enumeration and other technical roots use the same functional taxonomy.
- [ ] Existing top-level `analysis` and `agent` package structures are fully dismantled into technical roots.
- [ ] Class names, methods, endpoints, JSON, SQL, schema, queue settings and business logic are unchanged.
- [ ] Mapper XML namespaces/types load successfully and SQL text is unchanged.
- [ ] Security, clinical ownership, Agent role isolation, report visibility, RAG citations, evaluation gates, Analysis state, Outbox and MQ behavior pass their targeted tests.
- [ ] Full-suite failures do not exceed the recorded baseline; a green baseline remains green.
- [ ] No unrelated frontend, Python, credential, upload or user file is staged.
- [ ] Seven independent commits make the migration reviewable and reversible by batch.


