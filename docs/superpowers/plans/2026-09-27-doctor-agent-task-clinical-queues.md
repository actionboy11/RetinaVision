# Doctor Agent Task and Clinical Queue Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 让医生通过自然语言查询本人任务、待审核结果和待签发报告，并获得可恢复的结构化卡片、分页上下文和受控页面跳转。

**Architecture:** 开放式问题先由无工具的 LLM Skill Router 根据数据库中的启用 Skill 定义输出 `skillCode + confidence + arguments`，Java 再校验角色、参数和执行模式。任务与临床队列属于 `DIRECT` Skill，由 `DoctorAgentSkillOrchestrator` 直接调用医生范围内的查询 Service；只有医学知识等 `TOOL_CALLING` Skill 才进入第二次 LLM 工具循环。

**Tech Stack:** Java 17、Spring Boot 3.5.15、Spring AI 1.1.8、MyBatis-Plus、MySQL/Flyway、JUnit 5/Mockito、Vue 3、TypeScript、Element Plus。

**Spec:** `docs/superpowers/specs/2026-09-27-doctor-agent-task-clinical-queues-design.md`

## Global Constraints

- `USER` 技术枚举继续代表患者；本轮只增强 `DOCTOR` Agent，患者、管理员和研究员能力不扩张。
- 任务查询默认 `VESSEL_SEGMENTATION`；只有明确提到“图像质检、质量检测、质检任务”才使用 `IMAGE_QUALITY_CHECK`。
- 所有任务和临床队列 SQL 必须直接包含 `medical_case.assigned_doctor_id = 当前医生 ID`；不得先全局查询再做 Java 鉴权。
- 列表默认按最近业务时间倒序，每页默认并封顶为 10 条。
- `DIRECT` Skill 的查询结果不得重新发送给 LLM；`TOOL_CALLING` Skill 每次只注册 Java 白名单允许的工具。
- Agent 只读：不增加创建、取消、重试、审核、修正或签发能力。
- 响应不得包含原图、mask、本地路径、完整结果 JSON、完整异常堆栈或未签发报告正文。
- 跳转路径只能由 Java 根据已鉴权内部 ID 生成；前端继续执行动作白名单。
- 数据库结构只通过 Flyway 维护；`docker/mysql/init.sql` 仍只创建数据库，不重复业务 DDL。
- 后端 Git 仓库为 `C:/codexcode/RetinaVision/RetinaVision`；前端根目录当前不是有效 Git 仓库，前端修改必须保留并验证，但不能伪称已提交。

## Review Focus

- 含“任务”但未说明类型时必须查询血管分割；显式“图像质检”必须覆盖默认值，测试归 Task 2 和 Task 4。
- “查看第三个”必须按当前 `CASE/TASK/CLINICAL_QUEUE` 引用类型解析；类型不匹配、越界或过期时要求重新查询，测试归 Task 5 和 Task 6。
- 显式任务编号属于其他医生时必须与不存在统一返回“资源不存在”，测试归 Task 3。
- 同一结果存在报告历史时，待签发查询不得因 JOIN 产生重复行，也不得把已有 `SIGNED` 报告列入队列，测试归 Task 4。
- Router 返回非法 JSON、未知 Skill、越权 Skill、非法枚举或低置信度时不得查询数据库或注册工具，测试归 Task 2 和 Task 6。

---

### Task 1: 固化 Skill 执行模式、引用类型与 V19 迁移

**Files:**
- Create: `src/main/java/com/example/retinavision/agent/AgentSkillExecutionMode.java`
- Create: `src/main/java/com/example/retinavision/agent/AgentReferenceType.java`
- Create: `src/main/java/com/example/retinavision/agent/DoctorTaskStatusFilter.java`
- Create: `src/main/java/com/example/retinavision/agent/DoctorClinicalQueueType.java`
- Modify: `src/main/java/com/example/retinavision/agent/AgentSkillCode.java`
- Modify: `src/main/java/com/example/retinavision/llm/PromptScenario.java`
- Modify: `src/main/java/com/example/retinavision/llm/LlmSafetyPolicy.java`
- Create: `src/main/resources/db/migration/V19__doctor_agent_task_clinical_queues.sql`
- Modify: `src/test/java/com/example/retinavision/migration/SchemaMigrationContractTest.java`

**Interfaces:**
- Produces: `DOCTOR_TASK_SEARCH` and `DOCTOR_CLINICAL_QUEUE` Skill codes.
- Produces: `AgentSkillExecutionMode { DIRECT, TOOL_CALLING }` and `AgentReferenceType { CASE, TASK, CLINICAL_QUEUE }`.
- Produces: `DoctorTaskStatusFilter { ANY, WAITING, RUNNING, FAILED, SUCCESS }` and `DoctorClinicalQueueType { PENDING_REVIEW, PENDING_REPORT }`.
- Produces: active Prompt template `AGENT_SKILL_ROUTER` with required output field `skillCode`; two new Skill v1 records remain inactive until evaluation passes.

- [ ] **Step 1: Write the failing migration contract test**

Add `doctorAgentTaskQueueMigrationAddsRouterPromptSkillsAndTypedContext()` asserting V19 contains `reference_type`, `AGENT_SKILL_ROUTER`, both new Skill codes, v1 routing examples and no update that activates the two new Skill versions.

- [ ] **Step 2: Run the focused test and verify it fails**

Run: `mvn -Dtest=SchemaMigrationContractTest#doctorAgentTaskQueueMigrationAddsRouterPromptSkillsAndTypedContext test`

Expected: FAIL because V19 and the new enums do not exist.

- [ ] **Step 3: Add the enums and migration**

V19 must backfill existing contexts with `reference_type = 'CASE'`, make the column non-null with default `CASE`, insert the router Prompt v1, and insert inactive v1 definitions for `DOCTOR_TASK_SEARCH` and `DOCTOR_CLINICAL_QUEUE`. The router Prompt must state that no tools are available and require a JSON object with `skillCode`, `confidence`, and `arguments`.

- [ ] **Step 4: Run the focused test**

Run: `mvn -Dtest=SchemaMigrationContractTest test`

Expected: PASS.

- [ ] **Step 5: Commit the schema and contracts**

```powershell
git add src/main/java/com/example/retinavision/agent src/main/java/com/example/retinavision/llm/PromptScenario.java src/main/java/com/example/retinavision/llm/LlmSafetyPolicy.java src/main/resources/db/migration/V19__doctor_agent_task_clinical_queues.sql src/test/java/com/example/retinavision/migration/SchemaMigrationContractTest.java
git commit -m "feat: define doctor task and queue skills"
```

### Task 2: 建立无工具 LLM Skill Router 与 Java 执行模式注册表

**Files:**
- Create: `src/main/java/com/example/retinavision/agent/AgentSkillDefinition.java`
- Create: `src/main/java/com/example/retinavision/agent/AgentSkillRegistry.java`
- Create: `src/main/java/com/example/retinavision/agent/AgentSkillCatalogService.java`
- Create: `src/main/java/com/example/retinavision/agent/AgentContextCommandParser.java`
- Create: `src/main/java/com/example/retinavision/agent/LlmAgentSkillRouter.java`
- Delete: `src/main/java/com/example/retinavision/agent/DefaultAgentSkillRouter.java`
- Modify: `src/main/java/com/example/retinavision/agent/AgentSkillRouter.java`
- Modify: `src/main/java/com/example/retinavision/agent/AgentSkillRoute.java`
- Modify: `src/main/java/com/example/retinavision/agent/AgentSkillVersionBindingService.java`
- Delete: `src/test/java/com/example/retinavision/agent/DefaultAgentSkillRouterTest.java`
- Create: `src/test/java/com/example/retinavision/agent/AgentContextCommandParserTest.java`
- Create: `src/test/java/com/example/retinavision/agent/LlmAgentSkillRouterTest.java`
- Create: `src/test/java/com/example/retinavision/agent/AgentSkillRegistryTest.java`

**Interfaces:**
- Consumes: `LlmOrchestrationService.generateJson("AGENT_SKILL_ROUTER", sanitizedContext)`.
- Produces: `AgentSkillRoute route(String question, AgentSkillCode currentSkill, List<AgentSkillDefinition> availableSkills)`.
- Produces: `Optional<AgentSkillRoute> AgentContextCommandParser.parse(String question, AgentQueryContextSnapshot context)`.
- Produces: `List<AgentSkillDefinition> AgentSkillCatalogService.availableFor(Long sessionId, CurrentUserVO user)` and a candidate-version variant used by evaluation.
- Produces: `AgentSkillRegistry.executionMode(AgentSkillCode)` and role/argument whitelist validation; task and clinical queue are `DIRECT`, medical knowledge is `TOOL_CALLING`.

- [ ] **Step 1: Write failing router and registry tests**

Cover: the serialized routing context contains only the question and Doctor-visible Skill definitions; no tool schema is present; valid JSON maps to the expected code and arguments; unknown Skill, `ADMIN` Skill for a doctor, illegal status, malformed JSON, and confidence below `0.65` fail before execution. Assert missing task type is normalized later to vessel segmentation, while explicit quality wording can be returned as `IMAGE_QUALITY_CHECK`.

- [ ] **Step 2: Write failing deterministic context tests**

Cover `NEXT_PAGE`, `PREVIOUS_PAGE`, `SELECT_INDEX` and `FILTER_FAILED`; assert these commands return without invoking `LlmOrchestrationService` and preserve the current reference type.

- [ ] **Step 3: Run focused tests and verify failure**

Run: `mvn -Dtest=LlmAgentSkillRouterTest,AgentContextCommandParserTest,AgentSkillRegistryTest test`

Expected: FAIL because the catalog, registry and LLM router are absent.

- [ ] **Step 4: Implement the router boundary**

Build the routing prompt context from database Skill name, description, selected version examples and Java-owned argument schema. Parse with Jackson, reject extra/unknown Skill codes, clamp confidence to `[0,1]`, and never register `ToolCallback` during routing. `AgentSkillVersionBindingService` must expose bound-version-or-active-version lookup without binding every catalog entry; binding still occurs only after a Skill is selected.

- [ ] **Step 5: Run focused tests**

Run: `mvn -Dtest=LlmAgentSkillRouterTest,AgentContextCommandParserTest,AgentSkillRegistryTest test`

Expected: PASS and Mockito verifies zero tool/gateway interactions beyond the single JSON router call.

- [ ] **Step 6: Commit the routing layer**

```powershell
git add -A src/main/java/com/example/retinavision/agent src/test/java/com/example/retinavision/agent
git commit -m "feat: add governed llm skill routing"
```

### Task 3: 实现医生范围内的任务列表与任务详情查询

**Files:**
- Create: `src/main/java/com/example/retinavision/agent/DoctorTaskSearchCriteria.java`
- Create: `src/main/java/com/example/retinavision/pojo/VO/DoctorAgentTaskSummaryVO.java`
- Create: `src/main/java/com/example/retinavision/pojo/VO/DoctorAgentTaskDetailVO.java`
- Create: `src/main/java/com/example/retinavision/pojo/VO/DoctorAgentTaskLogVO.java`
- Modify: `src/main/java/com/example/retinavision/service/DoctorAgentQueryService.java`
- Modify: `src/main/java/com/example/retinavision/service/impl/DoctorAgentQueryServiceImpl.java`
- Modify: `src/main/java/com/example/retinavision/mapper/DoctorAgentQueryMapper.java`
- Modify: `src/main/resources/mapper/DoctorAgentQueryMapper.xml`
- Modify: `src/test/java/com/example/retinavision/service/impl/DoctorAgentQueryServiceImplTest.java`
- Modify: `src/test/java/com/example/retinavision/mapper/DoctorAgentQueryMapperContractTest.java`

**Interfaces:**
- Produces: `PageResult<DoctorAgentTaskSummaryVO> searchTasks(DoctorTaskSearchCriteria criteria, Integer page, Integer pageSize, CurrentUserVO doctor)`.
- Produces: `DoctorAgentTaskDetailVO getTaskDetail(String taskReference, CurrentUserVO doctor)`.
- `DoctorAgentTaskDetailVO` extends the task projection with at most five sanitized logs; it exposes no image path, result JSON or stack trace.

- [ ] **Step 1: Write failing Service tests**

Assert role enforcement, page/page-size normalization, default `VESSEL_SEGMENTATION`, doctor ID forwarding, explicit task number lookup, and the same “资源不存在” exception for unknown and other-doctor references. Assert error/log summaries are single-line and length limited.

- [ ] **Step 2: Write failing Mapper contract tests**

Assert count, list, detail and log SQL each join `medical_case` and include `c.assigned_doctor_id = #{doctorId}`. Assert `WAITING` expands to `CREATED/WAITING`, `RUNNING` expands to `RUNNING/RETRYING`, sort is `t.updated_at DESC, t.id DESC`, and logs use `ORDER BY created_at DESC, id DESC LIMIT 5`.

- [ ] **Step 3: Run focused tests and verify failure**

Run: `mvn -Dtest=DoctorAgentQueryServiceImplTest,DoctorAgentQueryMapperContractTest test`

Expected: FAIL on missing task query interfaces.

- [ ] **Step 4: Implement criteria, projections, Mapper SQL and Service**

Task summary fields must exactly match the spec. Parse numeric IDs only as an alternative to exact `task_no`; both branches remain in the same doctor-scoped SQL. Normalize `error_message` and task-log messages before returning them.

- [ ] **Step 5: Run focused tests**

Run: `mvn -Dtest=DoctorAgentQueryServiceImplTest,DoctorAgentQueryMapperContractTest test`

Expected: PASS.

- [ ] **Step 6: Commit task querying**

```powershell
git add src/main/java/com/example/retinavision/agent/DoctorTaskSearchCriteria.java src/main/java/com/example/retinavision/pojo/VO/DoctorAgentTask*.java src/main/java/com/example/retinavision/service/DoctorAgentQueryService.java src/main/java/com/example/retinavision/service/impl/DoctorAgentQueryServiceImpl.java src/main/java/com/example/retinavision/mapper/DoctorAgentQueryMapper.java src/main/resources/mapper/DoctorAgentQueryMapper.xml src/test/java/com/example/retinavision/service/impl/DoctorAgentQueryServiceImplTest.java src/test/java/com/example/retinavision/mapper/DoctorAgentQueryMapperContractTest.java
git commit -m "feat: add doctor-scoped task queries"
```

### Task 4: 实现待审核与待签发临床队列查询

**Files:**
- Create: `src/main/java/com/example/retinavision/agent/DoctorClinicalQueueCriteria.java`
- Create: `src/main/java/com/example/retinavision/pojo/VO/DoctorClinicalQueueItemVO.java`
- Modify: `src/main/java/com/example/retinavision/service/DoctorAgentQueryService.java`
- Modify: `src/main/java/com/example/retinavision/service/impl/DoctorAgentQueryServiceImpl.java`
- Modify: `src/main/java/com/example/retinavision/mapper/DoctorAgentQueryMapper.java`
- Modify: `src/main/resources/mapper/DoctorAgentQueryMapper.xml`
- Modify: `src/test/java/com/example/retinavision/service/impl/DoctorAgentQueryServiceImplTest.java`
- Modify: `src/test/java/com/example/retinavision/mapper/DoctorAgentQueryMapperContractTest.java`

**Interfaces:**
- Produces: `PageResult<DoctorClinicalQueueItemVO> searchClinicalQueue(DoctorClinicalQueueCriteria criteria, Integer page, Integer pageSize, CurrentUserVO doctor)`.
- Queue criteria contains `DoctorClinicalQueueType queueType` and `DoctorDateWindow dateWindow`; `queueType` is required after route normalization.

- [ ] **Step 1: Write failing queue Service tests**

Assert doctor-only access, size capped at 10, `PENDING_REVIEW` and `PENDING_REPORT` forwarded unchanged, and empty data returns an empty `PageResult` rather than null.

- [ ] **Step 2: Write failing SQL contract tests**

Assert `PENDING_REVIEW` requires successful vessel segmentation plus result and no review or `PENDING/CHANGES_REQUESTED`; `PENDING_REPORT` requires `APPROVED` and `NOT EXISTS` a `SIGNED` report. Assert distinct task/result rows and `COALESCE(t.finished_at, ar.created_at) DESC, t.id DESC` sorting.

- [ ] **Step 3: Run focused tests and verify failure**

Run: `mvn -Dtest=DoctorAgentQueryServiceImplTest,DoctorAgentQueryMapperContractTest test`

Expected: FAIL on missing queue query methods.

- [ ] **Step 4: Implement queue projection and SQL**

Use `EXISTS/NOT EXISTS` for signed-report history instead of joining all report versions. Return only the approved projection fields, including quality status/score extracted from the associated quality result without exposing its JSON.

- [ ] **Step 5: Run focused tests**

Run: `mvn -Dtest=DoctorAgentQueryServiceImplTest,DoctorAgentQueryMapperContractTest test`

Expected: PASS.

- [ ] **Step 6: Commit clinical queues**

```powershell
git add src/main/java/com/example/retinavision/agent/DoctorClinicalQueueCriteria.java src/main/java/com/example/retinavision/pojo/VO/DoctorClinicalQueueItemVO.java src/main/java/com/example/retinavision/service src/main/java/com/example/retinavision/mapper/DoctorAgentQueryMapper.java src/main/resources/mapper/DoctorAgentQueryMapper.xml src/test/java/com/example/retinavision/service/impl/DoctorAgentQueryServiceImplTest.java src/test/java/com/example/retinavision/mapper/DoctorAgentQueryMapperContractTest.java
git commit -m "feat: add doctor clinical queue queries"
```

### Task 5: 持久化类型化分页引用与任务筛选上下文

**Files:**
- Modify: `src/main/java/com/example/retinavision/agent/AgentQueryContextSnapshot.java`
- Modify: `src/main/java/com/example/retinavision/agent/PersistentAgentQueryContextService.java`
- Modify: `src/main/java/com/example/retinavision/pojo/Entity/AgentQueryContextEntity.java`
- Modify: `src/test/java/com/example/retinavision/agent/PersistentAgentQueryContextServiceTest.java`

**Interfaces:**
- Produces: context fields `referenceType`, `taskType`, `taskStatus`, `queueType`, existing date/eye/case filters, selected case/task IDs and `List<Long> recentReferenceIds`.
- Existing rows with missing/blank reference type load as `CASE`; at most 10 references are saved.

- [ ] **Step 1: Write failing context compatibility tests**

Cover saving/restoring `TASK` and `CLINICAL_QUEUE` filters, converting existing integer case-reference JSON to `Long`, defaulting legacy rows to `CASE`, truncating references to 10, and returning empty for expired or malformed contexts.

- [ ] **Step 2: Run the focused test and verify failure**

Run: `mvn -Dtest=PersistentAgentQueryContextServiceTest test`

Expected: FAIL because typed references and new filters are absent.

- [ ] **Step 3: Implement typed context persistence**

Keep filters in `current_filters_json`; write enum names only. Rename the in-memory accessor from `recentCaseIds` to `recentReferenceIds` and update existing case Skill call sites without changing their behavior.

- [ ] **Step 4: Run context and existing orchestrator tests**

Run: `mvn -Dtest=PersistentAgentQueryContextServiceTest,DoctorAgentSkillOrchestratorTest test`

Expected: PASS.

- [ ] **Step 5: Commit context isolation**

```powershell
git add src/main/java/com/example/retinavision/agent/AgentQueryContextSnapshot.java src/main/java/com/example/retinavision/agent/PersistentAgentQueryContextService.java src/main/java/com/example/retinavision/pojo/Entity/AgentQueryContextEntity.java src/test/java/com/example/retinavision/agent/PersistentAgentQueryContextServiceTest.java src/test/java/com/example/retinavision/agent/DoctorAgentSkillOrchestratorTest.java
git commit -m "feat: isolate agent query reference types"
```

### Task 6: 编排 DIRECT 任务/队列 Skill 与受控动作

**Files:**
- Modify: `src/main/java/com/example/retinavision/agent/DoctorAgentSkillOrchestrator.java`
- Modify: `src/main/java/com/example/retinavision/service/impl/AgentChatServiceImpl.java`
- Modify: `src/test/java/com/example/retinavision/agent/DoctorAgentSkillOrchestratorTest.java`
- Modify: `src/test/java/com/example/retinavision/agent/AgentChatServiceImplTest.java`

**Interfaces:**
- Consumes: task/queue query methods from Tasks 3-4 and typed context from Task 5.
- Produces: structured types `TASK_LIST`, `TASK_DETAIL`, `CLINICAL_QUEUE` using existing `AgentStructuredData`.
- Produces actions: `/tasks/{taskId}` for `VIEW_TASK`; `/tasks/{taskId}?tab=clinical&stage=review` for `VIEW_REVIEW`.

- [ ] **Step 1: Write failing Orchestrator tests**

Cover initial task list, explicit task number detail, task-page `NEXT/PREVIOUS`, task-page `SELECT_INDEX`, pending review queue, pending report queue, and clinical-page selection. Assert default task type is vessel segmentation and explicit quality query is quality check.

- [ ] **Step 2: Add safety and context failure tests**

Assert CASE context cannot satisfy “查看第三个任务”, TASK context cannot satisfy “查看第二个待审核结果”, index 0/out-of-range/expired context fails safely, and a changed permission causing detail lookup failure is not replaced with stale card data.

- [ ] **Step 3: Add the DIRECT-versus-TOOL_CALLING boundary test**

In `AgentChatServiceImplTest`, verify task and queue routes complete through the Orchestrator without invoking `AgentModelGateway` or `AgentToolFactory`; verify `MEDICAL_KNOWLEDGE_QA` still invokes the model with only `searchMedicalKnowledge` registered.

- [ ] **Step 4: Run focused tests and verify failure**

Run: `mvn -Dtest=DoctorAgentSkillOrchestratorTest,AgentChatServiceImplTest test`

Expected: FAIL because the new Skill switch branches and structured payloads are absent.

- [ ] **Step 5: Implement direct execution and fixed summaries**

Validate route arguments through `AgentSkillRegistry`, bind the selected Skill version, call the Java query Service, store task IDs as typed page references, and generate summaries only from returned totals/statuses. Do not pass returned rows to `AgentModelGateway`.

- [ ] **Step 6: Run focused tests**

Run: `mvn -Dtest=DoctorAgentSkillOrchestratorTest,AgentChatServiceImplTest test`

Expected: PASS and Mockito verifies the model is untouched for DIRECT paths.

- [ ] **Step 7: Commit orchestration**

```powershell
git add src/main/java/com/example/retinavision/agent/DoctorAgentSkillOrchestrator.java src/main/java/com/example/retinavision/service/impl/AgentChatServiceImpl.java src/test/java/com/example/retinavision/agent/DoctorAgentSkillOrchestratorTest.java src/test/java/com/example/retinavision/agent/AgentChatServiceImplTest.java
git commit -m "feat: orchestrate doctor task and queue skills"
```

### Task 7: 让 Skill 评测真正使用候选定义并守住启用门槛

**Files:**
- Modify: `src/main/java/com/example/retinavision/service/impl/AgentSkillAdministrationServiceImpl.java`
- Modify: `src/test/java/com/example/retinavision/agent/AgentSkillAdministrationServiceTest.java`
- Modify: `src/test/java/com/example/retinavision/agent/DoctorAgentRoutingEvaluationTest.java`
- Create: `src/test/java/com/example/retinavision/agent/DoctorTaskClinicalQueueRoutingCorpusTest.java`

**Interfaces:**
- Consumes: `AgentSkillCatalogService` candidate-version catalog and the same `AgentSkillRouter` used at runtime.
- Preserves: activation thresholds route `>= 0.90`, parameters `>= 0.95`, safety `true`.

- [ ] **Step 1: Write failing evaluation tests**

Assert evaluation injects the requested inactive candidate version into the router catalog, does not mutate active versions, counts positive parameter matches separately from negative routing matches, and refuses activation after malformed/low-confidence/unsafe runs.

- [ ] **Step 2: Add task/queue corpus contract tests**

Cover all eight acceptance phrases plus ambiguous negatives such as “病例任务是什么”, cross-Skill phrases and write requests. Expected arguments must include task type/status/date or queue type as applicable.

- [ ] **Step 3: Run focused tests and verify failure**

Run: `mvn -Dtest=AgentSkillAdministrationServiceTest,DoctorAgentRoutingEvaluationTest,DoctorTaskClinicalQueueRoutingCorpusTest test`

Expected: FAIL because evaluation still calls the old deterministic router without candidate definitions.

- [ ] **Step 4: Implement candidate-aware evaluation**

Use the runtime router contract with a supplied candidate catalog. Keep unit tests deterministic by mocking `LlmOrchestrationService`; real admin evaluation intentionally calls the configured LLM and records failures for review.

- [ ] **Step 5: Run focused tests**

Run: `mvn -Dtest=AgentSkillAdministrationServiceTest,DoctorAgentRoutingEvaluationTest,DoctorTaskClinicalQueueRoutingCorpusTest test`

Expected: PASS.

- [ ] **Step 6: Commit governance changes**

```powershell
git add src/main/java/com/example/retinavision/service/impl/AgentSkillAdministrationServiceImpl.java src/test/java/com/example/retinavision/agent
git commit -m "feat: evaluate llm skill routing definitions"
```

### Task 8: 增加任务、详情与临床队列前端卡片

**Files:**
- Modify: `C:/codexcode/RetinaVision/src/types/agent.ts`
- Modify: `C:/codexcode/RetinaVision/src/utils/agent-display.ts`
- Create: `C:/codexcode/RetinaVision/src/components/agent/AgentTaskList.vue`
- Create: `C:/codexcode/RetinaVision/src/components/agent/AgentTaskDetail.vue`
- Create: `C:/codexcode/RetinaVision/src/components/agent/AgentClinicalQueue.vue`
- Modify: `C:/codexcode/RetinaVision/src/components/agent/AgentMessageContent.vue`
- Modify: `C:/codexcode/RetinaVision/src/views/agent/ClinicalAgent.vue`

**Interfaces:**
- Consumes: `TASK_LIST`, `TASK_DETAIL`, `CLINICAL_QUEUE`, existing pagination, and backend-generated actions.
- Produces: compact responsive cards; no local reconstruction of task/review paths.

- [ ] **Step 1: Extend TypeScript discriminated unions**

Add exact task/log/queue interfaces matching backend field names and extend `AgentStructuredData`. Add Chinese names for both Skill codes and status mappings through existing enum helpers.

- [ ] **Step 2: Run type-check and verify the renderer is incomplete**

Run from `C:/codexcode/RetinaVision`: `npm.cmd run type-check`

Expected: PASS for types alone, while the new payloads still have no visual branch; record this as the UI red state rather than inventing a test framework.

- [ ] **Step 3: Implement the three focused components**

Task list displays at most 10 rows/cards with status, task/case/patient numbers, retries, time and sanitized error. Detail adds five latest logs. Clinical queue emphasizes pending review/report and emits only supplied actions. Use stable grid dimensions, compact empty states and single-column narrow layout.

- [ ] **Step 4: Wire message rendering, shortcuts and action whitelist**

`AgentMessageContent.vue` selects components by discriminator. `ClinicalAgent.vue` adds task/queue shortcuts and accepts only existing regex-safe `/tasks/{id}` paths, including the fixed `?tab=clinical&stage=review` query; it must not execute arbitrary URLs.

- [ ] **Step 5: Verify frontend compilation**

Run: `npm.cmd run type-check`

Run: `npm.cmd run build`

Expected: both exit 0.

- [ ] **Step 6: Record the frontend Git limitation**

Do not run a fake commit from the invalid root repository. Keep the verified frontend files intact and list them explicitly in the implementation close-out so the user can place them under the intended repository later.

### Task 9: 同步契约文档并完成真实验收

**Files:**
- Modify: `C:/codexcode/RetinaVision/API_CONTRACT.md`
- Modify: `C:/codexcode/RetinaVision/README.md`
- Modify: `C:/codexcode/RetinaVision/RetinaVision/README.md`
- Modify: `C:/codexcode/RetinaVision/RetinaVision/docs/superpowers/specs/2026-09-27-doctor-agent-task-clinical-queues-design.md` only if implementation reveals an approved design correction.

**Interfaces:**
- Documents the two-stage Router, `DIRECT/TOOL_CALLING` split, new structured payloads and role boundary without exposing credentials or sample patient data.

- [ ] **Step 1: Update public contracts and architecture text**

Extend Agent Skill/data unions, document task/queue field whitelists and clarify that router calls have no tools. Remove obsolete text that says pending queues are returned as case searches.

- [ ] **Step 2: Run the full automated regression**

Backend: `mvn test`

Frontend from workspace root: `npm.cmd run type-check` then `npm.cmd run build`

Expected: backend test count is at least the existing 368 plus new tests; all commands exit 0.

- [ ] **Step 3: Run real local acceptance after V19 migration**

Evaluate and activate v1 for both new Skills through the admin API, then use a doctor account to run the eight phrases from the spec. For each, compare returned totals/rows against MySQL, verify only assigned cases appear, verify current-page selection, and confirm generated paths open the expected task or clinical review tab.

- [ ] **Step 4: Measure the correct latency boundary**

Record Router latency separately from DIRECT query latency. The Service/Mapper portion must target P95 below 1 second; do not claim the external LLM router has the same bound.

- [ ] **Step 5: Commit backend documentation separately**

```powershell
git add README.md docs/superpowers/specs/2026-09-27-doctor-agent-task-clinical-queues-design.md
git commit -m "docs: document doctor task agent workflows"
```

The root `README.md` and `API_CONTRACT.md` remain verified but uncommitted until the workspace root is attached to a valid Git repository.

### Task 10: 最终审查与分支交付

**Files:**
- Review only: all files touched in Tasks 1-9.

**Interfaces:**
- Produces no new behavior; validates the implementation against the spec and commit boundaries.

- [ ] **Step 1: Inspect repository state and commit scope**

Run in backend: `git status --short` and `git log --oneline -12`.

Expected: each backend feature batch is a focused commit; unrelated user changes remain untouched.

- [ ] **Step 2: Run security-oriented review searches**

Search new projections and structured JSON for `mask`, `objectKey`, absolute paths, full `resultJson`, unrestricted URLs and write actions. Confirm none are exposed by task/queue responses.

- [ ] **Step 3: Request code review**

Use `superpowers:requesting-code-review` against the complete backend branch plus the explicit frontend file list. Resolve only verified findings and rerun affected tests.

- [ ] **Step 4: Run final verification before claiming completion**

Use `superpowers:verification-before-completion`; rerun `mvn test`, `npm.cmd run type-check`, and `npm.cmd run build`, and report actual outputs and any uncommitted frontend limitation.

