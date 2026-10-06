# RetinaVision 渐进式 Monorepo 迁移设计

## 1. 背景与目标

RetinaVision 当前由三个本地目录组成：外层工作区中的 Vue 3 前端、已有独立 Git 历史的 Java 后端仓库，以及未纳入 Git 的 Python AI 推理服务。GitHub 目前只展示 Java 后端，导致前端 Agent 结构化卡片、完整交互体验和 Python 推理实现无法随同后端版本审查、克隆和验证。

本次迁移以现有 Java 后端 GitHub 仓库为唯一版本库，采用渐进式 Monorepo：保留 Java 后端在仓库根目录，将前端加入 `frontend/`，将 Python 推理服务加入 `ai-service/`。迁移不改变浏览器 API、Java 到 Python 的内部接口、数据库结构、RabbitMQ 链路或模型算法。

成功标准：

- 一个 GitHub 仓库包含可构建的 Vue、Java 和 Python 源码。
- 现有 Java Git 历史、IDEA 工程和 Maven 根目录保持兼容。
- 模型权重、患者数据、上传文件、推理产物、密钥和本地配置不进入 Git。
- 三套技术栈拥有独立 CI，公共 Docker 配置能够引用仓库内的 AI 服务。
- 医生 Agent 任务查询和临床队列在迁移后保持可用。

## 2. 方案选择

采用渐进式 Monorepo，而不是移动全部 Java 文件或拆成三个仓库。

目标结构：

```text
RetinaVision/
├── .github/workflows/
├── ai-service/
│   ├── models/
│   ├── src/
│   ├── tests/
│   ├── Dockerfile
│   ├── requirements.txt
│   └── README.md
├── docker/
├── docs/
│   └── BACKEND.md
├── frontend/
│   ├── src/
│   ├── package.json
│   ├── package-lock.json
│   └── README.md
├── src/                       # Java 后端源码
├── pom.xml
├── API_CONTRACT.md
├── README.md
└── .gitignore
```

Java 保留在根目录可避免近 500 个源码文件的无收益重命名，也不破坏现有 Maven、Flyway、IDEA 和 worktree 习惯。前端和 AI 服务获得明确边界，仍能由同一 PR 同步修改公共契约和启动配置。

## 3. 来源与迁移边界

迁移来源：

- 前端：`C:/codexcode/RetinaVision` 中的 Vue 项目文件和 `src/`。
- AI 服务：`C:/codexcode/RetinaVision/retinavision-ai` 中的源码、测试、Dockerfile、依赖与 README。
- Java 与 Git 历史：当前仓库最新 `origin/master`。

采用复制后验证，不立即删除外层来源目录。仓库内的新目录通过全部验证并合并后，才将外层副本视为可清理对象。

前端迁入：

- `src/`
- `package.json`、`package-lock.json`
- `index.html`
- `env.d.ts`
- `tsconfig.json`、`tsconfig.node.json`
- `vite.config.ts`
- 脱敏后的 `.env.development.example`

AI 服务迁入：

- `src/`
- `tests/`
- `scripts/` 中可复现、无敏感数据的维护脚本
- `requirements.txt`
- `Dockerfile`、`.dockerignore`
- `README.md`
- 空的 `models/` 目录说明

不迁入个人简历与面试文档、数据库备份、Codex 临时日志、本地输出目录或空的外层 `.git` 目录。

## 4. 安全与体积边界

根 `.gitignore` 统一覆盖三套技术栈，同时保留各子目录必要的局部规则。以下内容禁止提交：

- 前端：`node_modules/`、`dist/`、`.env*`、Vite/Codex 日志。
- Java：`target/`、`uploads/`、本地 Spring 配置、`docker/.env`。
- Python：虚拟环境、缓存、`.env`、`models/*.pth`、`storage/`、`storage-e2e/`、`logs/`。
- 通用：IDE 配置、worktree、临时目录、数据库导出、原始图像、mask、PDF 和推理结果。

允许提交的环境文件只能是使用占位符的 `*.example`。迁移前扫描候选文件名、内容和体积；暂存后再次扫描 Git index，防止被忽略文件通过强制添加进入提交。

当前模型 `model_new.pth` 约 67.34 MB，不使用 Git LFS，也不提交到 GitHub。`ai-service/models/README.md` 记录预期文件名、环境变量、可选 SHA-256 校验方法和本地放置步骤；模型来源未经确认时不提供公开下载链接。

## 5. 文档职责

- 根 `README.md`：系统定位、三服务架构、角色边界、统一启动顺序、验证命令和医疗安全声明。
- `docs/BACKEND.md`：迁移现有 Java README 的后端实现、配置、RabbitMQ、Redis、RAG、Agent 和 Flyway 细节。
- `frontend/README.md`：Node 版本、安装、开发、构建、代理和目录说明。
- `ai-service/README.md`：Python 版本、依赖、模型放置、推理 API、测试和存储边界。
- 根 `API_CONTRACT.md`：只维护浏览器到 Java 的公共 HTTP 契约。

Java 到 Python 的 multipart 与结果协议属于内部接口，放入 `docs/BACKEND.md` 和 `ai-service/README.md`，不混入浏览器契约。

## 6. Docker 与本地运行

保留 Java 与前端的本地开发方式。本次只调整现有 Docker Compose 的 AI 路径：

```text
${RETINAVISION_AI_PROJECT_ROOT:-../../retinavision-ai}
```

改为：

```text
${RETINAVISION_AI_PROJECT_ROOT:-../ai-service}
```

Compose 文件仍位于 `docker/`，继续编排 MySQL、Redis、RabbitMQ、Qdrant 和 Python AI。模型和 AI 存储继续使用本地 bind mount，不构建进镜像。

默认端口保持：前端 5173、Java 8080、AI 8000、MySQL 3307、Redis 6379、RabbitMQ 5672、Qdrant 6333。迁移不新增生产部署承诺，也不把 Java 和 Vue 强行容器化。

## 7. CI 设计

保留现有 Gitleaks 工作流，并新增按路径触发的三条流水线：

### Backend CI

- 使用 Java 17。
- 提供 MySQL、Redis 和 RabbitMQ 服务。
- 使用 CI 专用环境变量，不写入仓库。
- 执行 `mvn test`，包含 Spring 上下文和 Flyway 验证。

### Frontend CI

- 使用 Node.js 20。
- 在 `frontend/` 执行 `npm ci`、`npm run type-check` 和 `npm run build`。
- 不上传包含运行时配置的构建产物。

### AI Service CI

- 使用 Python 3.11 和 CPU 版 PyTorch。
- 在 `ai-service/` 安装依赖并执行 `python -m pytest -q`。
- 单元测试不得依赖真实模型权重；需要模型的本地验收独立记录。

公共 Docker、契约或根级构建配置变化时触发相关全部检查。CI 不使用真实患者数据和生产密钥。

## 8. 实施与提交边界

迁移在基于最新 `origin/master` 的独立 worktree 和功能分支中完成。建议提交顺序：

1. 仓库安全边界和 Monorepo 骨架。
2. Vue 前端源码与前端说明。
3. Python AI 源码、测试与模型说明。
4. Docker 路径、系统文档和公共契约。
5. 三套 CI 工作流及最终修正。

每个提交只包含对应范围，禁止将外层临时文件混入。最终通过 Pull Request 合并，不直接推送 `master`。

## 9. 验证与验收

自动验证：

```powershell
mvn test
npm.cmd --prefix .\frontend run type-check
npm.cmd --prefix .\frontend run build
python -m pytest -q .\ai-service\tests
docker compose -f .\docker\docker-compose.yml config
```

迁移安全验证：

- Git 暂存区不存在模型、数据库、图像、mask、PDF、日志、构建产物和本地环境文件。
- 没有 API Key、JWT Secret、数据库密码或 RabbitMQ 密码。
- 没有超过约定阈值的意外大文件。
- Gitleaks 工作流通过。

运行验收：

- Docker 基础设施与 Python AI 健康检查通过。
- Java 可以调用迁移后的 Python 血管分割接口。
- Vue 可以登录并访问 Java API。
- 医生 Agent 可以展示任务列表、任务详情和临床队列结构化卡片。
- 前端受控动作仍只能跳转允许的病例、任务和审核路径。

## 10. 回滚与非目标

迁移失败时保留功能分支和诊断信息，`master` 与外层原始目录不受影响。不得使用破坏性重置覆盖用户文件。PR 合并前不删除外层前端和 AI 服务；合并后清理也应作为单独、本地确认的操作。

本次非目标：

- 不改变 Java 包结构或迁移到 `backend/`。
- 不修改业务 API、数据库表或 Agent 行为。
- 不重新训练或发布模型。
- 不接入 Git LFS、MCP、Kubernetes 或生产部署平台。
- 不将 Java 和前端容器化。
- 不实现患者 Agent 新功能；患者 Agent 在 Monorepo 收尾完成后另行设计。
