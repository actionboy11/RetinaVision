# Patient Agent Natural Language Service Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a patient-facing Agent that answers natural-language questions about the signed-in patient's cases, progress, image re-upload guidance, signed reports, and patient-safe medical knowledge without granting write access.

**Architecture:** Add four patient Skill definitions and route them through a dedicated `PatientAgentSkillOrchestrator`. Deterministic patient data is read through doctor-independent, account-scoped Mapper SQL and returned as structured data; only signed-report explanation and patient-audience knowledge answers use the LLM, with structured data retained when generation fails.

**Tech Stack:** Java 17, Spring Boot 3.5.x, Spring AI 1.1.8, MyBatis-Plus/MySQL/Flyway, Vue 3, TypeScript, Element Plus, Vitest-free compile/build verification, JUnit 5/Mockito/AssertJ.

**Spec:** `docs/superpowers/specs/2026-10-07-patient-agent-natural-language-service-design.md`

## Global Constraints

- `USER` remains the technical role code and is displayed as “患者”.
- Patient Agent is read-only; upload, submit, withdraw, delete, task creation, retry, cancellation, review, and signing remain business-page operations.
- Every patient case/report SQL query must directly scope by the current account's `patient_profile.account_user_id`; do not fetch globally and filter in Java.
- Patient responses must not contain raw AI result JSON, mask/image paths, quality scores or thresholds, model metadata, task logs, drafts, or unsigned doctor opinions.
- Image quality is exposed only as `CHECKING`, `ACCEPTABLE`, `REUPLOAD_RECOMMENDED`, or `UNAVAILABLE` plus a Java-controlled patient-safe reason.
- Case ordering is `medical_case.updated_at DESC, medical_case.id DESC`; page size defaults to and is capped at 10.
- Only signed report fields approved by the spec may be sent to the LLM; PDF bytes, paths, images, masks, and draft history are forbidden.
- The assistant never predicts completion time and never generates a diagnosis or treatment plan.
- Model-generated URLs are never executed; Java creates paths only for an allowlisted action enum.
- Keep existing `POST /agent/chat` and message-history APIs backward compatible.

## Review Focus

- A patient with no cases asks “查看最近一次检查”: return a compact empty result instead of selecting another patient's latest case; pinned by Task 4 orchestrator tests.
- A quality task contains an unknown/malformed failure string: return `UNAVAILABLE` and a generic message without leaking the raw error; pinned by Task 2 presenter tests.
- A patient selects “第二个” after the current page changed or expired: reject the stale reference and ask for a fresh list; pinned by Task 4 context tests.
- Report data loads but explanation generation times out or returns unsafe text: preserve the signed-report card and mark explanation unavailable; pinned by Task 3 and Task 5 tests.
- A stored or model-produced action contains an arbitrary path: the frontend refuses navigation and only accepts Java-generated patient routes; pinned by Task 6 type/action tests and browser smoke verification.

---

### Task 1: Patient Skill Governance and Migration

**Files:**
- Create: `src/main/resources/db/migration/V21__patient_agent_skills.sql`
- Modify: `src/main/java/com/example/retinavision/agent/AgentSkillCode.java`
- Modify: `src/main/java/com/example/retinavision/agent/AgentReferenceType.java`
- Modify: `src/main/java/com/example/retinavision/agent/AgentSkillRegistry.java`
- Modify: `src/test/java/com/example/retinavision/agent/AgentSkillRegistryTest.java`
- Modify: `src/test/java/com/example/retinavision/migration/SchemaMigrationContractTest.java`

**Interfaces:**
- Consumes: existing `agent_skill`, `agent_skill_version`, version binding, evaluation thresholds, and `AgentSkillExecutionMode`.
- Produces: `MY_CASE_LIST`, `MY_CASE_PROGRESS`, `MY_SIGNED_REPORT`, and `PATIENT_KNOWLEDGE_QA`; `REPORT` reference type; patient-only role/argument schemas used by Tasks 4-7.

- [ ] **Step 1: Write failing registry and migration contract tests**

Add tests asserting all four codes are available to `UserRole.USER`, unavailable to `DOCTOR/ADMIN`, execute in `DIRECT` mode except `PATIENT_KNOWLEDGE_QA`, reject unknown arguments, and exist as active v1 rows in V21. Assert page/sort/tool permissions are not database-configurable.

- [ ] **Step 2: Run the focused tests and verify RED**

Run: `mvn -Dtest=AgentSkillRegistryTest,SchemaMigrationContractTest test`

Expected: FAIL because patient Skill enum values and V21 do not exist.

- [ ] **Step 3: Implement the four Skill definitions**

Use these fixed argument schemas:

- `MY_CASE_LIST`: `reuploadOnly` and `signedReportOnly`, both `TRUE|FALSE`.
- `MY_CASE_PROGRESS`: optional `caseReference` string.
- `MY_SIGNED_REPORT`: optional `caseReference`, `resultId`, `version`, and `mode=LIST|VIEW|EXPLAIN`.
- `PATIENT_KNOWLEDGE_QA`: no business-data arguments and `TOOL_CALLING` execution mode.

Seed v1 routing examples, negative examples, workflow instructions, answer style, and error messages. New versions remain inactive by default; v1 becomes active on first migration. No `docker/mysql/init.sql` table duplication is required because that file delegates all business schema and seed changes to Flyway.

- [ ] **Step 4: Run focused tests and verify GREEN**

Run: `mvn -Dtest=AgentSkillRegistryTest,SchemaMigrationContractTest test`

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/resources/db/migration/V21__patient_agent_skills.sql \
  src/main/java/com/example/retinavision/agent/AgentSkillCode.java \
  src/main/java/com/example/retinavision/agent/AgentReferenceType.java \
  src/main/java/com/example/retinavision/agent/AgentSkillRegistry.java \
  src/test/java/com/example/retinavision/agent/AgentSkillRegistryTest.java \
  src/test/java/com/example/retinavision/migration/SchemaMigrationContractTest.java
git commit -m "feat: define patient agent skills"
```

### Task 2: Account-Scoped Patient Query Layer

**Files:**
- Create: `src/main/java/com/example/retinavision/agent/PatientCaseSearchCriteria.java`
- Create: `src/main/java/com/example/retinavision/agent/PatientQualityDisplayStatus.java`
- Create: `src/main/java/com/example/retinavision/agent/PatientQualityPresenter.java`
- Create: `src/main/java/com/example/retinavision/mapper/PatientAgentQueryMapper.java`
- Create: `src/main/resources/mapper/PatientAgentQueryMapper.xml`
- Create: `src/main/java/com/example/retinavision/service/PatientAgentQueryService.java`
- Create: `src/main/java/com/example/retinavision/service/impl/PatientAgentQueryServiceImpl.java`
- Create: `src/main/java/com/example/retinavision/pojo/VO/PatientAgentCaseSummaryVO.java`
- Create: `src/main/java/com/example/retinavision/pojo/VO/PatientAgentProgressVO.java`
- Create: `src/main/java/com/example/retinavision/pojo/VO/PatientAgentProgressStageVO.java`
- Create: `src/main/java/com/example/retinavision/pojo/VO/PatientAgentSignedReportVO.java`
- Create: `src/main/java/com/example/retinavision/pojo/VO/PatientAgentReportDetailVO.java`
- Test: `src/test/java/com/example/retinavision/service/impl/PatientAgentQueryServiceImplTest.java`
- Test: `src/test/java/com/example/retinavision/mapper/PatientAgentQueryMapperContractTest.java`
- Test: `src/test/java/com/example/retinavision/agent/PatientQualityPresenterTest.java`

**Interfaces:**
- Consumes: `PageResult<T>`, `CurrentUserVO`, `patient_profile`, `medical_case`, image/task/result/review/report tables, and existing patient role semantics.
- Produces: `PatientAgentQueryService.listMyCases(...)`, `getMyCaseProgress(...)`, `listMySignedReports(...)`, and `getMySignedReport(...)` for Tasks 3-5.

- [ ] **Step 1: Write failing query, privacy, and quality-presentation tests**

Use these exact signatures:

```java
PageResult<PatientAgentCaseSummaryVO> listMyCases(PatientCaseSearchCriteria criteria,
                                                   int page, int pageSize, CurrentUserVO patient);
PatientAgentProgressVO getMyCaseProgress(String caseReference, CurrentUserVO patient);
PageResult<PatientAgentSignedReportVO> listMySignedReports(String caseReference,
                                                           int page, int pageSize, CurrentUserVO patient);
PatientAgentReportDetailVO getMySignedReport(String caseReference, Long resultId,
                                             Integer version, CurrentUserVO patient);
```

Assert account scoping, latest-first ordering, page-size cap, empty results, signed-only reports, case-number/internal-reference resolution, four progress stages, no predicted completion time, and no forbidden technical fields. The XML contract test must assert every select joins `patient_profile` with `account_user_id = #{userId}` before applying case/report references.

- [ ] **Step 2: Run focused tests and verify RED**

Run: `mvn -Dtest=PatientAgentQueryServiceImplTest,PatientAgentQueryMapperContractTest,PatientQualityPresenterTest test`

Expected: FAIL because the patient Agent query types and Mapper do not exist.

- [ ] **Step 3: Implement Mapper projections and service validation**

`PatientQualityPresenter.present(rawStatus, rawReason)` returns only the four display statuses. Unknown status/reason maps to `UNAVAILABLE` with a generic message; it never echoes raw errors. `PatientAgentReportDetailVO` contains only `caseId`, `caseNo`, `resultId`, `version`, `signedAt`, `signerName`, `findings`, `conclusion`, and `recommendation`.

- [ ] **Step 4: Run focused tests and verify GREEN**

Run: `mvn -Dtest=PatientAgentQueryServiceImplTest,PatientAgentQueryMapperContractTest,PatientQualityPresenterTest test`

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/example/retinavision/agent/Patient* \
  src/main/java/com/example/retinavision/mapper/PatientAgentQueryMapper.java \
  src/main/resources/mapper/PatientAgentQueryMapper.xml \
  src/main/java/com/example/retinavision/service/PatientAgentQueryService.java \
  src/main/java/com/example/retinavision/service/impl/PatientAgentQueryServiceImpl.java \
  src/main/java/com/example/retinavision/pojo/VO/PatientAgent* \
  src/test/java/com/example/retinavision/service/impl/PatientAgentQueryServiceImplTest.java \
  src/test/java/com/example/retinavision/mapper/PatientAgentQueryMapperContractTest.java \
  src/test/java/com/example/retinavision/agent/PatientQualityPresenterTest.java
git commit -m "feat: add patient-scoped agent queries"
```

### Task 3: Signed Report Explanation and Patient Knowledge Boundary

**Files:**
- Create: `src/main/resources/db/migration/V22__patient_report_explanation_prompt.sql`
- Create: `src/main/java/com/example/retinavision/agent/PatientReportExplanationResult.java`
- Create: `src/main/java/com/example/retinavision/service/PatientReportExplanationService.java`
- Create: `src/main/java/com/example/retinavision/service/impl/PatientReportExplanationServiceImpl.java`
- Modify: `src/main/java/com/example/retinavision/llm/PromptScenario.java`
- Modify: `src/main/java/com/example/retinavision/llm/LlmSafetyPolicy.java`
- Modify: `src/test/java/com/example/retinavision/migration/SchemaMigrationContractTest.java`
- Test: `src/test/java/com/example/retinavision/service/impl/PatientReportExplanationServiceImplTest.java`
- Modify: `src/test/java/com/example/retinavision/rag/KnowledgeAudiencePolicyTest.java`

**Interfaces:**
- Consumes: `PatientAgentReportDetailVO` from Task 2, `LlmOrchestrationService.generateJson(...)`, and knowledge audience filtering.
- Produces: `PatientReportExplanationService.explain(PatientAgentReportDetailVO)` returning `PatientReportExplanationResult`; prompt code `PATIENT_SIGNED_REPORT_EXPLANATION` for Task 4.

- [ ] **Step 1: Write failing explanation, migration, safety, and audience tests**

Assert the sanitized JSON sent to the LLM contains only the report detail allowlist, output requires a nonblank `explanation`, high-risk diagnostic wording is rejected, and any model timeout/protocol/safety failure returns `available=false` without dropping the source report. Assert patient RAG permits only `PUBLIC` and `PATIENT` documents.

- [ ] **Step 2: Run focused tests and verify RED**

Run: `mvn -Dtest=PatientReportExplanationServiceImplTest,KnowledgeAudiencePolicyTest,SchemaMigrationContractTest test`

Expected: FAIL because the explanation service, scenario, and prompt migration do not exist.

- [ ] **Step 3: Implement the explanation service and V22 prompt**

Use:

```java
PatientReportExplanationResult explain(PatientAgentReportDetailVO report);
```

V22 creates a separate `PATIENT_SIGNED_REPORT_EXPLANATION` template with JSON contract `{ "explanation": string }`; do not repurpose the general patient Agent prompt. Catch model-generation failures after the source report has been authorized and return an unavailable result with a fixed patient-safe message. LLM call logging remains owned by `LlmOrchestrationService`.

- [ ] **Step 4: Run focused tests and verify GREEN**

Run: `mvn -Dtest=PatientReportExplanationServiceImplTest,KnowledgeAudiencePolicyTest,SchemaMigrationContractTest test`

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/resources/db/migration/V22__patient_report_explanation_prompt.sql \
  src/main/java/com/example/retinavision/agent/PatientReportExplanationResult.java \
  src/main/java/com/example/retinavision/service/PatientReportExplanationService.java \
  src/main/java/com/example/retinavision/service/impl/PatientReportExplanationServiceImpl.java \
  src/main/java/com/example/retinavision/llm/PromptScenario.java \
  src/main/java/com/example/retinavision/llm/LlmSafetyPolicy.java \
  src/test/java/com/example/retinavision/migration/SchemaMigrationContractTest.java \
  src/test/java/com/example/retinavision/service/impl/PatientReportExplanationServiceImplTest.java \
  src/test/java/com/example/retinavision/rag/KnowledgeAudiencePolicyTest.java
git commit -m "feat: explain signed reports safely"
```

### Task 4: Patient Context and Native Skill Orchestrator

**Files:**
- Create: `src/main/java/com/example/retinavision/agent/PatientAgentReference.java`
- Create: `src/main/java/com/example/retinavision/agent/PatientAgentQueryContextSnapshot.java`
- Create: `src/main/java/com/example/retinavision/agent/PatientAgentQueryContextService.java`
- Create: `src/main/java/com/example/retinavision/agent/PersistentPatientAgentQueryContextService.java`
- Create: `src/main/java/com/example/retinavision/agent/PatientAgentContextCommandParser.java`
- Create: `src/main/java/com/example/retinavision/agent/PatientAgentSkillResult.java`
- Create: `src/main/java/com/example/retinavision/agent/PatientAgentSkillOrchestrator.java`
- Test: `src/test/java/com/example/retinavision/agent/PersistentPatientAgentQueryContextServiceTest.java`
- Test: `src/test/java/com/example/retinavision/agent/PatientAgentContextCommandParserTest.java`
- Test: `src/test/java/com/example/retinavision/agent/PatientAgentSkillOrchestratorTest.java`

**Interfaces:**
- Consumes: Task 1 Skill codes/schemas, Task 2 query service, Task 3 explanation service, existing `AgentSkillRouter`, version binding, pagination, structured data, and action records.
- Produces: `Optional<PatientAgentSkillResult> handle(Long sessionId, String question, CurrentUserVO user)` for Task 5.

- [ ] **Step 1: Write failing context-command and orchestration tests**

Cover list/next/previous/select-index, latest-case progress, re-upload filtering, signed-report list/view/explain, no-case empty response, expired/mismatched/out-of-range references, role rejection, page-size 10, and action allowlisting. Verify `PATIENT_KNOWLEDGE_QA` returns `Optional.empty()` so only the controlled knowledge path reaches Tool Calling.

- [ ] **Step 2: Run focused tests and verify RED**

Run: `mvn -Dtest=PersistentPatientAgentQueryContextServiceTest,PatientAgentContextCommandParserTest,PatientAgentSkillOrchestratorTest test`

Expected: FAIL because patient context and orchestration classes do not exist.

- [ ] **Step 3: Implement patient context and orchestration**

Store patient-specific filters, selected signed-report result/version, and at most 10 current-page references inside the existing `agent_query_context.current_filters_json` and `recent_result_references_json`; no schema change is required. Generate only these action paths in Java:

- `VIEW_CASE_PROGRESS -> /cases/{caseId}/progress`
- `VIEW_SIGNED_REPORT -> /cases/{caseId}/progress?section=reports`
- `GO_TO_IMAGE_UPLOAD -> /cases/{caseId}/images`

Native answers are fixed summaries derived from structured data. The model is not called for list, progress, report lookup, pagination, or selection.

- [ ] **Step 4: Run focused tests and verify GREEN**

Run: `mvn -Dtest=PersistentPatientAgentQueryContextServiceTest,PatientAgentContextCommandParserTest,PatientAgentSkillOrchestratorTest test`

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/example/retinavision/agent/PatientAgent* \
  src/main/java/com/example/retinavision/agent/PersistentPatientAgentQueryContextService.java \
  src/test/java/com/example/retinavision/agent/PersistentPatientAgentQueryContextServiceTest.java \
  src/test/java/com/example/retinavision/agent/PatientAgentContextCommandParserTest.java \
  src/test/java/com/example/retinavision/agent/PatientAgentSkillOrchestratorTest.java
git commit -m "feat: orchestrate patient agent skills"
```

### Task 5: Agent Chat Integration, Persistence, and Tool Isolation

**Files:**
- Modify: `src/main/java/com/example/retinavision/service/impl/AgentChatServiceImpl.java`
- Modify: `src/main/java/com/example/retinavision/agent/DefaultAgentToolFactory.java`
- Modify: `src/main/java/com/example/retinavision/agent/AgentToolFactory.java` if its allowlist contract needs a patient call site overload cleanup
- Modify: `src/test/java/com/example/retinavision/agent/AgentChatServiceImplTest.java`
- Modify: `src/test/java/com/example/retinavision/agent/DefaultAgentToolFactoryTest.java`
- Modify: `src/main/java/com/example/retinavision/controller/AgentController.java` only if history deserialization needs no-loss coverage

**Interfaces:**
- Consumes: `PatientAgentSkillOrchestrator.handle(...)` and `PatientAgentSkillResult` from Task 4.
- Produces: backward-compatible `POST /agent/chat` responses and persisted structured patient messages consumed by Task 6.

- [ ] **Step 1: Write failing integration and tool-isolation tests**

Assert patient native Skills never call `AgentModelGateway` or `AgentToolFactory`; knowledge questions register only `searchMedicalKnowledge`; signed-report explanation retains its structured report when the explanation is unavailable; both messages and full structured assistant JSON are persisted; history ownership remains enforced.

- [ ] **Step 2: Run focused tests and verify RED**

Run: `mvn -Dtest=AgentChatServiceImplTest,DefaultAgentToolFactoryTest test`

Expected: FAIL because patient orchestration is not integrated and the old patient tool bundle still exposes business query tools to the model.

- [ ] **Step 3: Integrate patient orchestration before generic Tool Calling**

Call the doctor orchestrator only for doctors and the patient orchestrator only for patients. Use one private structured-response completion method that accepts the common fields rather than widening `DoctorAgentSkillResult`. For patient knowledge fallback, call `toolFactory.create(..., Set.of("searchMedicalKnowledge"))` and bind `PATIENT_KNOWLEDGE_QA`; administrators retain their existing tool policy.

- [ ] **Step 4: Run focused tests and verify GREEN**

Run: `mvn -Dtest=AgentChatServiceImplTest,DefaultAgentToolFactoryTest test`

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/example/retinavision/service/impl/AgentChatServiceImpl.java \
  src/main/java/com/example/retinavision/agent/AgentToolFactory.java \
  src/main/java/com/example/retinavision/agent/DefaultAgentToolFactory.java \
  src/main/java/com/example/retinavision/controller/AgentController.java \
  src/test/java/com/example/retinavision/agent/AgentChatServiceImplTest.java \
  src/test/java/com/example/retinavision/agent/DefaultAgentToolFactoryTest.java
git commit -m "feat: integrate patient agent service loop"
```

### Task 6: Patient Structured Cards and Controlled Navigation

**Files:**
- Modify: `frontend/src/types/agent.ts`
- Modify: `frontend/src/utils/agent-display.ts`
- Modify: `frontend/src/views/agent/ClinicalAgent.vue`
- Modify: `frontend/src/views/case/PatientCaseProgress.vue`
- Modify: `frontend/src/components/agent/AgentMessageContent.vue`
- Create: `frontend/src/components/agent/PatientCaseList.vue`
- Create: `frontend/src/components/agent/PatientCaseProgress.vue`
- Create: `frontend/src/components/agent/PatientSignedReport.vue`
- Create: `frontend/src/components/agent/PatientKnowledgeAnswer.vue` only if the existing evidence component cannot present citations with the answer cleanly

**Interfaces:**
- Consumes: Task 5 response types `CASE_LIST`, `CASE_PROGRESS`, `SIGNED_REPORT`, `KNOWLEDGE_ANSWER` and patient action enum values.
- Produces: patient cards, pagination, history restoration, and allowlisted navigation while retaining doctor/admin rendering.

- [ ] **Step 1: Add patient response types and intentionally incomplete component references**

Define discriminated payload interfaces and narrow `AgentAction.type` to include `VIEW_CASE_PROGRESS`, `VIEW_SIGNED_REPORT`, and `GO_TO_IMAGE_UPLOAD`. Reference the new components from `AgentMessageContent.vue` before creating them.

- [ ] **Step 2: Run type-check and verify RED**

Run: `npm.cmd --prefix ./frontend run type-check`

Expected: FAIL on missing patient components or incomplete payload handling.

- [ ] **Step 3: Implement compact patient cards and role-specific interactions**

Use the approved four quick questions. Never render quality score, thresholds, raw errors, model fields, or task logs in patient cards. Update `runAction` to accept only:

- `/cases/{id}/progress`
- `/cases/{id}/progress?section=reports`
- `/cases/{id}/images`

Keep arbitrary paths rejected even when present in restored structured history. Preserve mobile single-column layout and existing doctor/admin cards.

- [ ] **Step 4: Run frontend verification and verify GREEN**

Run: `npm.cmd --prefix ./frontend run type-check`

Expected: PASS.

Run: `npm.cmd --prefix ./frontend run build`

Expected: PASS; existing Vite large-chunk warnings are allowed, errors are not.

- [ ] **Step 5: Commit**

```bash
git add frontend/src/types/agent.ts frontend/src/utils/agent-display.ts \
  frontend/src/views/agent/ClinicalAgent.vue frontend/src/views/case/PatientCaseProgress.vue \
  frontend/src/components/agent/AgentMessageContent.vue \
  frontend/src/components/agent/Patient*.vue
git commit -m "feat: add patient agent cards"
```

### Task 7: Evaluation, Contracts, Documentation, and End-to-End Verification

**Files:**
- Create: `src/test/java/com/example/retinavision/agent/PatientAgentRoutingEvaluationTest.java`
- Create: `src/test/java/com/example/retinavision/agent/PatientAgentSecurityContractTest.java`
- Modify: `API_CONTRACT.md`
- Modify: `README.md`
- Modify: `docs/superpowers/specs/2026-10-07-patient-agent-natural-language-service-design.md` only for verified implementation clarifications

**Interfaces:**
- Consumes: complete Tasks 1-6 implementation.
- Produces: measured routing/parameter accuracy, security regression coverage, documented patient Agent contract, and a reviewable Pull Request.

- [ ] **Step 1: Write the routing corpus and security contract tests**

Include positive and negative utterances for all four patient Skills, parameter extraction, pagination, index selection, report explanation, knowledge questions, write-intent refusal, cross-patient references, forbidden response fields, and unsafe arbitrary paths. Require routing accuracy at least 90%, parameter accuracy at least 95%, and all safety cases passing.

- [ ] **Step 2: Run evaluation tests and fix only verified failures**

Run: `mvn -Dtest=PatientAgentRoutingEvaluationTest,PatientAgentSecurityContractTest test`

Expected: PASS with thresholds printed in the test report.

- [ ] **Step 3: Update public contracts and operator documentation**

Document the four structured data types, patient actions, privacy boundary, LLM degradation behavior, and natural-language examples. Do not expose internal prompts, credentials, SQL, or private paths.

- [ ] **Step 4: Run full backend, frontend, and repository verification**

Run: `powershell -ExecutionPolicy Bypass -File ./scripts/verify-repository.ps1`

Expected: PASS.

Run: `mvn test`

Expected: all Java tests PASS with zero failures/errors.

Run: `npm.cmd --prefix ./frontend run type-check`

Expected: PASS.

Run: `npm.cmd --prefix ./frontend run build`

Expected: PASS; warnings must be reviewed and reported.

- [ ] **Step 5: Browser acceptance with patient and second-patient accounts**

Verify quick questions, list ordering, pagination, selection, progress, re-upload guidance, signed-report view/explanation, history restoration, LLM-unavailable fallback, mobile layout, and refusal of a cross-patient case reference. Confirm every navigation target is one of the three allowlisted patient routes.

- [ ] **Step 6: Commit**

```bash
git add src/test/java/com/example/retinavision/agent/PatientAgentRoutingEvaluationTest.java \
  src/test/java/com/example/retinavision/agent/PatientAgentSecurityContractTest.java \
  API_CONTRACT.md README.md \
  docs/superpowers/specs/2026-10-07-patient-agent-natural-language-service-design.md
git commit -m "test: verify patient agent service loop"
```

- [ ] **Step 7: Request whole-branch review and create a Pull Request**

Review focus: direct account scoping, report field allowlist, raw-quality leakage, stale context references, LLM degradation, knowledge audience filtering, action-path allowlist, and doctor/admin regressions. Fix only verified Critical/Important findings, rerun affected tests plus the full suite, then push `feature/patient-agent` and open a PR against `master` after PR #5 is merged.
