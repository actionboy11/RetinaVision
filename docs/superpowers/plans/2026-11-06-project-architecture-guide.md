# RetinaVision Project Architecture Guide Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Create `docs/PROJECT_ARCHITECTURE_GUIDE.md` as the authoritative guide to RetinaVision's architecture, backend package structure, core orchestration, security boundaries, and code-reading path.

**Architecture:** Build the guide from the current `master` tree, starting with component and business-flow models before presenting a searchable directory and class catalog. Treat Java as the primary subject; describe Vue, Python, and Docker only to the depth needed to understand system collaboration.

**Tech Stack:** Markdown, Mermaid, Java 21/Spring Boot, Vue 3, Python/FastAPI/PyTorch, MySQL, Redis, RabbitMQ, Qdrant, Docker Compose

**Spec:** `docs/superpowers/specs/2026-11-06-project-architecture-guide-design.md`

## Global Constraints

- Use the current repository tree and source code as the source of truth.
- Do not expose local credentials, tokens, real patient data, or absolute local paths.
- Do not document every Getter, Setter, constructor, basic CRUD method, or POJO field.
- Explain Controller and simple Service methods at responsibility level; reserve detailed treatment for complex orchestration and security boundaries.
- Keep frontend, Python, and Docker explanations concise compared with the Java backend.
- Use repository-relative paths and verify every referenced path, class, and method.

## Review Focus

- Stale names after the Java package reorganization: every referenced class must resolve in the current package.
- Role isolation: the guide must distinguish route authorization, Skill availability, and clinical object-level filtering.
- Async consistency: task transaction, outbox publication, RabbitMQ delivery, idempotent execution, and result writeback must be described in the correct order.
- Agent boundaries: Native Skill, Function Calling, RAG, context persistence, and evaluation must not be conflated.
- Documentation scope: directory and method catalogs must remain useful without becoming generated source listings.

---

### Task 1: Build the authoritative architecture inventory

**Files:**
- Read: `README.md`
- Read: `docs/BACKEND.md`
- Read: `API_CONTRACT.md`
- Read: `pom.xml`
- Read: `docker/docker-compose.yml`
- Read: `frontend/package.json`
- Read: `ai-service/requirements.txt`
- Create: `docs/PROJECT_ARCHITECTURE_GUIDE.md`

**Interfaces:**
- Consumes: current repository directory tree and build configuration.
- Produces: document skeleton, terminology table, component inventory, and root directory dictionary.

- [ ] **Step 1: Inventory runtime components and repository roots**

Record each component's responsibility, process boundary, protocol, persisted data, and configuration entry point.

- [ ] **Step 2: Write the document skeleton and terminology rules**

Create all approved top-level sections and define role names, clinical objects, task/result/report distinctions, and DTO/VO/entity terminology.

- [ ] **Step 3: Add the component diagram and root directory dictionary**

Include a Mermaid diagram and explain `src`, `frontend`, `ai-service`, `docker`, `scripts`, `docs`, migrations, tests, and generated/local-only directories.

- [ ] **Step 4: Verify referenced roots**

Run a PowerShell path check for every root path listed in the dictionary. Expected: no missing paths.

### Task 2: Document Java layers and functional packages

**Files:**
- Read: `src/main/java/com/example/retinavision/**`
- Read: `src/main/resources/**`
- Modify: `docs/PROJECT_ARCHITECTURE_GUIDE.md`

**Interfaces:**
- Consumes: Task 1 terminology and root directory dictionary.
- Produces: Java layer model, functional package dictionary, POJO conventions, and dependency direction.

- [ ] **Step 1: Explain technical layers**

Document `controller`, `service`, `mapper`, `pojo`, `config`, `filter`, `mq`, `ai`, `llm`, `rag`, `redis`, `enumeration`, `exception`, `result`, and `utils`.

- [ ] **Step 2: Explain functional subpackages**

Cover `agent`, `analysis`, `auth`, `cases`, `clinical`, `governance`, `imaging`, `knowledge`, `operations`, `patient`, `reporting`, `user`, and `shared` where present.

- [ ] **Step 3: Explain object and interface conventions**

Describe Entity/DTO/VO/Command/Event, Service/Impl, Port/Adapter, Mapper XML, Controller, configuration, and policy classes.

- [ ] **Step 4: Add a backend dependency diagram**

Show normal HTTP flow and the Analysis port/adapter boundary. State prohibited dependency directions enforced by architecture tests.

- [ ] **Step 5: Verify package names**

Compare every documented package against directories under `src/main/java/com/example/retinavision`. Expected: no undocumented invented package.

### Task 3: Write the backend module and core API handbook

**Files:**
- Read: `src/main/java/com/example/retinavision/controller/**`
- Read: `src/main/java/com/example/retinavision/service/**`
- Read: `src/main/java/com/example/retinavision/mapper/**`
- Modify: `docs/PROJECT_ARCHITECTURE_GUIDE.md`

**Interfaces:**
- Consumes: Task 2 layer and package conventions.
- Produces: module-by-module responsibility, entry-point, Service capability, custom query, and core-method catalog.

- [ ] **Step 1: Document authentication, users, patients, and cases**

For each module list controllers, Service interfaces, important implementations, custom Mappers, role boundary, and principal data objects.

- [ ] **Step 2: Document imaging, analysis, clinical workflow, and reporting**

Emphasize upload validation, task lifecycle, outbox, inference execution, human review, report generation, signing, and download boundaries.

- [ ] **Step 3: Document Agent, knowledge, governance, and operations**

Distinguish chat/session handling, Skill routing/orchestration, RAG, Prompt/Skill/evaluation governance, call logging, dead-letter recovery, statistics, and service health.

- [ ] **Step 4: Build the core-method catalog**

For simple Controller and Service methods provide one-line responsibilities. For orchestration, state machine, transaction, security, and idempotency methods document parameters, result, collaborators, failure behavior, and design reason.

- [ ] **Step 5: Verify class and method references**

Use `rg` to resolve every class and method shown in the core catalog. Expected: all references found exactly in current Java sources.

### Task 4: Explain core business flows and security boundaries

**Files:**
- Read: Agent, analysis, auth, clinical, patient, and reporting implementations and tests.
- Modify: `docs/PROJECT_ARCHITECTURE_GUIDE.md`

**Interfaces:**
- Consumes: Task 3 module catalog.
- Produces: end-to-end sequence diagrams, state transitions, transaction boundaries, and role-isolation explanation.

- [ ] **Step 1: Write identity and authorization flow**

Cover login, JWT parsing, blacklist, login limiter, request context, route authorization, and clinical object-level access.

- [ ] **Step 2: Write image-to-report flow**

Cover image validation, quality task, task/outbox transaction, RabbitMQ, Python inference, idempotent writeback, review, report draft, signing, and PDF retrieval.

- [ ] **Step 3: Write doctor and patient Agent flows**

Explain role-specific Skill registry, LLM router, context commands, Native Skill/Function Calling branches, RAG branch, structured response, and navigation whitelist.

- [ ] **Step 4: Write evaluation and governance flow**

Explain anonymous datasets, binding snapshots, real-model runner, deterministic scorer, human approval, and version activation gate.

- [ ] **Step 5: Add state and transaction tables**

Summarize task, image quality, review/report, evaluation, and outbox states without duplicating every enum constant unnecessarily.

### Task 5: Add supporting components, tests, and reading routes

**Files:**
- Read: `frontend/src/**`
- Read: `ai-service/src/**`
- Read: `docker/**`
- Read: `scripts/**`
- Read: `src/test/**`
- Modify: `docs/PROJECT_ARCHITECTURE_GUIDE.md`

**Interfaces:**
- Consumes: complete backend explanation from Tasks 1-4.
- Produces: concise Vue/Python/Docker organization, test strategy, extension guide, and reading paths.

- [ ] **Step 1: Summarize Vue organization**

Explain `views`, `components`, `composables`, `api`, `types`, `stores`, `router`, `plugins`, `utils`, and role-aware layout without analyzing page styling internals.

- [ ] **Step 2: Summarize Python inference organization**

Explain FastAPI entry, schemas, quality checks, model loading, inference, runtime state, storage, and tests without explaining model mathematics.

- [ ] **Step 3: Summarize Docker and local operations**

Explain Compose services, volumes, ports, local environment files, startup dependencies, and which processes remain outside Compose.

- [ ] **Step 4: Explain testing and architecture guards**

Cover unit/contract/architecture/migration tests, frontend static checks, Python tests, and repository safety verification.

- [ ] **Step 5: Add code-reading and extension routes**

Provide beginner, business-flow, Agent, asynchronous-analysis, security, and troubleshooting reading paths with ordered files.

### Task 6: Validate and polish the architecture guide

**Files:**
- Modify: `docs/PROJECT_ARCHITECTURE_GUIDE.md`

**Interfaces:**
- Consumes: the complete draft.
- Produces: verified, readable, current architecture guide.

- [ ] **Step 1: Check document structure and repetition**

Ensure each concept has one primary explanation and later sections link back instead of repeating it.

- [ ] **Step 2: Validate paths, class names, and method names**

Run repository searches for all backticked source paths and sampled core symbols. Expected: no stale package names or missing files.

- [ ] **Step 3: Validate Mermaid blocks and Markdown shape**

Check balanced fences, heading order, table structure, and Mermaid syntax by inspection or available renderer.

- [ ] **Step 4: Scan for sensitive and local-only content**

Run `powershell -ExecutionPolicy Bypass -File ./scripts/verify-repository.ps1`. Expected: repository safety verification passes.

- [ ] **Step 5: Review against the approved design**

Confirm every required module, role boundary, flow, and reading route is present and that simple methods are not over-documented.

- [ ] **Step 6: Commit**

```bash
git add docs/PROJECT_ARCHITECTURE_GUIDE.md docs/superpowers/specs/2026-11-06-project-architecture-guide-design.md docs/superpowers/plans/2026-11-06-project-architecture-guide.md
git commit -m "docs: add comprehensive project architecture guide"
```
