# Java 分层包结构重构设计

## 1. 背景与意图

RetinaVision 后端已经按 `agent`、`analysis`、`auth`、`cases` 等业务域拆分，但 `service` 目录目前同时容纳 Service 接口、Service 实现、用例、编排器、Parser、Registry、Factory、Policy、Port、Adapter、Storage 和数据对象。`service` 下共有约 130 个 Java 文件，目录名称无法准确表达文件职责，阅读者必须逐个打开类才能判断用途。

本次重构以“按目录快速找到代码”为第一目标，采用职责分层方式重新组织 Java 包：

- `service` 只保存面向 Controller 或其他业务模块的业务 Service 接口与实现；
- `application` 保存用例、Handler、Orchestrator 和应用层辅助组件；
- `port` 保存核心流程依赖的外部能力接口；
- `infrastructure` 保存 Port 的 MyBatis、HTTP、Redis、文件等技术实现；
- `exception` 集中保存全部异常类型，并按领域分组。

## 2. 目标

1. 打开目录即可判断文件是接口、实现、编排、外部端口还是技术实现。
2. Service 接口与实现类物理分离。
3. 将当前错误放在 `service` 下的 UseCase、Handler、Port、Adapter、Storage 移出。
4. 将散落在 `ai`、`llm`、`pojo`、`service` 中的异常类型集中到 `exception`。
5. 保持现有类名、方法签名、Spring Bean 名称、HTTP 契约、序列化数据、数据库结构和业务行为不变；内部 Java 完全限定类名（FQCN）会随包移动而改变。
6. 使用架构测试防止后续再次把非 Service 类型放回 `service`。

## 3. 非目标

- 不重命名已有业务类。
- 不为了目录整齐给每个类机械新增接口。
- 不修改领域模型、数据库表、Flyway 迁移或 Mapper SQL 行为。
- 不改变 Agent 路由、分析任务、权限、安全或评测逻辑。
- 不在本轮拆分 Maven 多模块或微服务。
- 不把所有现有技术包一次性改造成完整 DDD 目录；只迁移与本次职责边界直接相关的类。

## 4. 采用方案

### 4.1 已选择：职责分层整理

目标顶层结构：

```text
com.example.retinavision
├── application/       用例、Handler、Orchestrator、应用层协作组件
├── service/           业务 Service API 与实现
├── port/              应用核心依赖的外部能力接口
├── infrastructure/    Port 的具体技术实现
├── exception/         集中异常与全局异常处理器
├── controller/        HTTP 接口
├── pojo/              Entity、DTO、VO、Command、领域数据
├── mapper/            暂时保留现有 MyBatis Mapper 根目录
├── llm/               暂时保留 LLM 客户端与通用能力
├── rag/               暂时保留 RAG 能力
├── mq/                暂时保留消息入口和 Outbox
└── config/            Spring 配置
```

`mapper`、`llm`、`rag`、`mq` 等现有技术根目录本轮不整体搬入 `infrastructure`。原因是它们包含大量独立配置、扫描和测试约束，一次性迁移会扩大风险，却不能直接解决当前 `service` 难以查找的问题。后续可单独进行第二阶段技术基础设施归并。

### 4.2 未采用方案

#### 仅在 `service` 内增加更多子目录

优点是改动小；缺点是 `port`、`adapter`、`storage`、`usecase` 仍然错误地被称为 Service，无法真正解决命名与职责不一致。

#### 完整 DDD/六边形模块化改造

每个业务域独立包含 `domain/application/infrastructure/interfaces`。结构最严格，但会同时迁移实体、Mapper、Controller、配置和跨域依赖，远超本次“容易查找”的目标。

## 5. 目标目录规则

### 5.1 Service

每个领域统一为：

```text
service/<domain>/
├── api/      业务 Service 接口
└── impl/     对应实现类
```

示例：

```text
service/analysis/api/TaskService.java
service/analysis/api/AnalysisResultService.java
service/analysis/impl/TaskServiceImpl.java
service/analysis/impl/AnalysisResultServiceImpl.java
```

规则：

- `api` 只允许 `interface`；
- `impl` 只允许接口实现或明确的业务 Service 实现；
- `service/<domain>` 根目录不直接放 Java 类型；
- Parser、Factory、Registry、Orchestrator、Policy、Handler、record 不进入 `service`。

### 5.2 Application

```text
application/<domain>/
├── usecase/          用例接口
├── handler/          用例实现与 Handler
├── orchestration/    跨 Service 编排
├── routing/          路由和解析
├── context/          会话/查询上下文
├── tooling/          Agent 工具注册与工厂
├── presentation/     应用结果到展示结果的转换
├── policy/           应用层确定性策略
└── evaluation/       评测执行、Fixture 和评分编排
```

只创建实际需要的子目录，不要求每个领域拥有完整目录集合。

Controller 可以依赖 `service/<domain>/api`，也可以直接依赖明确的 `application/<domain>/usecase`。后者适用于本身就是应用用例、当前没有业务 Service 接口的具体类，例如 `AgentEvaluationService` 和 `AgentEvaluationAdministrationService`；它们移动到 `application/governance/evaluation/usecase`，不为满足目录形式机械新增空洞接口。

典型迁移：

```text
service/analysis/ExecuteAnalysisTaskUseCase
    -> application/analysis/usecase/ExecuteAnalysisTaskUseCase

service/analysis/ExecuteAnalysisTaskHandler
    -> application/analysis/handler/ExecuteAnalysisTaskHandler

service/agent/DoctorAgentSkillOrchestrator
    -> application/agent/orchestration/DoctorAgentSkillOrchestrator

service/agent/AgentContextCommandParser
    -> application/agent/context/AgentContextCommandParser

service/agent/AgentSkillRegistry
    -> application/agent/routing/AgentSkillRegistry

service/agent/DefaultAgentToolFactory
    -> application/agent/tooling/DefaultAgentToolFactory
```

Agent 中依赖具体外部技术的实现进入 Infrastructure：

```text
llm/agent/LlmAgentSkillRouter
    -> infrastructure/agent/routing/LlmAgentSkillRouter

service/agent/PersistentAgentQueryContextService
    -> infrastructure/agent/context/PersistentAgentQueryContextService

service/agent/PersistentPatientAgentQueryContextService
    -> infrastructure/agent/context/PersistentPatientAgentQueryContextService
```

对应接口 `AgentSkillRouter`、`AgentQueryContextService`、`PatientAgentQueryContextService` 分别进入 `application/agent/routing/api` 和 `application/agent/context/api`。这样 Application 只声明所需能力，LLM 与 MyBatis 持久化实现位于 Infrastructure。

### 5.3 Port

```text
port/<domain>/
```

Port 是应用核心调用外部技术时依赖的接口，不是 Controller 使用的业务 Service API。

分析域迁移：

```text
service/analysis/port/*
    -> port/analysis/*
```

包括 `AiInferencePort`、`AnalysisTaskRepository`、`ArtifactStore`、`AnalysisResultStore`、`TaskAuditLog` 等。

### 5.4 Infrastructure

```text
infrastructure/<domain>/
├── persistence/      MyBatis/数据库适配器
├── inference/        AI/HTTP 适配器
├── storage/          本地文件或对象存储实现
└── integration/      其他外部系统适配器
```

本轮首先迁移当前已经位于 `service/analysis/adapter` 和 `service/analysis/storage` 的实现：

```text
service/analysis/adapter/LegacyAnalysisTaskRepository
    -> infrastructure/analysis/persistence/LegacyAnalysisTaskRepository

service/analysis/adapter/MyBatisAnalysisResultStore
    -> infrastructure/analysis/persistence/MyBatisAnalysisResultStore

service/analysis/storage/LocalArtifactStore
    -> infrastructure/analysis/storage/LocalArtifactStore
```

当前位于 `ai/analysis` 的 AI 客户端暂时保留原位，只更新其 Port import。后续技术基础设施归并时再整体迁移，避免同一功能的一半被移动、一半被遗漏。

例外是 `HttpAiInferenceGateway`：它直接实现 `AiInferencePort` 并依赖 `LocalArtifactStore`，因此它本身就是分析域的基础设施适配器，必须移动到 `infrastructure/analysis/inference`。底层 `AiInferenceClient`、HTTP DTO 和通用客户端继续保留在 `ai/analysis`，由 Gateway 适配到 Port。

### 5.5 数据契约

接口暴露的数据对象不放在 `service`：

```text
AiQuotaReservation
    -> pojo/analysis/dto/AiQuotaReservation

ReportPdfDocument
    -> port/reporting/model/ReportPdfDocument
```

规则：

- Service API 使用的 Command、DTO、VO、record 进入 `pojo/<domain>` 对应子目录；
- 只服务于某个 Port 的数据契约可进入 `port/<domain>/model`；
- `port` 允许接口和必要的 Port 数据契约，但不允许具体技术实现。

### 5.6 Exception

所有异常类型集中到：

```text
exception/
├── shared/
├── auth/
├── agent/
├── analysis/
├── evaluation/
├── llm/
└── handler/
```

目标示例：

```text
exception/shared/BaseException
exception/shared/ConflictException
exception/shared/RateLimitException
exception/auth/AccountNotFoundException
exception/agent/AgentUnsafeRequestException
exception/analysis/AiInferenceException
exception/analysis/IllegalTaskTransitionException
exception/evaluation/AgentEvaluationInfrastructureException
exception/llm/LlmException
exception/handler/GlobalExceptionHandler
```

`AiFailureCategory` 和 `AgentEvaluationFailureType` 是分类枚举，不是异常类型，不因名称包含 Failure 而迁移到 `exception`。

`IllegalTaskTransitionException` 移动后仍接收 `AnalysisTaskStatus`。这会形成 `pojo.analysis.domain -> exception.analysis -> pojo.analysis.domain` 的包级双向引用。本轮接受这一小范围依赖，因为用户明确要求异常集中且不允许修改构造器契约；架构测试不把 `exception` 定义为低层无依赖模块。后续若重构领域错误模型，可通过错误码或独立值对象解除该引用。

## 6. 领域迁移范围

### 6.1 Analysis

- Service 接口进入 `service/analysis/api`；
- Service 实现保留在 `service/analysis/impl`；
- UseCase/Handler 进入 `application/analysis`；
- Port 进入 `port/analysis`；
- Adapter/Storage 进入 `infrastructure/analysis`；
- 分析异常进入 `exception/analysis`。

Analysis 完整迁移表：

```text
service/analysis/ExecuteAnalysisTaskUseCase
    -> application/analysis/usecase/ExecuteAnalysisTaskUseCase
service/analysis/ExecuteAnalysisTaskHandler
    -> application/analysis/handler/ExecuteAnalysisTaskHandler
service/analysis/AiQuotaReservation
    -> pojo/analysis/dto/AiQuotaReservation

service/analysis/port/AiInferencePort
    -> port/analysis/AiInferencePort
service/analysis/port/AnalysisResultStore
    -> port/analysis/AnalysisResultStore
service/analysis/port/AnalysisTaskEventOutbox
    -> port/analysis/AnalysisTaskEventOutbox
service/analysis/port/AnalysisTaskRepository
    -> port/analysis/AnalysisTaskRepository
service/analysis/port/ArtifactStore
    -> port/analysis/ArtifactStore
service/analysis/port/ImageQualityProjectionPort
    -> port/analysis/ImageQualityProjectionPort
service/analysis/port/ReportDraftPort
    -> port/analysis/ReportDraftPort
service/analysis/port/SourceImageReader
    -> port/analysis/SourceImageReader
service/analysis/port/TaskAuditLog
    -> port/analysis/TaskAuditLog

service/analysis/adapter/LegacyAnalysisTaskRepository
    -> infrastructure/analysis/persistence/LegacyAnalysisTaskRepository
service/analysis/adapter/LegacyReportDraftGateway
    -> infrastructure/analysis/persistence/LegacyReportDraftGateway
service/analysis/adapter/MyBatisAnalysisResultStore
    -> infrastructure/analysis/persistence/MyBatisAnalysisResultStore
service/analysis/adapter/MyBatisImageQualityProjection
    -> infrastructure/analysis/persistence/MyBatisImageQualityProjection
service/analysis/adapter/MyBatisSourceImageReader
    -> infrastructure/analysis/persistence/MyBatisSourceImageReader
service/analysis/adapter/MyBatisTaskAuditLog
    -> infrastructure/analysis/persistence/MyBatisTaskAuditLog
service/analysis/storage/LocalArtifactStore
    -> infrastructure/analysis/storage/LocalArtifactStore
ai/analysis/HttpAiInferenceGateway
    -> infrastructure/analysis/inference/HttpAiInferenceGateway
```

### 6.2 Agent

- 真正的业务 Service 接口进入 `service/agent/api`；
- 对应实现进入 `service/agent/impl`；
- Orchestrator、Parser、Registry、Factory、上下文、Presenter、Policy 移入 `application/agent` 对应职责目录；
- 内部策略接口（如 `AgentSkillRouter`、`AgentToolFactory`）进入 Application 对应职责目录的 `api`；纯应用实现进入 `impl`，依赖 LLM、Mapper 等具体技术的实现进入 `infrastructure/agent`；
- Agent 异常进入 `exception/agent`。

### 6.3 Governance Evaluation

- 已有接口的治理 Service 进入 `service/governance/api`；
- Service 实现进入 `service/governance/impl`；
- `AgentEvaluationService`、`AgentEvaluationAdministrationService` 是 Controller 直接调用的具体应用用例，进入 `application/governance/evaluation/usecase`；
- Runner、Scorer、Fixture、CaseExecutor、Eligibility 等评测编排组件进入 `application/governance/evaluation` 对应职责目录；
- 评测基础设施异常进入 `exception/evaluation`。

### 6.4 其余 Service 领域

`auth`、`cases`、`clinical`、`imaging`、`knowledge`、`operations`、`patient`、`reporting`、`user` 统一执行接口到 `api`、实现到 `impl` 的迁移。

不属于接口实现的类需要按职责移出：

- `SecurePatientNumberGenerator` -> `application/patient/support`；
- `ReportPdfDocument` -> `port/reporting/model`；
- `ReportPdfRenderer` -> `port/reporting`；
- `JdkReportPdfRenderer` -> `infrastructure/reporting/pdf`。

## 7. 依赖方向

允许的主要方向：

```text
controller
    -> service/api
    -> application/usecase

service/impl
    -> service/api
    -> application
    -> port
    -> mapper/pojo（现阶段兼容）

application
    -> service/api
    -> port
    -> pojo/domain

infrastructure
    -> port
    -> mapper/ai/redis/文件 API
```

禁止：

- `port` 依赖 `infrastructure`；
- `application` 直接依赖 `infrastructure`；
- `service/api` 依赖 `service/impl`；
- Controller 直接依赖 Service 实现类；
- Controller 可以依赖明确的 application use case，但不得依赖 application 内部 Handler、Fixture 或 support；
- 新异常类型散落在 `service`、`pojo`、`llm`、`ai`。

## 8. Spring 与框架兼容

1. 应用主类位于 `com.example.retinavision`，移动后的包仍在默认组件扫描范围内。
2. `@Service`、`@Component`、`@Configuration` 注解保持不变，Bean 名称默认由类名产生，因此包移动不改变 Bean 名称。
3. 手工 `new` 出的 Handler、Port Adapter 和 Storage 由配置类继续构造，只更新 import。
4. MyBatis Mapper 本轮不移动，因此 Mapper 扫描和 XML namespace 不变化。
5. Jackson、Flyway、Spring Security、RabbitMQ 路由和配置键不变化。

## 9. 测试与防回归

### 9.1 每批验证

- 编译主代码；
- 执行受影响领域单元测试；
- 检查旧包名无残留；
- 检查 Git diff 仅包含文件移动、package/import 和架构测试调整。

### 9.2 全量验证

```text
mvn test
powershell -ExecutionPolicy Bypass -File ./scripts/verify-repository.ps1
powershell -ExecutionPolicy Bypass -File ./scripts/verify-monorepo-layout.ps1
```

### 9.3 架构规则

更新或新增 ArchUnit 约束。首先扩充顶层包白名单，允许 `application`、`port` 和 `infrastructure`；限制性规则按每个业务切片迁移完成后逐条启用，不在第一批一次性要求尚未迁移的旧包满足最终结构：

- `service..api` 只能包含接口；
- `service..<domain>` 根目录不得直接包含类型；
- `application` 不依赖 `infrastructure`；
- `port` 只能包含接口或必要的 Port 数据契约；
- 异常子类必须位于 `exception..`；
- `infrastructure` 可以依赖 `port`，反向依赖禁止。

## 10. 分批顺序

1. **基础护栏与异常**：扩充允许的顶层包，建立只针对已迁移代码的规则，集中异常类型。
2. **Analysis 垂直切片**：迁移 UseCase、Handler、Port、Adapter、Storage 和 Service API。
3. **Agent 垂直切片**：迁移 Service API、编排、上下文、路由、工具和策略。
4. **Governance/Evaluation**：迁移评测编排和治理 Service。
5. **其余 Service 域**：统一 `api/impl`，迁移少量非 Service 类型。
6. **收尾**：清理空目录、旧 import、文档和架构指南，执行全量测试。

每一批都保持可编译、可测试，避免一次移动 130 个文件后再集中修错。

生产类移动时，对应测试类同步移动到同样的职责包。这样测试导航与生产代码一致，也避免未来 package-private 测试访问产生隐式阻塞。

## 11. 风险与控制

### 11.1 大量 import 变化

通过 IDE/编译器和 `rg` 检查旧包名；不在同一批修改业务逻辑。

### 11.2 Spring Bean 丢失

保持注解和类名，使用上下文加载测试验证 Bean 装配。

### 11.3 包可见性

迁移前检查 package-private 类、构造器和方法；必要时将协作类放入同一职责包，不随意扩大 `public`。

### 11.4 与未提交修改冲突

实施在独立 worktree 和独立分支进行；最终通过 PR 合并，不覆盖主工作区现有修改。

## 12. 完成标准

- `service` 下每个领域只存在 `api`、`impl` 及必要的 `package-info.java`；
- `service` 不再包含 Port、Adapter、Storage、UseCase、Handler、Parser、Registry、Factory、Policy 或 record；
- 所有异常类型位于 `exception` 的领域子包；
- Analysis 和 Agent 主流程行为及公开接口不变；
- 现有测试全部通过；
- 架构测试能够阻止旧结构重新出现；
- `PROJECT_ARCHITECTURE_GUIDE.md` 与最终包结构一致。
