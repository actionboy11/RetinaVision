# Agent 评测与可观测性闭环实施计划

**Spec:** `docs/superpowers/specs/2026-10-07-agent-evaluation-observability-design.md`

## Global Constraints

- 所有生产代码遵循 TDD：测试先失败，再实现最小修复。
- 固定评测数据不得包含真实患者身份、路径、图像、mask、医生未签发意见或完整 Prompt。
- 评测不得写正式 Agent 会话、消息或 Skill 执行日志。
- 现有浏览器 API 继续使用 `ApiResponse<T>`/`PageResult<T>` 契约，仅 ADMIN 可访问新接口。
- 保留旧 Skill 评测记录和接口兼容；旧记录不能满足新启用门槛。

## Task 1: Schema and anonymous corpus

**Produces:** V23 schema, entities/mappers, 180 anonymous cases, migration contract tests.

1. Extend migration contract tests for five evaluation tables, LLM log source columns, indexes, foreign keys, dataset counts, and anonymous identifier policy; run RED.
2. Add V23 Flyway migration and synchronize `docker/mysql/init.sql`.
3. Add entities and MyBatis-Plus mappers for datasets, cases, runs, bindings, and results.
4. Run migration tests GREEN and commit `feat: add agent evaluation schema`.

## Task 2: Deterministic scorer and fixture runtime

**Consumes:** Task 1 case/run schema. **Produces:** scorer, fixture query adapters, in-memory contexts, evaluation case outcome model.

1. Add failing tests for all metrics, P95, safety short-circuit, failure classification, context sequences, and absence of clinical persistence.
2. Implement `AgentEvaluationScorer`, anonymous doctor/patient fixture services, fixed knowledge citations, and in-memory context services.
3. Build an evaluation runtime that reuses Router, Registry, and doctor/patient Orchestrators without business persistence.
4. Run focused tests GREEN and commit `feat: add deterministic agent evaluation runtime`.

## Task 3: Versioned model execution and asynchronous runs

**Consumes:** Task 1 persistence and Task 2 runtime. **Produces:** selected Prompt invocation, runner lifecycle, cancellation, tagged LLM logs.

1. Add failing tests for candidate Router Prompt execution, frozen bindings, single active run, sequential progress, cancellation, INVALID infrastructure failures, and tagged LLM logs.
2. Extend internal LLM orchestration with a version-specific method while preserving active-version calls.
3. Add dedicated single-thread executor, `AgentEvaluationService`, and `AgentEvaluationRunner`.
4. Persist per-case results and aggregate metrics; commit `feat: execute agent evaluation runs` after GREEN tests.

## Task 4: Governance gates and admin API

**Consumes:** Task 3 completed runs. **Produces:** ADMIN endpoints, review flow, activation eligibility.

1. Add failing controller/service tests for options, datasets, runs, paged results, cancel, review, role denial, and activation gates.
2. Implement `/agent-evaluations/**` API and compatibility adapter for the legacy Skill evaluate endpoint.
3. Require matching `PASSED + APPROVED` runs for future Skill and `AGENT_SKILL_ROUTER` Prompt activation; leave current active versions unchanged.
4. Run focused tests GREEN and commit `feat: govern agent versions with evaluations`.

## Task 5: Administrator evaluation UI

**Consumes:** Task 4 API. **Produces:** admin-only evaluation route, run form, progress, metrics, failure table, review actions.

1. Add TypeScript API/types and an ADMIN-only `/agent-evaluations` route and menu entry.
2. Implement dataset overview, controlled model/version selections, API call estimate, confirmation, progress polling, cancellation, metrics, failure filters, and review.
3. Link Skill and Prompt management to matching evaluation details.
4. Run type-check/build and commit `feat: add agent evaluation center`.

## Task 6: Documentation, regression, and acceptance

**Consumes:** all prior tasks. **Produces:** public contract, operational docs, full verification evidence.

1. Update `API_CONTRACT.md`, root README, and backend docs with evaluation behavior and safety boundaries.
2. Run `mvn test`, frontend type-check/build, repository safety verification, and `git diff --check`.
3. With configured Aliyun service, run one doctor and one patient evaluation when credentials and quota are available; otherwise report the exact environmental blocker without fabricating results.
4. Browser-check desktop and mobile admin evaluation views; commit `docs: document agent evaluation workflow`.

## Review Focus

- Evaluation adapters must never query or mutate clinical tables.
- Candidate Prompt execution must not change the active Prompt.
- Cancel and infrastructure failure states must not produce eligible runs.
- Activation matching must include role, model, dataset version, Skill version, and relevant Prompt version.
- Result summaries and logs must not leak Prompt text, credentials, paths, or clinical content.
