# RetinaVision Backend

RetinaVision Backend 是眼底图像 AI 辅助分析平台的业务中枢。系统以 Spring Boot 为浏览器和 AI 服务之间的唯一业务入口，负责患者检查流程、医生归属、异步推理、结果审阅、报告签发、RAG 知识问答与只读临床 Agent。

> 本项目用于工程实践与辅助复核。图像质量评分、血管分割结果、大模型草稿和 Agent 回答均不构成医学诊断；正式报告必须由负责医生审核并签发。

## 核心能力

- **患者检查流程**：匿名患者档案、病例申请、医生分配、图像上传、提交与撤回、脱敏进度和已签发报告查询。
- **医生工作台**：仅访问本人负责病例，查看质量评分，创建血管分割任务，处理失败任务，审核结果并签发 PDF。
- **异步 AI 任务**：Transactional Outbox、RabbitMQ Publisher Confirm、手动 ACK、原子任务 claim、有限重试和死信恢复。
- **Python 推理集成**：通过 HTTP multipart 调用图像质量检测和血管分割服务，持久化结构化指标与 mask。
- **Redis 安全控制**：登录失败限流、JWT 登出黑名单和基于 Lua 的 AI 任务配额。
- **RAG 知识助手**：Spring AI Embedding、Qdrant 检索、知识受众过滤、引用校验和低相关兜底。
- **只读临床 Agent**：LLM Skill Router、Java Native Skill、受控 Function Calling、服务端多轮上下文、结构化分页和对象级鉴权。
- **AI 治理**：Prompt 版本、RAG/Prompt 评测、调用审计、模型质控和管理员发布门槛。

## 角色边界

| 角色 | 可以做什么 | 不能做什么 |
|---|---|---|
| 患者 `USER` | 管理本人草稿、上传图像、提交检查、查看进度与正式报告 | 创建技术任务、查看 mask/日志、审核或签发 |
| 医生 `DOCTOR` | 管理本人负责患者、创建分割任务、结果复核、审核和签发 | 访问其他医生病例、管理 Prompt/RAG 或平台质控 |
| 管理员 `ADMIN` | 用户与医生管理、AI 质控、Prompt/RAG/Skill 治理 | 读取病例临床详情、原图、医生意见和报告正文 |
| 研究员 `RESEARCHER` | 使用允许受众的知识助手 | 访问临床病例、任务、结果和临床 Agent |

所有临床对象均执行病例归属鉴权。越权访问与资源不存在返回一致结果，避免泄露患者或病例是否存在。

## 系统架构

```text
Vue Web
   │ JWT + HTTP /api
   ▼
Spring Boot API
   ├── MySQL：用户、患者、病例、任务、结果、审核、报告与审计
   ├── Redis：登录限流、JWT 黑名单、任务配额
   ├── Qdrant：知识分块向量与引用元数据
   ├── LLM / Embedding：OpenAI-compatible API（Spring AI 1.1.8）
   └── Transactional Outbox → RabbitMQ
                                  │ Java Listener
                                  ▼
                         Python FastAPI / PyTorch
                                  │
                                  └── 质量指标、分割 mask、模型元数据
```

RabbitMQ 消费者运行在 Java 后端进程中。Python 服务只负责模型加载与推理，不访问业务数据库，也不接收浏览器 JWT。

## 关键流程

### 异步分析任务

1. 医生创建任务，后端在同一数据库事务中写入 `analysis_task` 和 `analysis_outbox`。
2. Outbox Publisher 将事件发布到 RabbitMQ，并等待 Broker Confirm。
3. Java Listener 手动消费消息，通过条件更新将任务从 `WAITING/RETRYING` claim 为 `RUNNING`。
4. Listener 调用 Python 推理服务；成功后保存结果并更新为 `SUCCESS`，失败则记录有限错误摘要并进入重试或死信流程。
5. 数据库状态 claim 保证重复消息不会重复执行已完成任务，整体交付语义为至少一次。

### RAG 与 Agent

知识助手负责“基于知识库回答医学资料问题”：文档切分后写入 Qdrant，提问时通过 `DocumentRetriever` 检索，回答必须引用本轮真实命中的知识片段。

临床 Agent 负责“用自然语言查询业务数据”，采用两阶段执行。第一阶段把用户问题、当前角色可见的版本化 Skill 定义和 JSON 输出契约交给 LLM Router，模型只返回 `skillCode`、置信度和参数；该阶段不注册 Function Calling 工具，也不发送病例、任务或临床队列数据。

第二阶段由 Java 校验角色、置信度和参数后选择执行模式：工作量、病例筛选、病例摘要、随访比较、任务查询和待审核/待签发队列走 Native `DIRECT` Skill，由 Service/Mapper 查询后直接组装固定摘要与结构化数据，不把查询结果再次发送给 LLM；医学知识问答走 `TOOL_CALLING`，医生侧只注册 `searchMedicalKnowledge`。任务、病例和临床队列查询均在 SQL 阶段限定 `assigned_doctor_id`，列表按最近更新时间倒序且每页最多 10 条，翻页会保留筛选与带类型的本页引用。

Skill 版本使用带预期参数和反例的语料评测，候选版本未达到路由准确率 90%、参数准确率 95% 和全部安全检查前不能启用。Agent 没有创建任务、修改病例、审核或签发工具；任务和队列响应只返回匿名编号、状态、时间及脱敏错误摘要，不返回原图、mask、路径、完整结果 JSON 或未签发报告正文。

## 技术栈

- Java 17、Spring Boot 3.5、Spring Security
- MyBatis-Plus、MySQL 8、Flyway
- Spring AMQP、RabbitMQ、Transactional Outbox
- Redis、Lua
- Spring AI 1.1.8、OpenAI-compatible Chat/Embedding、Qdrant
- JUnit 5、Mockito、ArchUnit

## 目录结构

```text
src/main/java/com/example/retinavision/
├── analysis/       # 任务领域、应用用例和基础设施适配器
├── agent/          # Skill 路由、上下文和 Tool Calling 编排
├── ai/             # Python AI HTTP 客户端
├── config/         # Security、RabbitMQ、Spring AI 配置
├── controller/     # 浏览器 API
├── llm/            # Prompt 编排、安全策略与模型客户端
├── rag/            # Embedding、Qdrant 与引用校验
├── service/        # 病例、审核、报告和治理业务
└── pojo/           # DTO、VO 与持久化实体

src/main/resources/
├── db/migration/   # Flyway 迁移
├── mapper/         # MyBatis XML
└── application.yaml
```

## 本地启动

### 1. 准备环境变量

仓库不提供可直接使用的密码、JWT Secret 或模型密钥。先创建本地环境文件：

```powershell
Copy-Item .\docker\.env.example .\docker\.env
```

编辑 `docker/.env`，为数据库和 RabbitMQ 设置仅用于本机的密码。该文件已被 Git 忽略。

Java 后端至少需要以下环境变量：

| 变量 | 用途 |
|---|---|
| `DB_PASSWORD` | MySQL 业务账号密码 |
| `RABBITMQ_PASSWORD` | RabbitMQ 业务账号密码 |
| `JWT_SECRET` | 不少于 32 字节的随机签名密钥 |

启用大模型、Embedding 或远程 Qdrant 时，再配置：

| 变量 | 用途 |
|---|---|
| `RETINA_LLM_ENABLED` | 是否启用 Chat Model |
| `RETINA_LLM_BASE_URL` | OpenAI-compatible Chat API 地址 |
| `RETINA_LLM_API_KEY` | Chat API Key |
| `RETINA_LLM_MODEL` | Chat 模型名 |
| `RETINA_EMBEDDING_ENABLED` | 是否启用 Embedding |
| `RETINA_EMBEDDING_BASE_URL` | OpenAI-compatible Embedding API 地址 |
| `RETINA_EMBEDDING_API_KEY` | Embedding API Key |
| `RETINA_EMBEDDING_MODEL` | Embedding 模型名 |
| `RETINA_QDRANT_BASE_URL` | Qdrant 地址 |
| `RETINA_QDRANT_API_KEY` | 远程 Qdrant API Key，可选 |

可以将这些值写入被忽略的 `src/main/resources/application-local.yaml`，但不得提交真实凭据。仓库内只保留环境变量名称，不保留真实值。

### 2. 启动基础设施

```powershell
Set-Location .\docker
docker compose --env-file .env up -d mysql rabbitmq redis qdrant
docker compose ps
Set-Location ..
```

### 3. 启动 Python AI 服务

Python 服务是独立项目，默认地址为 `http://127.0.0.1:8000`。启动前应确认模型加载完成，并检查：

```powershell
Invoke-RestMethod http://127.0.0.1:8000/health
```

### 4. 启动后端

```powershell
mvn spring-boot:run
```

默认 API 地址为 `http://127.0.0.1:8080/api`。

## API 模块

| 模块 | 主要能力 |
|---|---|
| `/auth` | 注册、登录、当前用户和退出登录 |
| `/patients`、`/cases` | 匿名患者档案、病例申请和进度 |
| `/images` | 图像上传、预览和质量检测 |
| `/analysis-tasks`、`/analysis-results` | 异步任务、结果、日志和比较 |
| `/doctor/reviews` | 医生待办、审核和签发流程 |
| `/knowledge` | 知识文档治理与 RAG 问答 |
| `/agent` | 角色化只读智能助手 |
| `/prompt-templates`、`/prompt-evaluations` | Prompt 版本与评测 |
| `/rag-evaluations`、`/agent-skills` | RAG 与 Skill 治理 |
| `/quality-control`、`/admin` | 模型质控和平台管理 |

公共响应使用统一 `ApiResponse<T>`；分页使用 `PageResult<T>`；图片、mask 和报告下载接口直接返回二进制流。

## 验证

```powershell
mvn test
mvn clean package
```

测试覆盖任务状态机、消息幂等、Outbox、权限边界、患者流程、RAG 引用、Prompt 安全、Agent Skill 路由与工具白名单。真实 MySQL、RabbitMQ、Qdrant、模型和 LLM 的端到端验证仍需在完整本地环境中执行。

## 安全约束

- 不提交 `.env`、`application-dev.yaml`、`application-local.yaml`、API Key、JWT、数据库密码或患者数据。
- GitHub Actions 会在 push、Pull Request 和手动触发时运行 Gitleaks，阻止新的凭据进入仓库。
- Docker Compose 默认只将 MySQL、RabbitMQ、Redis、Qdrant 和 AI 服务绑定到 `127.0.0.1`。
- 不向大模型发送原始图像、mask、文件绝对路径、JWT 或患者真实身份。
- LLM 和工具审计仅保存脱敏摘要，不保存完整 Prompt、长响应或工具原始结果。
- 文件路径在读写前必须规范化并校验仍位于配置根目录内。
- AI 输出仅作辅助参考，正式医学意见只来自负责医生保存的审核记录。

如任何真实凭据曾被提交到远程仓库，仅从最新文件删除并不足够；必须先在供应商侧撤销并重新生成，再根据仓库协作情况决定是否清理 Git 历史。
