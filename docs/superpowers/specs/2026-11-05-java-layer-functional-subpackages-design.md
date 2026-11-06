# RetinaVision Java 技术目录内按功能分类设计

日期：2026-11-05

## 1. 背景与目标

RetinaVision Java 后端目前主要按照 `controller`、`service`、`mapper`、`pojo` 等技术职责组织。随着系统功能增加，这些目录中的类数量持续增长，其中 `service`、`service.impl` 和 `pojo` 已经难以快速查找。

本次重构保留开发者熟悉的技术顶层目录，在每个技术目录内部增加功能子目录。开发者既可以先按技术职责定位，也可以在目录内部按 Agent、分析任务、临床流程、知识库等功能继续缩小范围。

目标结构属于“分层目录 + 功能子分类”，不是严格领域模块化，也不改变 Spring Boot 单体应用的部署方式。

## 2. 已确认的选择

- 保留 `controller`、`service`、`mapper`、`pojo`、`enumeration` 等技术顶层目录。
- 每个技术目录内部按照相同功能名称建立子目录。
- `service` 接口与实现放在同一功能目录中，实现类进入该功能的 `impl`。
- 其他现有技术目录采用相同分类方式。
- 当前 `analysis/application/domain/infrastructure` 不作为特殊结构保留，拆回对应技术目录的 `analysis` 子目录。
- 只移动包、更新 import 和完整类名引用，保持现有类名与业务逻辑。

## 3. 目标与非目标

### 3.1 目标

1. 降低大型技术目录的单层文件数量。
2. 让相同功能在不同技术目录下使用一致的子目录名称。
3. 保持现有 Controller、Service、Mapper、DTO、VO、Entity 的职责和类名。
4. 分批迁移，每批均可编译、测试和独立提交。
5. 通过架构测试阻止新类重新堆入技术目录根部。

### 3.2 非目标

- 不建立严格 DDD 模块边界。
- 不引入 `domain/application/infrastructure` 分层。
- 不重写业务、不合并 Service、不拆分类、不重命名类或方法。
- 不改变 HTTP 契约、数据库、SQL、Flyway、RabbitMQ 或序列化字段。
- 不消除现有跨功能调用和 Mapper 依赖。
- 不拆分 Maven 模块或微服务。
- 不修改 Vue 前端和 Python AI 服务。

## 4. 考虑过的方案

### 4.1 完整按功能顶层模块组织

将 `agent`、`analysis`、`clinical` 等作为最高层目录，再在内部放 Controller、Service 和 Mapper。目录边界更强，但需要移动几乎所有 Java 文件并处理更多模块归属问题，预计 6–10 小时。

### 4.2 只整理 Service

只在 `service` 内增加功能子目录，预计 1.5–3 小时。它能解决当前最明显的拥挤问题，但 Controller、Mapper 和 POJO 仍然难以按功能查找。

### 4.3 技术目录内按功能分类（已选择）

保留熟悉的技术顶层目录，并在内部统一增加功能子目录。它不追求严格模块隔离，但能够直接改善 IntelliJ 项目树的查找体验，预计 4–6 小时。

## 5. 统一功能分类

所有技术目录使用以下功能名称，不为同一含义创造不同目录名：

```text
auth          登录、JWT、登录限流和认证过滤
user          用户、角色、医生目录和后台用户管理
patient       患者档案和患者本人检查流程
cases         病例、病例归属和病例访问
imaging       图像上传、预览、元数据和图像状态
analysis      分析任务、结果、状态机、Outbox、MQ 和推理
clinical      人工反馈、修正、审核和医生队列
reporting     报告草稿、PDF、签发数据和患者解释
agent         在线 Agent、路由、上下文、Skill 和工具调用
knowledge     文档入库、Embedding、Qdrant、RAG 和引用
governance    Prompt/Skill/Agent/RAG 评测、审计和 AI 质控
operations    系统状态、统计、队列和死信恢复
shared        无明确单一功能归属的稳定公共能力
```

`case` 是 Java 关键字，因此统一使用 `cases`。

目录只在实际存在对应类时创建，不建立空目录。

## 6. 目标目录结构

```text
com/example/retinavision/
├── controller/
│   ├── agent/
│   ├── analysis/
│   ├── auth/
│   ├── cases/
│   ├── clinical/
│   ├── governance/
│   ├── imaging/
│   ├── knowledge/
│   ├── operations/
│   ├── patient/
│   └── user/
├── service/
│   ├── agent/impl/
│   ├── analysis/impl/
│   ├── auth/impl/
│   ├── cases/impl/
│   ├── clinical/impl/
│   ├── governance/impl/
│   ├── imaging/impl/
│   ├── knowledge/impl/
│   ├── operations/impl/
│   ├── patient/impl/
│   ├── reporting/impl/
│   └── user/impl/
├── mapper/
│   ├── agent/
│   ├── analysis/
│   ├── cases/
│   ├── clinical/
│   ├── governance/
│   ├── imaging/
│   ├── knowledge/
│   ├── operations/
│   ├── patient/
│   ├── reporting/
│   └── user/
├── pojo/
│   ├── agent/{dto,entity,vo}/
│   ├── analysis/{command,domain,dto,entity,event,vo}/
│   ├── auth/{dto,vo}/
│   ├── cases/{dto,entity,vo}/
│   ├── clinical/{dto,entity,vo}/
│   ├── governance/{dto,entity,vo}/
│   ├── imaging/{entity,vo}/
│   ├── knowledge/{dto,entity,vo}/
│   ├── operations/vo/
│   ├── patient/{dto,entity,vo}/
│   ├── reporting/{dto,entity,vo}/
│   └── user/{dto,entity,vo}/
├── enumeration/{analysis|cases|clinical|imaging|knowledge|patient|reporting|user}/
├── config/{analysis|auth|clinical|governance|shared}/
├── mq/analysis/
├── ai/analysis/
├── llm/{agent|governance|knowledge|reporting|shared}/
├── rag/{governance|knowledge}/
├── filter/auth/
├── redis/{analysis|auth|shared}/
└── utils/{auth|shared}/
```

该树用于说明统一形式，不是封闭枚举。第 5 节的功能分类是权威清单：只要某个技术目录中存在对应功能类，就创建相同名称的功能子目录，例如 `mapper/cases`、`mapper/reporting`；不存在类时不创建空目录。

现有 `result` 和 `exception` 规模较小且属于全局 HTTP 基础能力，可以保留顶层，不强行为它们增加只有一层的 `shared` 子目录。`RetinaVisionApplication` 继续位于根包。

## 7. Service 组织规则

接口和实现按功能靠近放置：

```text
service/agent/AgentChatService.java
service/agent/impl/AgentChatServiceImpl.java

service/analysis/AnalysisTaskExecutionService.java
service/analysis/impl/AnalysisTaskExecutionServiceImpl.java
```

规则：

1. 保持接口名和实现类名不变。
2. 保持 `@Service`、`@Transactional` 和默认 Bean 名不变。
3. 不把接口和实现合并到一个类。
4. 不因为跨包访问而扩大方法可见性；有包私有协作时，将内聚类一起移动。
5. `ReportPdfDocument` 等当前位于 Service 目录但不是 Service 的类，本轮仍按报告功能移动，不顺手重新设计类型职责。

## 8. POJO 组织规则

POJO 先按功能分类，再按类型分类。新包名统一使用小写：

```text
pojo/agent/dto
pojo/agent/entity
pojo/agent/vo
```

每个类根据主要业务所有者归属：

- `CurrentUserVO` 归 `auth/vo`。
- `CaseEntity` 和病例查询模型归 `cases`。
- `ImageFileEntity` 和质量摘要归 `imaging`。
- 任务、结果、日志和 Outbox 模型归 `analysis`。
- 反馈、修正和审核模型归 `clinical`。
- 报告及签发响应归 `reporting`。
- Agent 会话、上下文和运行时 Skill 模型归 `agent`。
- 评测运行、Prompt 版本、审计和质控模型归 `governance`。

若同一 POJO 被多个功能使用，它仍放在主要所有者目录，其他功能通过 import 使用；不得为了减少 import 将业务对象塞入 `shared`。

## 9. `analysis` 的迁移方式

现有 `analysis/application/domain/infrastructure` 拆入全局技术目录：

| 当前类别 | 目标位置 |
|---|---|
| 执行用例、Handler、端口接口 | `service/analysis` 或 `service/analysis/port` |
| 状态机、状态、任务类型、转换 | `pojo/analysis/domain` |
| Command、事件和执行结果 | `pojo/analysis/command|event|dto` |
| Task/Result Service 与实现 | `service/analysis|service/analysis/impl` |
| Task/Result/Outbox Mapper | `mapper/analysis` |
| Task/Result/Log/Outbox Entity | `pojo/analysis/entity` |
| MQ Listener、Publisher、Message Adapter | `mq/analysis` |
| Python AI HTTP Client/Gateway | `ai/analysis` |
| 文件和产物存储实现 | `service/analysis/storage` |
| Analysis 配置和属性 | `config/analysis` |

状态机和端口的类名、构造方法、实现和测试保持不变。原 ArchUnit 测试改为新路径，但继续保护分析模型不依赖 Spring/MyBatis/MQ/文件系统，以及服务端口不依赖具体适配实现。

## 10. Agent、知识与治理边界

- 在线 Agent 聊天、Router、上下文、Native Skill、Tool Calling 和医生/患者查询归 `agent`。
- 文档、Embedding、Qdrant、RAG 检索、受众过滤和引用验证归 `knowledge`。
- Agent 评测、Prompt/RAG 评测、Skill 管理、调用审计和质量控制归 `governance`。
- 通用 LLM Client、Properties 和编排基础类进入 `llm/shared`；明确只服务 Agent、知识、报告或治理的类进入对应子目录。
- 当前 `agent/evaluation` 移入治理相关技术目录，例如 Service 进入 `service/governance`、模型进入 `pojo/governance`，不保留独立顶层 `agent/evaluation`。
- 当前顶层 `com.example.retinavision.agent` 必须完整拆除：Service/编排进入 `service/agent`，请求响应和上下文模型进入 `pojo/agent`，模型网关进入 `llm/agent`，安全策略进入适合的 `service/agent/policy`，评测代码进入治理分类。完成后不保留另一套顶层 Agent 组织方式。

本轮只改变位置，不重新设计这些功能之间的调用关系。

## 11. Spring、MyBatis 与资源兼容

### 11.1 Spring

- 根启动类位置不变，组件扫描继续覆盖所有子包。
- 默认 Bean 名由类名产生，类名不变，因此 Bean 名不变。
- 更新测试中的 import 和显式扫描路径。
- 不修改 Security matcher、Filter 顺序、事务注解或条件配置。

### 11.2 MyBatis

- `@MapperScan` 改为扫描 `com.example.retinavision.mapper`，该根目录下只包含 Mapper 子包，可以安全递归扫描。
- Mapper XML 文件继续保留在 `src/main/resources/mapper/`，不按功能搬动资源文件。
- 更新 XML 的 `namespace`、`resultType`、`parameterType` 和嵌套 Mapper 类型完整类名。
- 不修改 SQL 文本、列、动态条件、排序和分页。

### 11.3 反射与序列化

- 全仓搜索完整类名字符串、`Class.forName`、Jackson 类型元数据和 Redis Java 类型信息。
- 当前按“没有外部 Java API 消费者，也没有业务数据主动持久化完整类名”规划。
- 若实际发现历史数据依赖旧包名，暂停对应类型迁移并补充兼容读取设计，不清空数据或伪造兼容。

## 12. 分批顺序

### 批次 1：基线与规则

- 运行当前 `mvn test`。
- 建立允许迁移期共存的 ArchUnit 目录规则。
- 记录当前未提交修改，避免覆盖用户文件。
- 若完整测试因真实数据库、外部服务或与本次迁移无关的既有问题失败，保存测试名、异常和数量作为基线；本次不顺手修复旧问题，但后续每批不得新增失败。能够不依赖外部环境运行的针对性测试必须通过。

### 批次 2：Service

- 先整理 `service` 和 `service.impl`，直接解决最拥挤目录。
- 每个功能完成后修复 import 和对应测试。

### 批次 3：Controller 与安全入口

- 整理 `controller`、`filter` 和认证相关 `utils`。
- 验证 HTTP 路径、权限和过滤链不变。

### 批次 4：Mapper 与 POJO

- 整理 Mapper、DTO、Entity、VO 和枚举。
- 同步修改 Mapper XML 完整类名。

### 批次 5：Analysis 技术适配

- 拆除现有 `analysis/application/domain/infrastructure`。
- 整理 `mq`、`ai`、analysis 配置和存储适配。
- 验证状态机、Outbox、MQ、AI 和幂等行为。

### 批次 6：LLM、RAG、配置与剩余目录

- 分类 `llm`、`rag`、`config`、`redis`、`constant` 和 `common`。
- 保持 Prompt、Agent、知识和报告行为不变。

### 批次 7：测试镜像与收尾

- 测试包按照生产代码的新功能子目录调整。
- 清理空目录，收紧 ArchUnit，更新 README 与后端文档。
- 运行完整验证。

每批单独提交。迁移期间项目必须保持可编译，不允许积累多个批次后才统一修复。

## 13. 验证要求

每批至少执行：

```powershell
mvn test
```

若批次 1 已确认存在非本次迁移造成的基线失败，则每批验收采用“受影响测试全部通过，完整测试失败集合不增加”的标准，并保存 Surefire 结果对比。不得把新增失败解释为基线问题。

最终执行：

```powershell
mvn test
mvn clean package
powershell -ExecutionPolicy Bypass -File .\scripts\verify-repository.ps1
```

额外检查：

- 搜索旧包声明和旧完整类名。
- Spring 上下文和全部 Mapper 正常加载。
- 无重复 Bean、重复 Mapper 或 Unknown statement。
- Agent 多角色、安全策略和对象级权限测试通过。
- Analysis 状态机、Outbox、MQ、重试和死信测试通过。
- RAG 引用、评测门槛和报告可见性测试通过。

## 14. 风险与控制

### 14.1 工程量仍大于只整理 Service

Controller、Mapper 和 POJO 同样需要更新 import，Mapper XML 还需要完整类名修改。通过先完成 Service、每批独立提交来保证中途即可获得可见收益。

### 14.2 MyBatis XML 遗漏

使用全仓旧包名搜索和 Spring/Mapper 测试验证，不仅依赖编译器。

### 14.3 包私有访问失效

内聚类一起移动；不得为了编译通过随意改成 `public`。

### 14.4 Analysis 规则退化

目录形式改变不代表取消状态机和端口约束；ArchUnit 按新路径保留原有行为保护。

### 14.5 误执行旧计划

原 `2026-11-05-java-package-by-feature-reorganization-design.md` 和对应计划明确标记为已替代。实施只能使用本设计后续生成的新计划。

## 15. 完成标准

1. 主要技术目录均按统一功能名称分类。
2. `service` 根目录和单一 `service.impl` 不再堆放全部业务类。
3. Controller、Mapper、POJO、枚举及其他技术目录采用相同功能分类。
4. `analysis/application/domain/infrastructure` 已迁移到统一技术目录。
5. 类名、业务逻辑、接口契约、SQL、表结构和消息语义保持不变。
6. Mapper XML、Spring 扫描和测试引用全部更新。
7. ArchUnit 阻止新类再次放入应当分类的技术目录根部。
8. 完整测试与构建通过；若批次 1 记录了外部环境导致的既有失败，则至少保证受影响测试全部通过且失败集合不增加，并单独报告环境验证结果。
9. 每一批都有独立、可审查的提交。


