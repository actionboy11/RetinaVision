# RetinaVision Java 按功能目录重构设计

> **状态：已被替代。** 2026-11-05 后续讨论改为“保留技术顶层目录、目录内部按功能分类”。请以 `2026-11-05-java-layer-functional-subpackages-design.md` 为准。

日期：2026-11-05

## 1. 背景与意图

RetinaVision Java 后端随着病例、图像、异步分析、临床复核、报告、Agent、知识库和 AI 治理能力持续增加，当前大部分代码仍按 `controller`、`service`、`mapper`、`pojo` 等技术类型集中存放。开发者查找一个功能时，需要在多个大型目录之间来回切换，`service`、`service.impl` 和 `pojo` 尤其拥挤。

本次重构的首要目标是让目录容易查找：开发者先进入业务功能模块，再在模块内部按照熟悉的 Controller、Service、Mapper、Model 等目录定位代码。Java 后端仍然是一个 Spring Boot 单体应用、一个 Maven 模块和一个部署单元。

## 2. 已确认的选择

- 采用按功能模块分批迁移，不进行一次性整体搬迁。
- 第一轮只移动 Java 包并更新引用，保持现有类名。
- 不合并重复 Service，不重命名类、方法、字段或数据库对象。
- 所有模块使用统一的“功能模块 + 技术子目录”结构。
- 已有 `analysis/application/domain/infrastructure` 也迁移到统一结构，不作为例外保留。
- 复杂模块可以增加 `mq`、`client`、`storage`、`config`、`policy` 等确有职责的子目录。

## 3. 目标与非目标

### 3.1 目标

1. 看到功能名称即可定位相关 Controller、Service、Mapper 和数据模型。
2. 将目前全局的 `controller/service/mapper/pojo/enumeration` 大目录拆回各自业务模块。
3. 每一批迁移后均可编译、测试和独立提交。
4. 保持浏览器 API、Java 到 Python API、RabbitMQ、数据库和序列化行为兼容。
5. 使用架构测试防止新代码重新堆回全局技术目录。

### 3.2 非目标

- 不新增业务功能。
- 不改变 HTTP 路径、请求字段、响应字段、枚举值或错误码。
- 不改变数据库表、Flyway 迁移、MyBatis SQL 或索引。
- 不改变 RabbitMQ exchange、queue、routing key、消息结构和 ACK 语义。
- 不改变 Agent 路由、Skill 执行、评测或安全策略。
- 不改变类名、方法签名、Bean 名称或现有业务流程。
- 不在本轮清理重复接口、拆分类、统一命名或引入新的框架。
- 不拆分 Maven 模块、微服务或独立数据库。
- 不重构 Vue 前端和 Python AI 服务。

## 4. 考虑过的实施方式

### 4.1 一次性整体迁移

一次移动所有 Java 类，可以最快得到最终目录，但会产生超大 diff，难以逐批验证，也会提高 import、Mapper XML、反射字符串和测试遗漏的风险。

### 4.2 按功能模块分批迁移（已选择）

每次迁移一个相对完整的功能模块，修正该模块及调用方引用，运行针对性测试和完整 `mvn test`，然后单独提交。迁移期间新旧包会短暂共存，但每一批都保持项目可编译。

选择原因：它最符合“目录容易查找”的目标，同时便于定位回归、审查改动和控制现有工作树风险。

### 4.3 只拆分 Service

只解决 `service` 目录过长，改动较小，但 Controller、Mapper、DTO、VO 和 Entity 仍然分散，不能真正改善按功能查找体验，因此不采用。

## 5. 顶层功能模块

目标根包保持：

```text
com.example.retinavision
```

其下按功能组织为：

```text
com/example/retinavision/
├── auth/          # 登录、注册、JWT、黑名单、登录限流、安全过滤
├── user/          # 用户管理、角色、医生目录和职业身份
├── patient/       # 患者档案、患者入口和患者本人检查流程
├── cases/         # 病例主体、病例查询、病例归属与访问校验
├── imaging/       # 图像上传、预览、元数据和本地文件处理
├── analysis/      # 质量检测、血管分割、任务、结果、Outbox 和推理调用
├── clinical/      # 人工修正、医生审核、临床队列和提醒
├── reporting/     # 报告草稿、签发、PDF 和患者报告解释
├── agent/         # 多角色 Agent、路由、上下文、Native Skill 和工具调用
├── knowledge/     # 文档入库、Embedding、Qdrant、RAG 和引用校验
├── governance/    # Prompt/Skill/Agent/RAG 评测、调用审计和 AI 质控
├── operations/    # 系统状态、统计、死信和运维恢复
├── platform/      # 多功能共同使用的技术适配，例如通用 LLM 与 Redis 基础能力
└── shared/        # 统一响应、异常、分页和极少量稳定公共类型
```

`case` 是 Java 关键字，不能作为包名，因此病例模块固定使用 `cases`。

## 6. 模块内部统一结构

模块按实际职责选择下列子目录，不要求创建空目录：

```text
module/
├── controller/       # HTTP 入口
├── service/          # 业务接口
│   ├── impl/         # 现有实现类
│   └── port/         # 现有外部能力接口，仅复杂模块需要
├── mapper/           # MyBatis Mapper 接口
├── model/
│   ├── entity/       # 持久化实体
│   ├── dto/          # 请求或内部传输对象
│   ├── vo/           # 响应视图对象
│   ├── enums/        # 模块枚举
│   └── event/        # 现有事件模型
├── mq/               # Listener、Publisher、Outbox；按需使用
├── client/           # HTTP 或 SDK 客户端；按需使用
├── storage/          # 文件或产物存储；按需使用
├── policy/           # 确定性访问或安全策略；按需使用
└── config/           # 模块专属配置；按需使用
```

Controller、Service 接口和 `impl` 的现有关系保持不变。此次不为了追求目录一致而创造无意义层级，也不将所有类强制塞进四层结构。

## 7. `analysis` 统一后的结构

`analysis` 当前已使用 `application/domain/infrastructure`。本轮将其改为与其他模块一致的查找模式：

```text
analysis/
├── controller/
├── service/
│   ├── impl/
│   └── port/
├── mapper/
├── model/
│   ├── entity/
│   ├── dto/
│   ├── vo/
│   ├── enums/
│   └── event/
├── mq/
├── client/
├── storage/
└── config/
```

现有状态机和领域模型类移动到 `analysis.model` 的合适子目录；执行用例和编排类移动到 `analysis.service`；端口接口移动到 `analysis.service.port`；MyBatis、Outbox、MQ、AI HTTP 和存储实现分别进入 `mapper`、`mq`、`client` 和 `storage`。类名和实现逻辑均保持不变。

原有 ArchUnit 规则不直接删除，而是机械更新为新包路径。对于源码中已经存在的 Controller 直接依赖 Mapper、跨功能 Mapper 访问等历史依赖，本轮采用基线豁免，不为满足新目录而改写调用链。新增结构规则只阻止代码重新写入已淘汰的全局技术目录；更严格的模块依赖治理留给后续独立设计。

## 8. 类归属判断规则

一个类只根据它主要服务的业务能力归属，不根据谁调用它归属：

1. 拥有数据库表或主要业务状态的模块优先拥有 Entity、Mapper 和枚举。
2. 只服务单一功能的 Controller、Service、DTO 和 VO 跟随该功能模块。
3. 被多个模块调用不等于进入 `shared`；仍由语义所有者模块持有，其他模块通过公开 Service 使用。
4. 只有与业务无关、语义稳定且确实跨模块的响应包装、分页和基础异常可进入 `shared`。
5. 通用 LLM、Redis 等技术适配进入 `platform`，但 Prompt、知识检索、Agent 或报告业务类仍留在各自功能模块。
6. 存在归属争议时，以现有 HTTP 入口、数据库表和主要业务用例三者中占多数的一侧作为所有者。

### 8.1 Agent 与治理边界

- `agent` 拥有在线运行时：聊天、路由、上下文、Native Skill、Tool Calling、医生/患者查询编排、运行时 Skill 目录和会话版本绑定。
- `governance` 拥有后台治理：Skill 版本管理、Agent 评测数据集、评测 Runner/Scorer/Fixture、评测结果、人工审批和治理 Controller。
- 当前 `agent.evaluation` 整体迁移到 `governance.agent.evaluation`；它可以调用 `agent` 的公开运行时接口，但不得改变这些接口。
- Skill 定义和会话绑定若被在线请求直接使用，则留在 `agent`；仅用于后台创建、评测、审批和启用的 Service、DTO、VO、Entity 与 Mapper 进入 `governance`。
- 迁移中允许两者继续保持现有调用关系，本轮不新增 facade 或重新设计依赖方向。

## 9. 兼容性约束

### 9.1 Spring

- 根启动类包名不变，继续覆盖所有子包扫描。
- `@Service`、`@Component`、`@Configuration` 等注解保持不变。
- 不显式修改 Bean 名称；类名保持不变，因此默认 Bean 名保持不变。
- 更新所有显式 `@MapperScan`、`@ComponentScan`、条件配置和测试扫描路径。

### 9.2 MyBatis

- Mapper XML 文件路径可以保持不变，避免无关资源移动。
- 必须更新 XML 中的 `namespace`、`resultType`、`parameterType` 和其他完整类名。
- 检查 MyBatis-Plus 泛型 Entity import 和类型别名。
- SQL 文本、列名、动态条件和分页逻辑不得修改。

### 9.3 Jackson、缓存与反射

- 检索源码、YAML、XML 和测试中的完整 Java 类名字符串。
- 检查 JSON 多态类型、Redis 序列化、消息反序列化和反射加载是否依赖旧包名。
- 当前按“没有外部 Java API 消费者，也没有业务数据主动持久化完整类名”规划。
- 若实施检查发现数据库、Redis、MQ 消息或脚本实际保存旧完整类名，则暂停对应批次并补充兼容读取或数据迁移设计；不得凭空增加大量兼容别名，也不得直接让历史数据失效。

### 9.4 RabbitMQ 与任务执行

- 消息 DTO 内容和 JSON 字段不变。
- Listener、Publisher、Outbox 与任务状态流转只移动包。
- 队列、交换机、routing key、Confirm、ACK、重试和死信行为不变。

### 9.5 API 与安全

- Controller 的 `@RequestMapping`、参数注解、权限注解和响应类型不变。
- Spring Security matcher、JWT 过滤器顺序和对象级权限校验不变。
- 不改变医生、患者、管理员和研究员的角色边界。

## 10. 分批迁移顺序

迁移采用从基础依赖到复杂业务、从较独立模块到高耦合模块的顺序：

### 批次 0：基线与迁移保护

- 记录 Java Git 状态和当前测试基线。
- 增加或调整 ArchUnit 迁移规则，先允许新旧包共存，但禁止新增类继续进入待淘汰的全局目录。
- 建立旧包到目标模块的迁移清单。

### 批次 1：`shared`、`platform`、`auth`、`user`

- 先稳定统一响应、异常、JWT、Redis 安全与用户身份相关依赖。
- 保持 Security 配置和认证链行为不变。

### 批次 2：`patient`、`cases`、`imaging`

- 迁移患者档案、病例归属、图像上传和文件访问。
- 重点验证对象级权限与文件路径安全。

### 批次 3：`analysis`

- 将现有 `application/domain/infrastructure` 和遗留任务 Service 统一到目标结构。
- 重点验证状态机、Outbox、MQ、AI 调用、重试与幂等测试。

### 批次 4：`clinical`、`reporting`

- 迁移人工修正、审核、签发、PDF 和患者解释。
- 重点验证审核前置条件、报告访问边界和二进制下载。

### 批次 5：`knowledge`、`agent`

- 迁移 RAG、Embedding、Qdrant、Agent Router、上下文和 Skill 编排。
- 迁移在线 Agent 运行时接口，但暂时允许治理评测代码通过更新后的 import 使用这些接口。

### 批次 6：`governance`、`operations`

- 迁移 Prompt/Skill 管理、Agent/Prompt/RAG 评测、评测 Fixture、审计、质控、系统状态、统计和死信恢复。
- 清空不再使用的全局 `controller/service/mapper/pojo/enumeration` 目录。

### 批次 7：收尾

- 收紧 ArchUnit：禁止生产代码落入旧全局技术目录。
- 更新后端目录文档和相关 ADR。
- 全量验证并检查 Git diff 只包含包迁移、引用和必要文档。

每个批次单独提交，不把全部结构迁移压成一个提交。

## 11. 每批执行方法

1. 根据清单选定本批类及其测试。
2. 使用文件移动保留 Git 重命名识别能力。
3. 更新 `package` 声明和 Java import。
4. 更新 XML、YAML、注解和字符串中的完整类名。
5. 更新同批测试包和 import。
6. 先运行模块相关测试，再运行完整 `mvn test`。
7. 检查 `git diff --summary` 和 `git diff --stat`，确认没有业务逻辑改写。
8. 独立提交本批结果。

若一个批次无法在不改行为的前提下通过测试，应缩小批次或保留临时兼容包；不得借目录迁移顺手重写业务。

## 12. 测试与验收

### 12.1 自动验证

- 每批运行受影响模块的单元测试和契约测试。
- 每批运行：

```powershell
mvn test
```

- 最终运行：

```powershell
mvn clean package
```

- 不调用真实外部 LLM 作为结构迁移的自动化门槛。
- 不要求前端和 Python 因纯 Java 包迁移发生代码修改；最终可执行现有仓库验证脚本确认无跨项目回归。

### 12.2 结构验收

- 生产代码不再集中在全局 `controller/service/mapper/pojo/enumeration`。
- 功能相关的 Controller、Service、Mapper 和模型可以从同一顶层模块找到。
- `analysis` 与其他模块采用一致的目录思路。
- 不存在旧包完整类名残留，允许的历史文档引用除外。
- ArchUnit 能阻止重新向旧全局技术目录添加生产类，并继续保护迁移前已经生效的关键行为约束。
- 本轮不要求 ArchUnit 消除现有 Controller-to-Mapper 或跨功能 Mapper 依赖；这些历史依赖只记录，不在机械迁移中重构。

### 12.3 行为验收

- 公共 HTTP 契约无变化。
- MyBatis Mapper 正常加载，SQL 行为无变化。
- Spring 容器不存在 Bean 缺失、重复或循环依赖新增。
- JWT、对象级权限、Agent 安全策略保持原行为。
- RabbitMQ、Outbox、任务执行和死信流程保持原行为。
- 报告、图片和 mask 的二进制接口保持原行为。

## 13. 风险与控制

### 13.1 大量 import 变化掩盖业务修改

控制：每批只做移动和引用修复；使用独立提交；审查时重点检查非 `package/import` 的文本差异。

### 13.2 MyBatis XML 仍引用旧完整类名

控制：全仓搜索旧包前缀；启动上下文测试与 Mapper 测试必须通过。

### 13.3 Spring 扫描或测试切片失效

控制：检查显式扫描注解、`@SpringBootTest`、`@WebMvcTest`、Mockito import 和测试资源。

### 13.4 包私有访问被移动破坏

控制：同一内聚单元一起迁移；不得为了通过编译随意扩大 `public` 可见性。确需改变可见性时暂停并单独设计。

### 13.5 历史序列化数据保存旧类名

控制：迁移前搜索序列化配置和数据库/Redis 使用方式；若存在 Java 类型元数据，增加兼容读取而不是清空数据。

### 13.6 与用户现有修改冲突

控制：实施前确认 Git 状态；只修改 Java 重构涉及文件；不重置、不覆盖用户的前端或其他未提交修改。

## 14. 完成标准

满足以下条件后，本轮 Java 目录重构完成：

1. 所有目标功能模块均采用统一的按功能组织方式。
2. `analysis` 已从旧分层迁移到统一结构，行为和类名不变。
3. 旧全局技术目录不再承载生产业务类。
4. 所有 MyBatis、Spring、MQ、序列化和测试引用均已更新。
5. ArchUnit 能防止代码重新堆回旧全局技术目录，并保留现有关键架构保护。
6. 完整 `mvn test` 与 `mvn clean package` 通过。
7. 每批拥有独立、可审查的提交。
8. README、后端文档和 ADR 与最终目录一致。


