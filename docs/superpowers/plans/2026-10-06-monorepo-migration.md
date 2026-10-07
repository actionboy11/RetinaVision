# RetinaVision Gradual Monorepo Migration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将现有 GitHub Java 仓库扩展为同时包含 Vue 前端、Java 后端和 Python AI 推理服务的安全、可构建 Monorepo。

**Architecture:** 保留 Maven/Java 后端在仓库根目录，新增 `frontend/` 与 `ai-service/`，通过根级文档、Docker Compose 和分层 CI 串联三套技术栈。迁移采用外层源码复制、Git 安全白名单、独立验证和分批提交，不删除外层原始目录，不提交模型权重或业务数据。

**Tech Stack:** Java 17, Spring Boot 3.5, Maven, Vue 3, TypeScript, Vite, Node.js 20, Python 3.11, FastAPI, PyTorch, pytest, Docker Compose, GitHub Actions, Gitleaks

**Spec:** `docs/superpowers/specs/2026-10-06-monorepo-migration-design.md`

## Global Constraints

- Java 后端继续位于仓库根目录；不迁移到 `backend/`。
- 前端目录固定为 `frontend/`，AI 服务目录固定为 `ai-service/`。
- 不改变浏览器 API、Java 到 Python 协议、数据库结构、RabbitMQ 链路或模型算法。
- 不提交 `model_new.pth`、图像、mask、PDF、数据库导出、上传目录、推理存储、日志、密钥或本地环境文件。
- 模型文件约 67.34 MB，不使用 Git LFS；只提交模型放置和校验说明。
- Node.js CI 使用 20，Python CI 使用 3.11，Java CI 使用 17。
- 外层 Vue 与 Python 来源目录在 PR 合并和运行验收前保持不变。
- 所有实现发生在 `feature/monorepo-migration` 隔离 worktree，不直接修改或推送 `master`。

## Review Focus

- 被 `.gitignore` 忽略但通过强制添加进入 index 的模型、环境文件或患者产物，必须由暂存区安全脚本拒绝；Task 1 覆盖。
- Vite 从外层根目录迁入 `frontend/` 后，`@` alias、`/api` 代理和构建路径必须继续指向前端自身；Task 2 覆盖。
- 缺少真实模型时 AI 单元测试必须可运行，而模型加载集成测试必须明确跳过而非伪造通过；Task 3 覆盖。
- Compose 从 `docker/` 解析 `../ai-service` 时，build context、模型 bind mount、storage 和 logs 路径必须全部一致；Task 4 覆盖。
- GitHub Actions 的路径过滤不能让根契约、Docker 或共享配置变化漏掉相关流水线；Task 5 覆盖。

---

### Task 1: 建立 Monorepo 安全边界与仓库验证脚本

**Files:**
- Modify: `.gitignore`
- Create: `scripts/verify-repository.ps1`
- Test: `scripts/verify-repository.ps1`

**Interfaces:**
- Consumes: 当前 Git index、工作树文件和根 `.gitignore`。
- Produces: `scripts/verify-repository.ps1 [-CheckIndex]`，后续每个任务提交前复用；退出码 `0` 表示没有禁止路径、敏感文件名或超过 10 MB 的已跟踪文件。

- [ ] **Step 1: 写安全验证脚本的失败断言**

创建 `scripts/verify-repository.ps1`，先只声明检查项并以失败结束。检查集合固定包括：`*.pth`、`.env`（允许 `*.example`）、`application-dev.yaml`、`application-local.yaml`、`uploads/`、`storage/`、`storage-e2e/`、`logs/`、`node_modules/`、`dist/`、`target/`、数据库导出和大于 10 MB 的已跟踪文件。`-CheckIndex` 时使用 `git diff --cached --name-only --diff-filter=ACMR` 检查暂存区。

- [ ] **Step 2: 运行脚本并确认红灯**

Run: `powershell -ExecutionPolicy Bypass -File .\scripts\verify-repository.ps1`

Expected: non-zero exit with the placeholder failure message.

- [ ] **Step 3: 扩展根 `.gitignore` 并实现安全验证**

保留现有 Java 规则，新增 `frontend/`、`ai-service/` 和通用临时文件规则。脚本仅输出相对路径和失败原因，不读取或打印密钥内容；对 `docker/.env.example`、`frontend/.env.development.example` 和模型说明文件放行。

- [ ] **Step 4: 验证工作树和当前 index**

Run: `powershell -ExecutionPolicy Bypass -File .\scripts\verify-repository.ps1`

Run: `powershell -ExecutionPolicy Bypass -File .\scripts\verify-repository.ps1 -CheckIndex`

Expected: both exit `0`; current backend tracked files remain allowed.

- [ ] **Step 5: 提交安全骨架**

```powershell
git add .gitignore scripts/verify-repository.ps1
git commit -m "chore: define monorepo safety boundaries"
```

### Task 2: 迁入 Vue 前端并保持现有 Agent 体验

**Files:**
- Create: `frontend/src/**`
- Create: `frontend/package.json`
- Create: `frontend/package-lock.json`
- Create: `frontend/index.html`
- Create: `frontend/env.d.ts`
- Create: `frontend/tsconfig.json`
- Create: `frontend/tsconfig.node.json`
- Create: `frontend/vite.config.ts`
- Create: `frontend/.env.development.example`
- Create: `frontend/README.md`

**Interfaces:**
- Consumes: `C:/codexcode/RetinaVision/src` 与外层 Vue 配置文件。
- Produces: 可在 `frontend/` 独立执行的 Vue 项目；Vite `@` 指向 `frontend/src`，`/api` 代理到 `http://127.0.0.1:8080`。

- [ ] **Step 1: 记录迁移前前端验证基线**

Run from `C:/codexcode/RetinaVision`: `npm.cmd run type-check`

Run from `C:/codexcode/RetinaVision`: `npm.cmd run build`

Expected: both exit `0`; retain output for migration后比对。

- [ ] **Step 2: 复制前端白名单文件**

使用 PowerShell `Copy-Item` 进行机械复制，只复制本任务 Files 列表；不得复制 `node_modules/`、`dist/`、`.codex-*.log`、`tmp/`、`output/`、个人 Markdown 或外层空 `.git/`。

- [ ] **Step 3: 编写前端 README**

记录 Node.js 20、`npm ci`、`npm run dev`、`npm run type-check`、`npm run build`、5173 端口、`/api` 代理和浏览器鉴权边界。不得包含本地账号或密码。

- [ ] **Step 4: 从新目录安装并验证**

Run: `npm.cmd --prefix .\frontend ci`

Run: `npm.cmd --prefix .\frontend run type-check`

Run: `npm.cmd --prefix .\frontend run build`

Expected: all exit `0`; output only appears under ignored `frontend/node_modules/` and `frontend/dist/`。

- [ ] **Step 5: 验证 Agent 关键源码已纳入**

Run: `Test-Path frontend/src/components/agent/AgentTaskList.vue; Test-Path frontend/src/components/agent/AgentTaskDetail.vue; Test-Path frontend/src/components/agent/AgentClinicalQueue.vue; Test-Path frontend/src/views/agent/ClinicalAgent.vue`

Expected: four `True` values.

- [ ] **Step 6: 运行安全检查并提交**

```powershell
git add frontend
powershell -ExecutionPolicy Bypass -File .\scripts\verify-repository.ps1 -CheckIndex
git commit -m "feat: add Vue frontend to monorepo"
```

### Task 3: 迁入 Python AI 服务并分离模型集成测试

**Files:**
- Create: `ai-service/src/**`
- Create: `ai-service/tests/**`
- Create: `ai-service/scripts/**`
- Create: `ai-service/requirements.txt`
- Create: `ai-service/Dockerfile`
- Create: `ai-service/.dockerignore`
- Create: `ai-service/.gitignore`
- Create: `ai-service/README.md`
- Create: `ai-service/pytest.ini`
- Create: `ai-service/models/.gitkeep`
- Create: `ai-service/models/README.md`
- Modify: `ai-service/tests/test_model_loader.py`

**Interfaces:**
- Consumes: `C:/codexcode/RetinaVision/retinavision-ai` 的源码和测试白名单。
- Produces: 无真实权重即可运行的 AI 单元测试命令，以及显式 `model_integration` marker；真实模型验收仍使用 `models/model_new.pth`。

- [ ] **Step 1: 复制 AI 服务白名单文件**

复制 `src/`、`tests/`、无敏感数据的 `scripts/`、依赖、Docker 与 README。不得复制 `.idea/`、`.pytest_cache/`、`__pycache__/`、`.venv/`、`models/model_new.pth`、`storage/`、`storage-e2e/` 或 `logs/`。

- [ ] **Step 2: 运行无模型测试并确认现有失败边界**

Run after installing dependencies: `python -m pytest -q .\ai-service\tests`

Expected: model loader test fails because `ai-service/models/model_new.pth` is intentionally absent; other unit tests identify no migration import errors.

- [ ] **Step 3: 定义模型集成测试 marker**

在 `ai-service/pytest.ini` 注册 `model_integration`；仅给真实 checkpoint 加载测试添加 `@pytest.mark.model_integration`。不得修改其断言或用 mock 替代模型加载。

- [ ] **Step 4: 编写模型与 AI README**

`models/README.md` 固定说明文件名 `model_new.pth`、默认环境变量 `RETINAVISION_AI_MODEL_PATH`、本地 SHA-256 命令和不提交权重的原因。AI README 说明 Python 3.11、CPU PyTorch 安装、FastAPI 启动、8000 端口、测试分层和 storage 边界。

- [ ] **Step 5: 验证无权重单元测试**

Run: `python -m pytest -q .\ai-service\tests -m "not model_integration"`

Expected: exit `0`, no test读取真实模型路径。

- [ ] **Step 6: 验证真实模型测试仍可运行**

将外层 `model_new.pth` 临时复制到被忽略的 `ai-service/models/model_new.pth`，先运行 `git check-ignore` 确认该文件被忽略，再执行：

Run: `python -m pytest -q .\ai-service\tests\test_model_loader.py -m model_integration`

Expected: exit `0`; `git status --short` 不显示模型文件。

- [ ] **Step 7: 安全检查并提交**

```powershell
git add ai-service
powershell -ExecutionPolicy Bypass -File .\scripts\verify-repository.ps1 -CheckIndex
git commit -m "feat: add Python inference service to monorepo"
```

### Task 4: 统一文档职责并修正 Docker 路径

**Files:**
- Move: `README.md` -> `docs/BACKEND.md`
- Create: `README.md`
- Create: `API_CONTRACT.md`
- Modify: `docker/docker-compose.yml`
- Modify: `docker/.env.example` only if comments reference the old external path
- Create: `scripts/verify-monorepo-layout.ps1`

**Interfaces:**
- Consumes: 外层系统 `README.md`、`API_CONTRACT.md`，Task 2 `frontend/`，Task 3 `ai-service/`。
- Produces: 根系统导航和可解析的 Compose，其中 AI 默认根路径为 `../ai-service`。

- [ ] **Step 1: 写布局契约验证脚本**

`scripts/verify-monorepo-layout.ps1` 断言根 `pom.xml`、`frontend/package.json`、`ai-service/requirements.txt`、四份职责文档存在；Compose 包含 `../ai-service` 且不包含 `../../retinavision-ai`。

- [ ] **Step 2: 运行布局验证并确认红灯**

Run: `powershell -ExecutionPolicy Bypass -File .\scripts\verify-monorepo-layout.ps1`

Expected: FAIL because docs and Compose still use old ownership/path.

- [ ] **Step 3: 迁移后端 README 并写系统 README**

使用 `git mv README.md docs/BACKEND.md` 保留历史。以外层系统 README 为事实来源改写根 README，所有命令使用新目录；删除旧的 `RetinaVision/` 和 `retinavision-ai/` 相对路径。系统 README 必须保留 AI 辅助、不构成诊断、医生审核签发边界。

- [ ] **Step 4: 导入并核对公共契约**

复制外层 `API_CONTRACT.md` 到根目录，核对医生 Agent 的 `TASK_LIST`、`TASK_DETAIL`、`CLINICAL_QUEUE` 与后端当前 VO 字段一致。不得把 Java 到 Python 的内部接口加入浏览器契约。

- [ ] **Step 5: 修改 Compose 的全部 AI 路径**

将 build context 和三个 bind mount 的默认来源统一改为 `${RETINAVISION_AI_PROJECT_ROOT:-../ai-service}`；端口、容器名和服务依赖保持不变。

- [ ] **Step 6: 验证布局和 Compose**

Run: `powershell -ExecutionPolicy Bypass -File .\scripts\verify-monorepo-layout.ps1`

Run with temporary non-secret environment values: `docker compose -f .\docker\docker-compose.yml config --quiet`

Expected: both exit `0`; rendered paths resolve under repository `ai-service/`。

- [ ] **Step 7: 提交文档和运行配置**

```powershell
git add README.md API_CONTRACT.md docs/BACKEND.md docker scripts/verify-monorepo-layout.ps1
powershell -ExecutionPolicy Bypass -File .\scripts\verify-repository.ps1 -CheckIndex
git commit -m "docs: unify monorepo workflows and contracts"
```

### Task 5: 增加三技术栈 GitHub Actions

**Files:**
- Create: `.github/workflows/backend-ci.yml`
- Create: `.github/workflows/frontend-ci.yml`
- Create: `.github/workflows/ai-service-ci.yml`
- Modify: `.github/workflows/secret-scan.yml`
- Create: `scripts/verify-workflows.ps1`

**Interfaces:**
- Consumes: Task 1 安全脚本、Task 2 npm 项目、Task 3 pytest marker、Task 4 根路径。
- Produces: PR 上独立的 Backend、Frontend、AI Service、Secret Scan 检查。

- [ ] **Step 1: 写 workflow 合同脚本**

断言三个 workflow 使用固定运行时版本；前端工作目录为 `frontend`；AI 命令排除 `model_integration`；后端执行 `mvn test` 并提供 MySQL 3307、Redis 6379、RabbitMQ 5672；根文档、Docker 和共享脚本变化触发相关检查。

- [ ] **Step 2: 运行合同脚本并确认红灯**

Run: `powershell -ExecutionPolicy Bypass -File .\scripts\verify-workflows.ps1`

Expected: FAIL because three CI files do not exist.

- [ ] **Step 3: 实现 Backend CI**

使用 `actions/checkout@v6`、`actions/setup-java@v5` 与 Java 17。服务容器使用 MySQL 8.4、Redis 7.4、RabbitMQ 3-management；只使用 CI 局部测试凭据，通过环境变量传入应用，执行 `mvn test`。

- [ ] **Step 4: 实现 Frontend CI**

使用 `actions/setup-node@v6` 与 Node 20、npm cache 指向 `frontend/package-lock.json`，依次执行 `npm ci`、`npm run type-check`、`npm run build`。

- [ ] **Step 5: 实现 AI Service CI**

使用 Python 3.11，安装 requirements 和 CPU 版 PyTorch，执行 `python -m pytest -q tests -m "not model_integration"`。不得下载真实 checkpoint。

- [ ] **Step 6: 调整 Secret Scan 和路径过滤**

Secret Scan 继续扫描完整提交历史，不因 paths 过滤跳过任何 PR。三个构建 workflow 按自身目录触发，同时将根 `.gitignore`、共享脚本、Docker、README 和契约纳入必要触发范围。

- [ ] **Step 7: 验证并提交 CI**

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\verify-workflows.ps1
git add .github scripts/verify-workflows.ps1
powershell -ExecutionPolicy Bypass -File .\scripts\verify-repository.ps1 -CheckIndex
git commit -m "ci: validate all monorepo services"
```

Expected: workflow contract exits `0`。

### Task 6: 完成三服务回归与浏览器验收

**Files:**
- Modify only if verification exposes a defect: files owned by Tasks 1-5
- No new feature files

**Interfaces:**
- Consumes: 完整 Monorepo、Docker 服务和本地忽略的模型文件。
- Produces: 可重复的回归记录；不产生新的公共 API。

- [ ] **Step 1: 执行完整静态安全检查**

Run: `powershell -ExecutionPolicy Bypass -File .\scripts\verify-repository.ps1`

Run: `git ls-files | Select-String -Pattern '\.(pth|png|jpg|jpeg|tif|tiff|pdf|sql)$|(^|/)\.env$'`

Expected: safety script passes；逐项复核搜索结果，SQL 只允许 Flyway migration 与 `docker/mysql/init.sql`，图片只允许无患者信息的前端展示资产，其他匹配项必须移出 index。

- [ ] **Step 2: 执行三套自动测试**

Run: `mvn test`

Run: `npm.cmd --prefix .\frontend run type-check`

Run: `npm.cmd --prefix .\frontend run build`

Run: `python -m pytest -q .\ai-service\tests -m "not model_integration"`

Run: `python -m pytest -q .\ai-service\tests\test_model_loader.py -m model_integration`

Expected: all exit `0`; report actual test counts。

- [ ] **Step 3: 启动基础设施与 AI 服务**

确保忽略的模型文件存在，使用本地 `docker/.env` 启动 Compose。验证 MySQL、Redis、RabbitMQ、Qdrant 与 AI health；不得把 `.env` 输出到日志或提交。

- [ ] **Step 4: 启动 Java 与前端并执行浏览器冒烟测试**

Java 使用本地安全环境变量启动，前端从 `frontend/` 启动。验证登录、医生 Agent 页面、任务查询、任务详情、临床队列卡片、分页和受控跳转；确认其他医生数据不可见。

- [ ] **Step 5: 验证 Java 到迁移后 AI 服务**

创建或复用匿名测试病例和非敏感测试图像，执行图像质量检测与血管分割，确认 Java 保存结果且 Python storage 位于被忽略目录。不得提交测试产物。

- [ ] **Step 6: 只提交验收中必要修正**

若无修正则不创建空提交；若有修正，运行所属任务全部验证后使用：

先用 `git diff --name-only` 列出验收修正，并仅对该列表中的实际文件路径逐个执行 `git add --`；随后运行安全脚本并提交为 `fix: complete monorepo migration verification`。禁止使用 `git add .`。

### Task 7: 最终审查、远程 CI 与 Pull Request

**Files:**
- Review only: Tasks 1-6 touched files

**Interfaces:**
- Consumes: 完整分支和所有验证结果。
- Produces: 可审查的 Pull Request，不直接修改 `master`。

- [ ] **Step 1: 检查提交边界与 Git 状态**

Run: `git status --short`

Run: `git log --oneline origin/master..HEAD`

Expected: worktree clean；提交按安全、前端、AI、文档/Docker、CI、必要修正分组。

- [ ] **Step 2: 请求完整分支代码审查**

重点审查：禁止文件、路径穿越、环境示例、Compose bind mount、CI 密钥、模型测试分层、README 命令和 Agent 前端契约。只修复经过验证的 Important/Critical 问题并重跑相关测试。

- [ ] **Step 3: 执行最终验证**

重新运行 Task 6 的安全、Java、Vue、Python 和 Compose 命令。任何失败都阻止推送和完成声明。

- [ ] **Step 4: 推送分支并创建 PR**

Push: `git push -u origin feature/monorepo-migration`

PR base: `master`

PR 描述列出目录决策、禁止提交内容、三套测试结果、模型集成测试结果和已知的大包构建警告。创建后附加 PR 到当前任务。

- [ ] **Step 5: 等待 GitHub Actions**

确认 Backend CI、Frontend CI、AI Service CI 和 Secret Scan 全部通过。远程失败必须定位并通过新提交修复，不得在 GitHub UI 中跳过检查。
