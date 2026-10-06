# 医生 Agent 任务与临床队列设计

## 1. 背景与目标

医生 Agent 已支持工作量统计、负责病例筛选、病例摘要、随访比较和医学知识检索，但“任务”“待审核”“待签发”仍被投影为病例列表。医生无法直接查看任务状态、失败摘要、待审核结果详情，也不能从 Agent 回答准确跳转到任务详情或临床审核步骤。

本阶段将 Agent 补齐为医生日常工作的只读入口：医生使用自然语言查询本人负责病例下的分析任务、待审核结果和已审核未签发报告，后端返回可信结构化数据及受控页面动作。Agent 不执行创建、取消、重试、审核或签发。

成功标准：

- “任务”默认指血管分割任务，明确提到图像质检时才查询质量检测任务。
- 任务、待审核和待签发均在 SQL 查询阶段限定当前医生。
- 列表默认按最近业务时间倒序，每页最多 10 条。
- 支持任务编号、当前页序号、分页和状态筛选。
- 从结构化卡片跳转任务详情或临床审核步骤，不执行任意模型 URL。
- 开放式自然语言先由 LLM Router 基于启用的 Skill 定义选择 `skillCode` 并提取参数；“继续、上一页、查看第三个”等上下文命令由 Java 确定性解析。
- `DIRECT` Skill 路由完成后不再调用 LLM，查询服务自身目标 P95 小于 1 秒；`TOOL_CALLING` Skill 才进入受控工具循环。

## 2. 非目标

- 不通过自然语言创建、取消、重试或修改任务优先级。
- 不通过 Agent 保存医生审核、上传修正 mask 或签发 PDF。
- 不向医生开放平台质控、死信恢复、Prompt/RAG 管理能力。
- 不返回原图、mask、本地路径、完整结果 JSON 或完整异常堆栈。
- 不建设通用工作流引擎，也不接入 MCP。
- 不改变患者、管理员和研究员 Agent 的现有工具范围。

## 3. 方案选择

采用两个独立 Skill，而不继续扩展病例筛选：

- `DOCTOR_TASK_SEARCH`：任务列表、状态筛选、任务编号定位和任务详情。
- `DOCTOR_CLINICAL_QUEUE`：待审核结果和已审核待签发报告。

这样可以让每个 Skill 拥有独立的参数、结构化响应、当前页引用、评测语料和工具白名单，避免“第三个病例”“第三个任务”“第二个待审核结果”互相污染。

## 4. 自然语言语义

### 4.1 `DOCTOR_TASK_SEARCH`

典型表达：

- 查询我的任务。
- 哪些分割任务失败了？
- 最近七天有哪些正在处理的任务？
- 查看任务 `T202609270001`。
- 查看第三个任务。
- 查询图像质检任务。

默认规则：

- 未明确任务类型时，`taskType = VESSEL_SEGMENTATION`。
- “图像质检、质量检测、质检任务”映射到 `IMAGE_QUALITY_CHECK`。
- 状态支持 `ANY / WAITING / RUNNING / FAILED / SUCCESS`；`WAITING` 包含 `CREATED` 和 `WAITING`，`RUNNING` 包含 `RUNNING` 和 `RETRYING`。
- 时间支持 `ANY / TODAY / LAST_7_DAYS / LAST_30_DAYS`。
- 显式任务编号进入详情；列表序号只解析当前任务页引用。
- 默认按 `analysis_task.updated_at DESC, id DESC`，每页最多 10 条。

### 4.2 `DOCTOR_CLINICAL_QUEUE`

典型表达：

- 今天有哪些待审核结果？
- 查看第二个待审核结果。
- 哪些结果已经审核但还没有签发？
- 打开第一个待签发报告。

队列类型：

- `PENDING_REVIEW`：血管分割任务成功，存在结果，且尚无审核记录，或审核状态为 `PENDING / CHANGES_REQUESTED`。
- `PENDING_REPORT`：审核状态为 `APPROVED`，且不存在状态为 `SIGNED` 的报告。

队列按任务完成时间倒序；只返回当前医生负责病例下的结果。队列序号只解析当前临床队列页引用。

## 5. 后端架构

### 5.1 路由和编排

`AgentSkillCode` 增加两个编码，Java 中为每个 Skill 固定声明 `DIRECT` 或 `TOOL_CALLING` 执行模式。数据库 Skill 版本提供路由说明、正反例和参数契约，但不能扩大执行模式、角色权限、工具白名单或字段白名单。

初始开放式问题由 LLM Router 处理。Router 只接收用户问题、当前角色可用的 Skill 目录和受控 JSON 输出契约，不注册业务工具；输出仅允许 `skillCode`、`confidence` 和 `arguments`。Java 对 Skill 编码、角色、枚举参数和默认值进行二次校验。

确定性上下文解析在 LLM Router 之前执行：

1. 上下文命令和序号表达。
2. 待审核、待签发语义。
3. 任务编号、任务状态、任务类型语义。
4. 上下文类型不匹配时直接返回重新查询提示，不让模型猜测旧引用。

`DoctorAgentSkillOrchestrator` 根据 Java 固定的执行模式分流：

- `DIRECT`：工作量、病例筛选、病例摘要、随访比较、任务查询和临床队列由 Orchestrator 直接调用 Java Service，结果组装为固定事实摘要和结构化数据，不再次发送给 LLM。
- `TOOL_CALLING`：医学知识问答进入第二次 LLM 调用，并且只注册该 Skill 允许的 `searchMedicalKnowledge` 工具；工具结果返回模型后生成带引用回答。

模型路由失败、返回未知 Skill 或参数协议异常时返回可读错误，不降级为全工具调用，也不让模型直接生成业务查询结果。

### 5.2 查询服务

扩展 `DoctorAgentQueryService`：

```java
PageResult<DoctorAgentTaskSummaryVO> searchTasks(
        DoctorTaskSearchCriteria criteria, int page, int pageSize, CurrentUserVO doctor);

DoctorAgentTaskDetailVO getTaskDetail(
        String taskReference, CurrentUserVO doctor);

PageResult<DoctorClinicalQueueItemVO> searchClinicalQueue(
        DoctorClinicalQueueCriteria criteria, int page, int pageSize, CurrentUserVO doctor);
```

Mapper SQL 必须直接包含：

```sql
medical_case.assigned_doctor_id = :doctorId
```

任务编号解析也必须与医生 ID 同时查询，禁止先全局解析再鉴权。越权和不存在统一返回“资源不存在”。

### 5.3 数据投影

任务列表只返回：

- `taskId`、`taskNo`
- `caseId`、`caseNo`、`patientNo`
- `taskType`、`status`
- `retryCount`、`maxRetryCount`
- 有限长度、单行化的 `errorSummary`
- `submittedAt`、`startedAt`、`finishedAt`、`updatedAt`

任务详情在上述字段基础上增加最近 5 条任务日志摘要，不返回完整堆栈、路径或结果 JSON。

临床队列只返回：

- `taskId`、`taskNo`、`resultId`
- `caseId`、`caseNo`、`patientNo`、`eyeSide`
- `resultType`、`qualityStatus`、`qualityScore`
- `reviewStatus`、`reportStatus`
- `finishedAt`、`resultCreatedAt`

## 6. 上下文隔离

新增 `AgentReferenceType`：

- `CASE`
- `TASK`
- `CLINICAL_QUEUE`

`agent_query_context` 增加 `reference_type`。现有 `recent_result_references_json` 继续保存当前页最多 10 个内部 ID，但必须结合 `reference_type` 解释：病例页保存病例 ID，任务页和临床队列保存任务 ID。

上下文切换规则：

- 新列表查询替换旧列表引用和筛选条件。
- “查看第三个任务”只接受 `TASK` 上下文。
- “查看第二个待审核结果”只接受 `CLINICAL_QUEUE` 上下文。
- 类型不匹配、越界、过期或权限失效时，要求用户重新查询对应列表。
- 进入任务详情后保存 `selectedTaskId` 和关联 `selectedCaseId`。

## 7. 结构化响应与页面动作

新增数据类型：

- `TASK_LIST`
- `TASK_DETAIL`
- `CLINICAL_QUEUE`

新增或复用受控动作：

- `VIEW_TASK`：`/tasks/{taskId}`
- `VIEW_REVIEW`：`/tasks/{taskId}?tab=clinical&stage=review`
- `NEXT_PAGE`
- `PREVIOUS_PAGE`

路径由 Java 根据动作类型和已鉴权内部 ID 生成。模型文本、数据库字段和用户输入都不能直接成为跳转 URL。

`POST /agent/chat` 路径保持不变。`AgentChatResponseVO` 继续使用现有 `data / pagination / actions`，仅扩展允许的数据类型与载荷。

## 8. 前端体验

`ClinicalAgent.vue` 继续负责会话和消息发送，结构化展示拆分为：

- `AgentTaskList.vue`
- `AgentTaskDetail.vue`
- `AgentClinicalQueue.vue`

任务列表卡片突出状态、任务编号、病例号、匿名患者编号和时间；失败任务显示有限失败摘要。任务详情显示最近日志。临床队列卡片突出“待审核”或“待签发”及进入工作台按钮。

卡片最多 10 条，窄屏改为单列。分页和详情按钮调用后端返回的受控动作，不在前端自行拼接业务 ID。历史会话通过 `structured_content_json` 恢复相同卡片。

医生快捷问题增加：

- 查询我的分割任务。
- 哪些分割任务失败了？
- 今天有哪些待审核结果？
- 哪些结果已经审核但还没有签发？

## 9. Skill 版本与迁移

Flyway 新增两个 `agent_skill` 和各自 v1 版本，并为现有上下文表增加 `reference_type`。新 Skill 版本默认不启用，必须满足：

- 路由准确率至少 90%。
- 参数准确率至少 95%。
- 安全测试全部通过。

旧会话没有 `reference_type` 时，兼容解释为 `CASE`。新会话分别绑定每个 Skill 的启用版本。

## 10. 安全与失败策略

- 仅 `DOCTOR` 注册这两个 Skill。
- 查询 SQL 必须直接限定负责医生。
- 管理员、患者和研究员不能调用医生任务或临床队列能力。
- 任务详情不存在或越权统一返回 404 语义。
- 上下文过期返回可读提示，不写入失败聊天消息。
- 结构化查询失败不降级为 LLM 猜测。
- 所有列表字段经过白名单投影，错误摘要单行化并限制长度。

## 11. 测试与验收

### 11.1 单元和契约测试

- 默认任务类型是血管分割；明确质检时使用图像质量检测。
- 状态、日期、任务编号和队列类型参数正确。
- 任务与临床队列按时间倒序，每页最多 10 条。
- SQL 查询包含当前医生 ID，其他医生任务不可见。
- 任务详情只返回最近 5 条脱敏日志。
- 待审核和待签发条件与业务状态一致。
- 列表引用类型隔离，序号越界和过期安全失败。
- 页面动作仅能生成允许的任务与审核路径。
- 新 Skill 未评测或评测失败时不能启用。

### 11.2 前端验证

- 任务列表、任务详情和临床队列卡片正常显示。
- 空状态、失败状态、分页和历史恢复正常。
- 点击任务进入任务详情。
- 点击待审核或待签发进入临床审核步骤。
- API 失败后 loading 复位，输入与既有消息不丢失。

### 11.3 真实验收语句

```text
查询我的任务
哪些分割任务失败了
查询图像质检任务
查看第三个任务
今天有哪些待审核结果
查看第二个待审核结果
哪些结果已经审核但还没有签发
打开第一个待签发报告
```

真实验收同时核对数据库结果、当前医生权限、结构化卡片、目标路径和响应耗时。

## 12. 兼容性

- 保留现有病例、随访、知识 Skill 和 `/agent/chat` 契约。
- 保留现有 `VIEW_TASK / VIEW_REVIEW` 动作枚举。
- 历史会话和缺少 `reference_type` 的上下文继续可读。
- 不改变 RabbitMQ 推理、医生审核、PDF 签发或 RAG 链路。
