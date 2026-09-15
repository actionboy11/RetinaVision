# RetinaVision Backend

RetinaVision Backend 是系统的业务核心和浏览器 API 入口。它负责 JWT 鉴权、病例与图像、异步分析任务、RabbitMQ 消息消费、Python AI 调用、结果持久化、文件预览和 Dashboard 统计。

系统总览和统一启动顺序见上级目录 `README.md`；浏览器接口字段以 `API_CONTRACT.md` 为准。

## 当前能力

- 用户注册、登录、当前用户、退出登录、JWT 黑名单和登录失败限流
- 病例分页、详情、新建、更新、对象级访问控制和软删除
- 眼底图像上传、本地存储、列表、预览、软删除、自动质量检测和幂等重检
- 分析任务分页、创建、质量门控、详情、取消、重试、AI 配额和状态日志
- RabbitMQ publisher confirm、JSON 消息、手动 ACK 和死信队列
- 调用 FastAPI 图像质量检测与血管分割接口，保存质量投影、mask 和分析结果
- 任务统计、队列统计和近期任务趋势
- 分割结果 JSON 查询与 mask 二进制预览
- 人工反馈、修正版本、医生审核、报告草稿、医生签发 PDF 和报告历史
- 可通过配置接入 Qwen 或 DeepSeek，为医生生成报告草稿和结构化结果解释
- RAG 医疗知识助手通过 Qwen Embedding 与 Qdrant 检索知识库，面向所有登录用户提供带引用的资料解释
- Prompt 模板数据库版本管理、管理员启用版本切换和脱敏 LLM 调用审计
- 管理员用户列表、角色授予、医生 professionalNo 维护和死信恢复

当前可执行 `IMAGE_QUALITY_CHECK` 和 `VESSEL_SEGMENTATION`。质量检测是 OpenCV/NumPy 规则评分的工程门控；血管分割使用 Python AI 服务中的 PyTorch 模型。AI 结果仅供辅助分析，正式 PDF 报告必须由医生审核通过后签发。

## 技术栈

- Java 17
- Spring Boot 3.5
- Spring Web、Validation、Security
- Spring AMQP / RabbitMQ
- MyBatis-Plus 3.5
- MySQL 8
- JJWT
- 本地文件系统（当前主流程）

Redis 已用于登录失败限流、JWT 登出黑名单和 AI 任务限流/配额。MinIO 客户端仍未进入核心业务链路。

## 目录

```text
RetinaVision/
├─ docker/
│  ├─ docker-compose.yml       # MySQL + RabbitMQ + Redis + AI
│  └─ mysql/init.sql           # 首次建库
├─ src/main/java/com/example/retinavision/
│  ├─ ai/                      # Python AI HTTP 客户端
│  ├─ config/                  # Security、RabbitMQ 等配置
│  ├─ controller/              # 浏览器 API
│  ├─ mapper/                  # MyBatis Mapper
│  ├─ mq/                      # 任务消息、发布者和消费者
│  ├─ pojo/                    # DTO、VO、Entity
│  └─ service/                 # 业务与任务执行
├─ src/main/resources/
│  ├─ application.yaml
│  └─ mapper/
├─ src/test/                   # Service、MQ、AI client 测试
└─ pom.xml
```

## 运行架构

```text
Vue -> Spring Controller -> Service -> MySQL / uploads
                               │
                               └-> RabbitMQ exchange
                                      │
                                      ▼
                              AnalysisTaskListener
                                      │
                                      ▼
                         AnalysisTaskExecutionService
                                      │ HTTP multipart
                                      ▼
                              Python AI :8000
```

Java 进程既是任务生产者，也是任务消费者。任务创建接口不直接执行模型，接口返回后由监听器异步处理。

## Analysis 模块与 Outbox

第一个纵向切片位于 `src/main/java/com/example/retinavision/analysis/`：

```text
analysis/
├─ domain/          # 不依赖框架的任务状态机与领域模型
├─ application/     # 用例、命令和输入/输出端口
└─ infrastructure/  # MyBatis、AI、文件、报告、MQ 与 Outbox 适配器
```

依赖方向为 `infrastructure -> application -> domain`；应用层只依赖端口，领域层不依赖 Spring、MyBatis、MQ、HTTP 或文件系统。`AnalysisTaskExecutionServiceImpl` 保留在遗留 service 包中，作为事务性兼容 facade：它把旧的 MQ 消息入口转换为应用用例调用，不承载新的执行编排。

任务创建在一个数据库事务中持久化 `analysis_task` 及 `analysis_outbox` 的交付意图。因此创建成功表示“任务和交付意图已持久化”，不表示消息已经存在于 RabbitMQ。调度发布器随后以 Broker confirm 发布已到期的 Outbox 事件；确认后才标记为 `PUBLISHED`。

Outbox 默认配置位于 `application.yaml`，可通过外部配置覆盖：

```yaml
retina:
  outbox:
    fixed-delay: 1s
    batch-size: 50
    retry-delay: 10s
    claim-timeout: 2m
```

发布器按 `PENDING -> PROCESSING -> PUBLISHED` 处理事件。条件更新确保同一事件只有一个发布器实例获得 claim；发布或反序列化失败会回到 `PENDING`，并在 `retry-delay` 后重试；超过 `claim-timeout` 的 `PROCESSING` 行会恢复为待处理。发布确认后、标记 `PUBLISHED` 前发生进程故障时，事件可能再次发布，因此交付语义为至少一次。任务消费者的原子 claim 会将重复消息处理为 `IGNORED`。

运维检查只读取元数据，严禁查询或打印 `payload_json`：

```sql
SELECT status,
       COUNT(*) AS event_count,
       MIN(created_at) AS oldest_created_at,
       MAX(attempt_count) AS max_attempt_count
FROM analysis_outbox
GROUP BY status;
```

重点监测待处理数量、最早待处理时间、处理时长、尝试次数和发布失败。当前没有自动清理 `PUBLISHED` 行；生产部署前应补充保留/清理策略。现有测试覆盖单元和契约行为，尚未覆盖真实 MySQL/Testcontainers 下的并发争用。

## 基础设施

从本目录执行：

```powershell
Set-Location .\docker
docker compose up -d
docker compose ps
Set-Location ..
```

默认开发服务：

| 服务 | 地址/端口 | 本地账号 |
|---|---|---|
| MySQL | `localhost:3307/retina_vision` | `retina` / `retina123456` |
| RabbitMQ AMQP | `localhost:5672` | `retina` / `retina123456` |
| RabbitMQ 管理台 | `http://localhost:15672` | `retina` / `retina123456` |
| Redis | `localhost:6379` | 本地开发默认无密码 |

这些凭据仅用于本机开发。

`docker/mysql/init.sql` 创建以下表：

- `sys_user`
- `medical_case`
- `image_file`
- `analysis_task`
- `analysis_result`
- `task_log`

初始化 SQL 只负责创建数据库，业务表由 `src/main/resources/db/migration` 中的 Flyway 脚本维护；已有数据库首次接入会 baseline 到 V1，再执行 V2 及后续迁移。

## 本地配置

应用默认激活 `dev` profile。创建 `src/main/resources/application-dev.yaml`；该文件已在 `.gitignore` 中：

```yaml
spring:
  datasource:
    driver-class-name: com.mysql.cj.jdbc.Driver
    url: jdbc:mysql://localhost:3307/retina_vision?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true
    username: retina
    password: retina123456
  rabbitmq:
    host: localhost
    port: 5672
    username: retina
    password: retina123456
    virtual-host: /
    publisher-confirm-type: correlated
    publisher-returns: true
    template:
      mandatory: true
    listener:
      simple:
        acknowledge-mode: manual

retina:
  upload:
    result-root: uploads/results
  mq:
    analysis-exchange: retina.analysis.exchange
    analysis-routing-key: retina.analysis.task.created
    analysis-queue: retina.analysis.task.queue
    analysis-dead-letter-exchange: retina.analysis.dlx
    analysis-dead-letter-routing-key: retina.analysis.task.dead
    analysis-dead-letter-queue: retina.analysis.task.dead.queue
```

`application.yaml` 中已有以下默认值：

```yaml
server:
  port: 8080
  servlet:
    context-path: /api

retina:
  ai:
    base-url: http://127.0.0.1:8000
    connect-timeout: 5s
    read-timeout: 5m
  upload:
    image-root: uploads/images
  llm:
    enabled: false
    provider: qwen
    base-url: https://{WorkspaceId}.cn-beijing.maas.aliyuncs.com/compatible-mode/v1
    api-key: ${RETINA_LLM_API_KEY}
    model: qwen-plus
  embedding:
    enabled: false
    provider: qwen
    base-url: https://{WorkspaceId}.cn-beijing.maas.aliyuncs.com/compatible-mode/v1
    api-key: ${RETINA_EMBEDDING_API_KEY}
    model: text-embedding-v4
    dimension: 1024
  qdrant:
    base-url: http://127.0.0.1:6333
    collection: retina_knowledge_chunks
    score-threshold: 0.2
```

生产或共享环境必须通过外部配置覆盖 JWT secret 和所有凭据，不能沿用开发值。

### 大模型报告草稿

`retina.llm.*` 用于医生工作台中的“AI 生成草稿”。后端使用 OpenAI-compatible Chat Completions API 调用 Qwen 或 DeepSeek，浏览器不直接访问大模型。该功能只发送结构化分析结果、图像质量摘要和必要上下文，不发送原始眼底图、mask、token、绝对路径或患者真实身份。生成内容写入当前 `DRAFT` 报告的 `draftJson`，仅作为医生审核前参考；正式 PDF 的医生所见、审核结论和处理建议只来自医生保存的审核记录。

草稿生成要求使用“辅助分析、建议复核、结合临床”的语气。后端会检查 `findings`、`conclusion`、`recommendation`，如果包含“诊断为”“确诊”“排除”“明确患有”“无需复查”等确定性诊断措辞，将拒绝写入并保留旧草稿。

AI 草稿生成成功、医生审核保存和 PDF 签发成功后会追加 `task_log` 审计记录。此类记录只用于任务详情时间线展示，`fromStatus` 与 `toStatus` 使用当前任务状态且通常相同，不会改变 `analysis_task.status`。血管分割结果页展示的图像质量状态/评分来自图像质量检测任务投影的 `qualitySummary`，不要求血管分割 `resultJson` 自带 `imageQualityScore`。

DeepSeek 配置示例：

```yaml
retina:
  llm:
    enabled: true
    provider: deepseek
    base-url: https://api.deepseek.com
    api-key: ${RETINA_LLM_API_KEY}
    model: deepseek-v4-flash
```

### RAG 医疗知识助手

知识助手配置位于 `retina.embedding.*` 和 `retina.qdrant.*`。管理员通过 `/knowledge/documents` 录入纯文本或 Markdown，后端按 Markdown/段落感知切分后调用 Qwen Embedding，并将向量和引用 payload 写入 Qdrant。管理员可重建索引、启用/停用或删除文档；停用文档不参与检索，删除文档会同步删除 MySQL chunk 与 Qdrant points。所有已登录用户可通过 `/knowledge/chat` 提问，回答必须带引用来源和固定免责声明；该功能不替代医生诊断、治疗建议或 PDF 报告签发。

引用现在只显示模型指认且可在本次检索 chunk 中逐字核验的摘录；没有可核验证据时，返回固定“依据不足”回答和空引用。Flyway `V8__rag_grounded_evaluation.sql` 新增未启用的 RAG v2 Prompt，要求返回 `answer` 与 `evidence[{chunkId,quote}]`。当前 v1 不强制提供 evidence，因此迁移到 v2 前部分问答会安全降级为“依据不足”，不会把所有检索命中伪装为引用。独立 `/rag-evaluations` 评测使用合成样本与 `retina_rag_eval_v1` collection，分别记录 Hit@3/MRR 和逐题回答引用校验；医生须判断引用是否真正支持回答。管理员只有在最新评测自动通过且医生批准后才能启用 v2。评测会调用真实 Embedding 和 LLM，可能产生费用，但不会写入正式知识库、病例或 PDF。

`V9__rag_evaluation_configuration.sql` 为评测记录增加 Embedding 模型与相似度阈值快照；评测执行和候选版本启用时均会核对当前配置。V8 已应用的环境应继续执行 V9，不要修改 V8 或对 Flyway 历史执行 `repair`。

### Prompt Engineering 平台

Flyway `V6__prompt_engineering.sql` 初始化 `REPORT_DRAFT_GENERATION`、`RAG_KNOWLEDGE_CHAT`、`CASE_TREND_SUMMARY` 三个模板及 v1 版本。三个业务 Service 继续负责构造脱敏 user context，`LlmOrchestrationService` 统一读取数据库中的启用 system prompt、调用 Qwen/DeepSeek、校验 JSON 输出契约与医疗安全词，并记录调用摘要。

管理员可通过 `/prompt-templates` 查看模板和版本，通过 `/prompt-templates/{templateCode}/active-version` 切换已经入库的版本，通过 `/llm-call-logs` 查看最近 200 条调用摘要。第一版不提供在线编辑能力；新增版本需要经过代码审查并通过后续 Flyway 迁移发布。若模板缺失、停用或没有有效启用版本，对应 LLM 场景会返回可读业务错误，不回退到源码中的隐藏 Prompt。

`llm_call_log` 不保存完整 system prompt、user context、API Key、token、患者真实身份、文件绝对路径或模型长响应。错误摘要只保留异常类型和通用错误说明，业务事务失败时审计记录通过独立事务保留。

Flyway `V7__prompt_evaluation.sql` 为已存在数据库新增报告草稿评测记录、版本发布标记和未启用候选 v2；新环境随 Flyway 顺序自动执行 V6、V7。`POST /prompt-evaluations/runs` 仅管理员可用，会在独立单线程有界队列中对固定合成样本分别调用当前版和候选版。医生通过 `PUT /prompt-evaluations/runs/{id}/review` 对结果给出 1–5 分与批准/拒绝意见；管理员及医生通过 `GET /prompt-evaluations/runs` 和详情查看进度、自动校验及有限长度的合成输出。新版本发布前必须通过最新一轮与当前版对照的自动校验及医生批准；已发布版本可回滚。评测不会写入正式报告，也不能替代医生审核。运行真实评测需要配置可用的大模型并会产生 API 调用费用。

## 启动与验证

先启动 MySQL、RabbitMQ、Redis 和 Python AI，再执行：

```powershell
mvn spring-boot:run
```

### Redis 限流、黑名单与配额

默认规则为：用户名登录失败 5 次/15 分钟、来源 IP 失败 20 次/15 分钟；AI 每用户 10 次/分钟、500 次/上海自然日。创建任务和人工重试成功投递都会消耗配额，RabbitMQ 自动重试不会重复计数。

观察 Redis 数据时使用 `SCAN`，避免在共享环境执行阻塞式 `KEYS *`：

```powershell
docker exec retina_redis redis-cli PING
docker exec retina_redis redis-cli --scan --pattern "rv:*"
docker exec retina_redis redis-cli TTL "<上一步返回的key>"
```

Key 仅保存用户名/IP 的 SHA-256 摘要、JWT `jti` 或用户 ID，不保存密码、JWT 原文和患者数据。停止 Redis 后，登录密码校验保持可用；JWT 鉴权以及 AI 创建/人工重试会返回 HTTP 503。

生产环境通过 `REDIS_HOST`、`REDIS_PORT`、`REDIS_PASSWORD` 覆盖连接参数，不应把 Redis 端口暴露到公网。

API 基础地址：

```text
http://127.0.0.1:8080/api
```

运行测试和构建：

```powershell
mvn test
mvn clean package
```

测试默认不等于完整联调。涉及真实数据库、RabbitMQ、文件和 AI 服务时，还需按根 README 完成端到端验证。

`mvn test` 仍是日常验证命令。本隔离分支连接的开发数据库已经有用户拥有、但本分支未包含的 V3/V4 Flyway 迁移；因此在该共享数据库上执行完整 Flyway 验证，需要这些迁移同时存在。不要对共享数据库执行 Flyway repair、reset，也不要复制或修改这些用户迁移；若标准命令因此失败，应记录为环境受限，并在合适环境中验证。

## API 概览

所有路径均位于 `/api` 下。JSON 业务接口使用统一响应包装；图片和报告接口直接返回二进制。

| 模块 | 主要路径 |
|---|---|
| Auth | `/auth/register`、`/auth/login`、`/auth/me`、`/auth/logout` |
| Case | `/cases`、`/cases/{caseId}` |
| Image | `/cases/{caseId}/images`、`/images/{imageId}/preview`、`/images/{imageId}/quality-check` |
| Task | `/analysis-tasks`、`/analysis-tasks/{taskId}`、`cancel`、`retry` |
| Result | `/analysis-tasks/{taskId}/result`、`/results/{resultId}/mask`、`/results/{resultId}/report` |
| Clinical workflow | `/analysis-results/{resultId}/feedback`、`corrections`、`review`、`report-draft`、`report-draft/ai-generate`、`report-sign`、`reports` |
| Doctor | `/doctor/reviews`、`/doctor/reviews/{resultId}` |
| Admin | `/admin/users`、`/admin/users/{userId}/role`、`/admin/dead-letters/recover` |
| Prompt | `/prompt-templates`、`/prompt-templates/{templateCode}/versions`、`active-version`、`/llm-call-logs` |
| Prompt evaluation | `/prompt-evaluations/runs`、`/prompt-evaluations/runs/{id}`、`/prompt-evaluations/runs/{id}/review` |
| Log | `/analysis-tasks/{taskId}/logs`（兼容单数 `/log`） |
| Dashboard | `/admin/statistics/tasks`、`queue`、`task-trend` |

完整请求、响应、错误码和枚举见 `../API_CONTRACT.md`。

## AI 任务执行

### 创建和投递

`TaskServiceImpl` 校验病例与图像后：

1. 新增 `analysis_task`，状态为 `WAITING`。
2. 在同一事务中写入一条状态为 `PENDING` 的 `analysis_outbox` 事件。
3. 写入一条 `USER` 类型任务日志；提交后由 Outbox 发布器异步投递。

创建接口返回成功时，任务和消息交付意图已经持久化，但消息可能尚未到达 RabbitMQ。发布器等待 broker confirm 后才把 Outbox 行标记为 `PUBLISHED`；失败的发布会保留为可重试的 `PENDING` 行。

### 消费和状态

`AnalysisTaskListener` 监听 `retina.analysis.task.queue`，使用手动 ACK：

| 执行结果 | MQ 处置 |
|---|---|
| `SUCCESS`、`IGNORED` | ACK |
| `REQUEUE` | NACK 并重新入队 |
| `FAILED`、未处理异常 | NACK，不重新入队，进入死信队列 |

可消费状态为 `WAITING`、`RETRYING`。消费者先更新为 `RUNNING`，再执行 AI 调用；成功更新为 `SUCCESS`，失败更新为 `FAILED`。

### 调用 Python AI

Java 通过 multipart `file` 调用：

```text
POST {retina.ai.base-url}/v1/inference/image-quality-check
POST {retina.ai.base-url}/v1/inference/vessel-segmentation
```

质量检测响应直接写入 `analysis_result`，并将 `quality_status`、`quality_score`、`quality_result_id` 和 `quality_checked_at` 投影到 `image_file`。血管分割响应包含 `maskUrl`，Java 随后下载 PNG。为避免服务端请求伪造，下载地址必须与配置的 AI 基础地址具有相同 scheme 和 authority。

### 结果持久化

成功时：

- `resultJson`、模型名、模型版本和耗时写入 `analysis_result`。
- 血管分割 mask 保存到 `uploads/results/tasks/{taskId}/mask.png`。
- 血管分割结果的 `mask_bucket` 记录为 `local`，`mask_object_key` 记录相对路径；质量检测结果不生成 mask。
- 任务更新为 `SUCCESS` 并写入 `WORKER` 日志。

失败时：

- 任务更新为 `FAILED`。
- 错误信息移除换行并限制在 1024 字符内。
- 写入 `RUNNING -> FAILED` 的 `WORKER` 日志。

## 文件存储

```text
uploads/
├─ images/                   # 用户上传原图
└─ results/
   └─ tasks/{taskId}/mask.png
```

读取和写入路径都会归一化并检查是否仍位于配置根目录。不要将绝对主机路径保存为公共 URL。

`/results/{resultId}/report` 只下载最新的医生签发报告；未审核、未签发或只有草稿时返回冲突，不再回退旧结果字段。

## 已知契约差异

以下是当前源码与 `API_CONTRACT.md` 目标契约之间需要后续统一的地方：

- 创建任务 VO 当前返回 `errorMessage`，前端类型期望 `message`；前端已有默认成功文案，因此主流程不阻塞。
- 取消任务 Controller 当前返回 `data: null`，前端类型和契约写作 `boolean`；前端当前不读取该值。

调整这些行为时，应同时修改 Java VO/Controller、前端类型、契约示例和测试。

## 常见问题

### 后端启动时报数据库连接失败

确认 Docker 容器已启动、JDBC 端口是宿主机 `3307`，并检查本地 `application-dev.yaml`。

### RabbitMQ 属性绑定为空

检查 `retina.mq.analysis-*` 六个配置项是否齐全。它们没有代码内默认队列名。

### 队列有消息但 consumerCount 为 0

检查后端日志、listener auto-startup、RabbitMQ 凭据和队列名。消费者在 Java 后端中，不在 Python 服务中。

### 任务进入死信队列

先查看任务 `errorMessage` 和日志，再检查 Python `/health`、模型文件、原图路径和 AI 超时。恢复原因后通过业务重试接口重新创建队列消息，不要直接把死信消息回灌到主队列。

### 分割结果图 404

确认 `analysis_result.mask_object_key` 与 `retina.upload.result-root` 下的真实文件一致，并保证启动工作目录没有变化导致相对路径指向不同位置。

## 安全与数据

- `application-dev.yaml`、`uploads/` 和构建产物已忽略，不要强制提交。
- 患者数据、原图和推理结果属于敏感医疗数据，不得进入日志、测试夹具或公共仓库。
- 本地示例密码和 JWT secret 不得用于生产。
- AI 结果仅供辅助分析，不替代临床诊断。

## AI 健康状态与运行方式

登录后可访问：

```text
GET /api/system/status
```

接口聚合 Python AI 实时状态、RabbitMQ 队列状态和数据库长期任务指标。AI 或 RabbitMQ 单独不可用时接口仍返回 HTTP 200，并将总体状态标记为 `DEGRADED`；数据库长期统计失败时由后端返回服务错误，不伪造健康数据。

Java 正式推理使用 5 秒连接超时和 5 分钟读取超时；健康探测独立使用 2 秒连接超时和 3 秒读取超时。每次任务尝试生成 `requestId`，同一个 ID 会传递给 Python 推理和结果图下载请求。

本机运行 AI 时保持：

```yaml
retina:
  ai:
    base-url: http://127.0.0.1:8000
```

如果 Java 也运行在同一 Docker 网络中，通过环境变量覆盖：

```text
RETINA_AI_BASE_URL=http://retinavision-ai:8000
```

AI 的 Windows Conda 启停脚本和 Docker 说明见同级项目 `../retinavision-ai/README.md`。
# 核心医疗闭环配置

管理员可以访问不包含患者明细的聚合统计看板，但不能访问病例、图像、分析结果、审核和报告等临床明细接口。修正 mask 可上传 PNG、JPEG 或 TIFF；后端校验解码结果、像素数量和尺寸后，统一二值化保存为 PNG。

数据库升级统一由 Flyway 自动执行。部署前备份数据库；不要重复手工执行旧的 `docker/mysql/migration` 脚本。

PDF 报告由 Java 服务生成。Windows 默认读取 `C:/Windows/Fonts/simhei.ttf`，其他环境必须配置具有合法使用授权的中文 TrueType 字体：

```yaml
retina:
  report:
    font-path: /opt/retinavision/fonts/your-authorized-font.ttf
```

服务不会把字体文件提交到仓库。签发前要求医生身份、`APPROVED` 审核结果以及完整的医生所见、审核结论和处理建议；签发记录保存 PDF SHA-256、医生身份快照和版本。
