# API_CONTRACT.md — RetinaVision 前后端接口契约

本文档是 RetinaVision 前后端联调的唯一接口事实来源。后端接口路径、请求参数、响应字段、枚举值、错误码和文件访问规则均以本文档为准。

契约边界仅包括“浏览器前端 ↔ Java 后端”。Java 后端调用 Python AI 的 `/health`、`/v1/inference/image-quality-check`、`/v1/inference/vessel-segmentation` 和 `/v1/artifacts/{inferenceId}/mask` 属于内部服务接口，由 `ai-service/README.md` 维护，不暴露为浏览器业务 API。

当前源码仍有以下已知偏差，修改相关代码时必须优先收敛到本契约：

- Java 创建任务 VO 当前字段名是 `errorMessage`，契约与前端类型要求成功提示字段为 `message`。
- Java 取消任务当前返回 `ApiResponse<null>`，契约要求 `ApiResponse<boolean>`。
- 病例和图像删除服务尚未实现契约要求的“存在运行中/成功任务时拒绝删除”检查。

## 1. 基础约定

### 1.1 项目信息

- 项目名称：RetinaVision 眼底图像 AI 分析任务管理系统
- 前端技术栈：Vue 3 + Vite + TypeScript + Element Plus
- API 基础路径：`/api`
- 本地后端建议地址：`http://localhost:8080`
- 前端开发代理：前端访问 `/api/**`，由 Vite 转发到后端

前端 `src/api/*` 中的接口路径不写 `/api` 前缀。例如：

```ts
request.post('/auth/login', data)
```

最终浏览器请求地址为：

```text
/api/auth/login
```

### 1.2 鉴权

除以下接口外，其余接口都需要登录：

- `POST /auth/register`
- `POST /auth/login`

登录后，前端会在请求头中携带：

```text
Authorization: Bearer <token>
```

后端需要校验该 token，并在失效时返回 HTTP `401` 或业务错误码 `40100`。

### 1.3 时间、ID、分页

- ID 类型：后端可用 `Long`，前端按 `number` 接收
- 时间字段：统一字符串格式 `yyyy-MM-dd HH:mm:ss`
- 枚举值：统一使用英文大写字符串传输
- 分页页码：`pageNo` 从 `1` 开始
- 默认分页：`pageNo = 1`，`pageSize = 10`

### 1.4 通用响应结构

所有 JSON 接口返回：

```ts
export interface ApiResponse<T> {
  code: number
  message: string
  data: T
}
```

成功响应：

```json
{
  "code": 0,
  "message": "success",
  "data": {}
}
```

分页响应：

```ts
export interface PageResult<T> {
  records: T[]
  total: number
  pageNo: number
  pageSize: number
}
```

分页成功示例：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "records": [],
    "total": 0,
    "pageNo": 1,
    "pageSize": 10
  }
}
```

### 1.5 HTTP Content-Type

JSON 请求：

```text
Content-Type: application/json
```

文件上传：

```text
Content-Type: multipart/form-data
```

图片预览、报告下载接口可返回二进制流，不强制套 `ApiResponse<T>`。

## 2. 错误码

| code | 含义 | 前端处理 |
|---:|---|---|
| 0 | 成功 | 正常处理 |
| 40000 | 请求参数错误 | 显示错误提示 |
| 40001 | 用户名或密码错误 | 登录页提示 |
| 40100 | 未登录或 Token 失效 | 清除 token，跳转 `/login` |
| 40300 | 无权限访问 | 显示无权限提示 |
| 40400 | 资源不存在 | 显示资源不存在 |
| 40900 | 重复提交或状态冲突 | 显示业务冲突 |
| 42900 | 登录尝试或 AI 任务提交过于频繁 | 显示提示，并按 `Retry-After` 秒数等待 |
| 50000 | 服务端内部错误 | 显示服务端异常 |
| 50001 | 文件存储异常 | 显示上传或下载失败 |
| 50002 | MQ 消息投递异常 | 显示任务提交失败 |
| 50003 | AI 分析任务执行异常 | 显示任务执行失败 |
| 50300 | Redis 等关键依赖暂时不可用 | 显示服务暂不可用，稍后重试 |

错误响应示例：

```json
{
  "code": 40000,
  "message": "请求参数错误",
  "data": null
}
```

## 3. 枚举定义

### 3.1 UserRole

| 值 | 中文含义 | 说明 |
|---|---|---|
| `ADMIN` | 管理员 | 不允许前台注册 |
| `DOCTOR` | 医生 | 由管理员授予并维护职业标识 |
| `RESEARCHER` | 研究员 | 由管理员授予；当前仅开放知识助手 |
| `USER` | 患者 | 公开注册；注册后自动生成匿名患者档案 |

### 3.2 PatientGender

| 值 | 中文含义 |
|---|---|
| `MALE` | 男 |
| `FEMALE` | 女 |
| `UNKNOWN` | 未知 |

### 3.3 EyeSide

| 值 | 中文含义 |
|---|---|
| `LEFT` | 左眼 |
| `RIGHT` | 右眼 |
| `BOTH` | 双眼 |

### 3.4 CaseStatus

| 值 | 中文含义 | 说明 |
|---|---|---|
| `ACTIVE` | 正常 | 可编辑、可上传图像 |
| `ARCHIVED` | 已归档 | 只读或限制操作，由后端决定 |
| `DELETED` | 已删除 | 软删除状态 |

### 3.4.1 CaseWorkflowStatus

| 值 | 中文含义 |
|---|---|
| `DRAFT` | 患者准备资料 |
| `SUBMITTED` | 已提交负责医生 |
| `IN_REVIEW` | 医生分析与审核中 |
| `COMPLETED` | 正式报告已签发 |
| `WITHDRAWN` | 患者已撤回 |

### 3.5 ImageStatus

| 值 | 中文含义 | 说明 |
|---|---|---|
| `UPLOADED` | 已上传 | 可用于创建任务 |
| `BOUND_TASK` | 已绑定任务 | 已有关联任务 |
| `DELETED` | 已删除 | 不可用于创建任务 |

### 3.6 TaskType

| 值 | 中文含义 | 说明 |
|---|---|---|
| `VESSEL_SEGMENTATION` | 血管分割 | 生成 mask 图和血管指标 |
| `IMAGE_QUALITY_CHECK` | 图像质量检测 | 生成质量评分指标 |

后续可扩展：

- `LESION_SCREENING`
- `REPORT_GENERATION`

### 3.7 TaskStatus

| 值 | 中文含义 | 说明 |
|---|---|---|
| `CREATED` | 已创建 | 任务已入库 |
| `WAITING` | 排队中 | 等待 Worker 消费 |
| `RUNNING` | 运行中 | Worker 正在处理 |
| `SUCCESS` | 成功 | 有分析结果 |
| `FAILED` | 失败 | 可查看错误原因，可重试 |
| `RETRYING` | 重试中 | 正在重新投递或重新排队 |
| `CANCELED` | 已取消 | 已取消，不再处理 |

### 3.8 OperatorType

| 值 | 中文含义 |
|---|---|
| `USER` | 用户操作 |
| `SYSTEM` | 系统操作 |
| `WORKER` | Worker 操作 |
| `ADMIN` | 管理员操作 |

## 4. Auth 认证模块

### 4.1 用户注册

```text
POST /auth/register
```

权限：公开接口，无需 token。

请求体：

```ts
export interface RegisterRequest {
  username: string
  password: string
  realName: string
  roleCode: 'DOCTOR' | 'RESEARCHER' | 'USER'
}
```

字段规则：

| 字段 | 类型 | 必填 | 规则 |
|---|---|---|---|
| `username` | string | 是 | 建议 3-32 个字符，唯一 |
| `password` | string | 是 | 建议 6-64 个字符，后端必须加密存储 |
| `realName` | string | 是 | 建议 2-32 个字符 |
| `roleCode` | enum | 是 | 只能为 `DOCTOR`、`RESEARCHER`、`USER` |

响应：

```ts
ApiResponse<boolean>
```

成功示例：

```json
{
  "code": 0,
  "message": "success",
  "data": true
}
```

错误建议：

- 用户名已存在：`40900`
- 参数不合法：`40000`
- 试图注册 `ADMIN`：`40300` 或 `40000`

前端行为：

- 注册成功后跳转 `/login`
- 不自动登录

### 4.2 登录

```text
POST /auth/login
```

权限：公开接口，无需 token。

请求体：

```ts
export interface LoginRequest {
  username: string
  password: string
}
```

响应：

```ts
export interface LoginResponse {
  token: string
  user: CurrentUser
}

export interface CurrentUser {
  id: number
  username: string
  realName: string
  roleCode: 'ADMIN' | 'DOCTOR' | 'RESEARCHER' | 'USER'
}
```

成功示例：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "token": "jwt-token",
    "user": {
      "id": 1,
      "username": "doctor01",
      "realName": "张医生",
      "roleCode": "DOCTOR"
    }
  }
}
```

错误建议：

- 用户名或密码错误：`40001`
- 用户被禁用：`40300`

### 4.3 获取当前用户

```text
GET /auth/me
```

权限：需要 token。

请求参数：无。

响应：

```ts
ApiResponse<CurrentUser>
```

成功示例：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "id": 1,
    "username": "doctor01",
    "realName": "张医生",
    "roleCode": "DOCTOR"
  }
}
```

### 4.4 退出登录

```text
POST /auth/logout
```

权限：需要 token。

请求参数：无。

响应：

```ts
ApiResponse<boolean>
```

说明：

- 后端将当前 JWT 的 `jti` 写入 Redis 黑名单，TTL 等于 token 剩余有效期；重复退出保持幂等
- 新签发 JWT 必须包含 `jti`，部署前签发且缺少 `jti` 的旧 token 失效，需要重新登录
- 前端无论接口是否成功都会清除本地 token 和用户信息

## 5. Case 病例模块

`USER` 在产品中表示患者本人。患者只能访问绑定在本人 `patient_profile.account_user_id` 下的病例；`DOCTOR` 只能访问 `assignedDoctorId` 为自己的病例；`RESEARCHER` 与 `ADMIN` 不可访问病例临床详情。图像、任务、结果、审核和报告继承同一归属规则，越权与不存在统一返回 `40400`。

匿名患者编号由后端安全随机生成，格式为 `PT-XXXX-XXXX`，不包含数据库 ID、姓名、手机号、生日或身份证信息。旧 `patientCode` 仅作为历史兼容字段保留，新接口使用 `patientId` 与 `patientNo`。

创建病例前可查询启用医生：

```text
GET /users/doctors
```

响应为 `ApiResponse<Array<{ id: number; displayName: string; professionalNo: string | null }>>`，不返回用户名、手机号或其他账户字段。

### 5.1 分页查询病例

```text
GET /cases
```

权限：需要 token。

Query 参数：

| 参数 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `pageNo` | number | 否 | 页码，默认 1 |
| `pageSize` | number | 否 | 每页数量，默认 10 |
| `keyword` | string | 否 | 匹配 `caseNo` 或 `patientCode` |
| `status` | CaseStatus | 否 | 病例状态 |
| `eyeSide` | EyeSide | 否 | 眼别 |

响应：

```ts
export interface CaseListItem {
  id: number
  caseNo: string
  patientId: number
  patientNo: string
  patientCode: string // 历史只读兼容字段
  patientAge: number | null
  patientGender: 'MALE' | 'FEMALE' | 'UNKNOWN'
  eyeSide: 'LEFT' | 'RIGHT' | 'BOTH'
  status: 'ACTIVE' | 'ARCHIVED' | 'DELETED'
  workflowStatus: 'DRAFT' | 'SUBMITTED' | 'IN_REVIEW' | 'COMPLETED' | 'WITHDRAWN'
  createdBy: number
  createdByName: string
  assignedDoctorId: number | null
  assignedDoctorName: string | null
  assignedDoctorProfessionalNo: string | null
  createdAt: string
  updatedAt: string
}
```

返回类型：

```ts
ApiResponse<PageResult<CaseListItem>>
```

示例：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "records": [
      {
        "id": 1,
        "caseNo": "C202606010001",
        "patientCode": "P001",
        "patientAge": 62,
        "patientGender": "MALE",
        "eyeSide": "LEFT",
        "status": "ACTIVE",
        "createdBy": 1,
        "createdByName": "张医生",
        "assignedDoctorId": 8,
        "assignedDoctorName": "李医生",
        "assignedDoctorProfessionalNo": "DOC-008",
        "createdAt": "2026-06-01 10:00:00",
        "updatedAt": "2026-06-01 10:00:00"
      }
    ],
    "total": 1,
    "pageNo": 1,
    "pageSize": 10
  }
}
```

### 5.2 创建病例

```text
POST /cases
```

权限：需要 token。

请求体：

```ts
export interface CreateCaseRequest {
  patientId?: number
  patientAge?: number
  patientGender: 'MALE' | 'FEMALE' | 'UNKNOWN'
  eyeSide: 'LEFT' | 'RIGHT' | 'BOTH'
  diagnosisNote?: string
  assignedDoctorId?: number
}
```

字段规则：

| 字段 | 类型 | 必填 | 规则 |
|---|---|---|---|
| `patientId` | number | 医生创建时必填 | 患者请求不得伪造该字段；后端从登录账号推导本人档案 |
| `patientAge` | number | 否 | 0-120 |
| `patientGender` | enum | 是 | `MALE`、`FEMALE`、`UNKNOWN` |
| `eyeSide` | enum | 是 | `LEFT`、`RIGHT`、`BOTH` |
| `diagnosisNote` | string | 否 | 最长 512 个字符 |
| `assignedDoctorId` | number | 患者必填 | 必须是启用状态的医生；医生创建时忽略并自动归属本人 |

响应：

```ts
export interface CaseDetail {
  id: number
  caseNo: string
  patientCode: string
  patientAge: number | null
  patientGender: 'MALE' | 'FEMALE' | 'UNKNOWN'
  eyeSide: 'LEFT' | 'RIGHT' | 'BOTH'
  diagnosisNote: string | null
  status: 'ACTIVE' | 'ARCHIVED' | 'DELETED'
  createdBy: number
  createdByName: string
  assignedDoctorId: number | null
  assignedDoctorName: string | null
  assignedDoctorProfessionalNo: string | null
  createdAt: string
  updatedAt: string
}
```

返回类型：

```ts
ApiResponse<CaseDetail>
```

成功示例：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "id": 101,
    "caseNo": "C202606100001",
    "patientCode": "P001",
    "patientAge": 62,
    "patientGender": "MALE",
    "eyeSide": "LEFT",
    "diagnosisNote": "左眼视盘边界欠清，建议进一步检查",
    "status": "ACTIVE",
    "createdBy": 1,
    "createdByName": "张医生",
    "createdAt": "2026-06-10 10:30:00",
    "updatedAt": "2026-06-10 10:30:00"
  }
}
```

说明：

- 后端负责生成 `caseNo`
- `createdBy` 从当前登录用户获取
- `createdByName` 根据 `createdBy` 查询用户真实姓名得到
- 业务状态初始为 `ACTIVE`，检查流程状态固定为 `DRAFT`
- `createdAt`、`updatedAt` 由后端生成
- 前端创建病例时不要传 `id`、`caseNo`、`status`、`createdBy`、`createdByName`、`createdAt`、`updatedAt`

患者可在 `DRAFT` 且尚无分析任务时调整负责医生：

```text
PUT /cases/{caseId}/doctor-assignment
{ "assignedDoctorId": 8 }
```

目标用户必须是启用医生；病例一旦存在分析任务即锁定归属。医生仍有负责病例时，管理员不能将其角色降级。

### 5.2.1 匿名患者档案与检查申请

```text
GET  /patients/me
POST /patients/offline
GET  /patients
POST /cases/{caseId}/submit
POST /cases/{caseId}/withdraw
GET  /cases/{caseId}/progress
GET  /cases/{caseId}/signed-reports
GET  /cases/{caseId}/signed-reports/{resultId}/{version}/download
```

- `GET /patients/me` 仅患者访问，返回本人匿名档案。
- `POST /patients/offline` 与 `GET /patients` 仅医生访问，用于创建线下匿名患者和查询与本人病例有关的匿名档案。
- 患者在 `DRAFT` 上传图像，上传成功后由后端内部自动创建图像质量检测任务；患者不能调用技术任务创建、重试、取消或质量覆盖接口。
- `submit` 要求已选择医生且至少有一张未删除图像，状态变为 `SUBMITTED`。
- `withdraw` 仅允许在医生尚未创建血管分割任务时执行，状态变为 `WITHDRAWN`。
- `progress` 仅返回脱敏业务进度和质量重传提示，不返回模型、mask、技术日志、AI 草稿或未签发医生意见。
- `signed-reports` 仅返回 `SIGNED/SUPERSEDED` 正式报告；PDF 下载同样执行本人对象级鉴权。

管理员历史档案治理接口为 `GET /admin/patient-profiles/legacy` 与 `PUT /admin/patient-profiles/{patientId}/account-link`，只展示匿名编号、旧编号、账号和病例数量，不返回临床内容。

### 5.3 查询病例详情

```text
GET /cases/{caseId}
```

权限：需要 token。

Path 参数：

| 参数 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `caseId` | number | 是 | 病例 ID |

响应：

```ts
ApiResponse<CaseDetail>
```

成功示例：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "id": 101,
    "caseNo": "C202606100001",
    "patientCode": "P001",
    "patientAge": 62,
    "patientGender": "MALE",
    "eyeSide": "LEFT",
    "diagnosisNote": "左眼视盘边界欠清，建议进一步检查",
    "status": "ACTIVE",
    "createdBy": 1,
    "createdByName": "张医生",
    "createdAt": "2026-06-10 10:30:00",
    "updatedAt": "2026-06-10 10:30:00"
  }
}
```

说明：

- 返回完整病例详情，字段与 5.2 中 `CaseDetail` 完全一致。
- `caseId` 查询的是数据库病例主键 `id`，不是业务编号 `caseNo`。
- 已软删除病例是否允许查询由后端决定；当前约定 `DELETED` 病例返回 `40400`，避免前端继续操作。
- 如果后续详情页需要关联图像或任务，建议仍通过独立接口查询，不要在该接口里混入大量关联列表。

错误建议：

```text
caseId 不存在或已删除：40400
未登录或 token 失效：40100 或 HTTP 401
```

### 5.4 更新病例

```text
PUT /cases/{caseId}
```

权限：需要 token。

Path 参数：

| 参数 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `caseId` | number | 是 | 病例 ID |

请求体：

```ts
export interface UpdateCaseRequest {
  patientAge?: number
  patientGender?: 'MALE' | 'FEMALE' | 'UNKNOWN'
  eyeSide?: 'LEFT' | 'RIGHT' | 'BOTH'
  diagnosisNote?: string
  status?: 'ACTIVE' | 'ARCHIVED'
}
```

响应：

```ts
ApiResponse<CaseDetail>
```

成功示例：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "id": 101,
    "caseNo": "C202606100001",
    "patientCode": "P001",
    "patientAge": 63,
    "patientGender": "MALE",
    "eyeSide": "BOTH",
    "diagnosisNote": "复查后补充：双眼均需进一步评估",
    "status": "ARCHIVED",
    "createdBy": 1,
    "createdByName": "张医生",
    "createdAt": "2026-06-10 10:30:00",
    "updatedAt": "2026-06-11 09:15:00"
  }
}
```

说明：

- 更新成功后返回完整 `CaseDetail`，方便前端直接刷新当前行或详情页。
- 请求体里的字段都是可选字段，但至少应提供一个可更新字段；空请求体建议返回 `40000`。
- `patientCode`、`caseNo`、`createdBy`、`createdByName`、`createdAt` 不允许通过该接口修改。
- `updatedAt` 由后端在更新成功时刷新，前端不要传。
- 前端不会把 `status` 更新为 `DELETED`；删除或软删除统一走 `DELETE /cases/{caseId}`。
- `status = ARCHIVED` 表示归档。当前约定归档后不允许创建新任务。
- 对 `patientAge` 的 `null` 语义：如果前端传 `null`，表示清空年龄；如果字段不存在，表示不修改年龄。

错误建议：

```text
caseId 不存在或已删除：40400
请求体为空或字段不合法：40000
状态冲突，例如已删除病例仍被更新：40900
未登录或 token 失效：40100 或 HTTP 401
```

### 5.5 删除或归档病例

```text
DELETE /cases/{caseId}
```

权限：需要 token。

Path 参数：

| 参数 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `caseId` | number | 是 | 病例 ID |

响应：

```ts
ApiResponse<boolean>
```

成功示例：

```json
{
  "code": 0,
  "message": "success",
  "data": true
}
```

说明：

- 该接口语义为“删除病例”，当前约定使用软删除：将 `status` 更新为 `DELETED`，必要时记录 `deletedAt`。
- 删除成功后列表接口默认不返回 `DELETED` 病例。
- 删除操作不建议物理删除数据库记录，避免图像、任务、报告等历史数据失去关联。
- 如果产品需要“归档”能力，应使用 `PUT /cases/{caseId}` 并传 `status: 'ARCHIVED'`；不要把归档和删除混在同一个 DELETE 接口里。
- 如果病例下存在 `CREATED`、`WAITING`、`RUNNING`、`RETRYING` 等未结束任务，后端应拒绝删除并返回 `40900`。
- 如果病例下只有 `SUCCESS`、`FAILED`、`CANCELED` 等已结束任务，当前约定允许软删除，但保留任务历史。
- 如果病例下存在未删除图像，后端可以同时软删除图像，也可以拒绝删除并返回 `40900`；两种策略需前后端统一。

错误建议：

```text
caseId 不存在或已删除：40400
存在运行中任务或业务状态冲突：40900
未登录或 token 失效：40100 或 HTTP 401
```

## 6. Image 图像模块

### 6.1 查询病例图像列表

```text
GET /cases/{caseId}/images
```

权限：需要 token。

Path 参数：

| 参数 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `caseId` | number | 是 | 病例 ID |

响应：

```ts
export interface ImageFileItem {
  id: number
  caseId: number
  originalFilename: string
  fileType: string
  fileSize: number
  storageBucket: string
  storageObjectKey: string
  previewUrl: string
  imageWidth: number | null
  imageHeight: number | null
  status: 'UPLOADED' | 'BOUND_TASK' | 'DELETED'
  uploadedBy: number
  uploadedByName: string
  uploadedAt: string
}
```

返回类型：

```ts
ApiResponse<ImageFileItem[]>
```

成功示例：

```json
{
  "code": 0,
  "message": "success",
  "data": [
    {
      "id": 201,
      "caseId": 101,
      "originalFilename": "fundus_left.jpg",
      "fileType": "image/jpeg",
      "fileSize": 2457600,
      "storageBucket": "retina-images",
      "storageObjectKey": "cases/101/fundus_left.jpg",
      "previewUrl": "/api/images/201/preview",
      "imageWidth": 2048,
      "imageHeight": 1536,
      "status": "UPLOADED",
      "uploadedBy": 1,
      "uploadedByName": "张医生",
      "uploadedAt": "2026-06-10 11:00:00"
    }
  ]
}
```

说明：

- 默认只返回当前病例下未删除图像，即 `status != 'DELETED'`。
- `previewUrl` 可以是后端可直接访问的相对路径，也可以是签名 URL；如果为空，前端会使用 `/images/{imageId}/preview`。
- `storageBucket`、`storageObjectKey` 主要用于后端定位文件；如果不希望前端感知对象存储细节，后续可改为仅后端内部字段。
- `caseId` 不存在或病例已删除时建议返回 `40400`。

### 6.2 上传病例图像

```text
POST /cases/{caseId}/images
```

权限：需要 token。

Path 参数：

| 参数 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `caseId` | number | 是 | 病例 ID |

FormData：

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `file` | File | 是 | 眼底图像文件 |

前端校验：

- 支持扩展名：`png`、`jpg`、`jpeg`、`tif`、`tiff`
- 支持 MIME：`image/png`、`image/jpeg`、`image/jpg`、`image/tiff`
- 单文件最大：20MB

响应：

```ts
ApiResponse<ImageFileItem>
```

成功示例：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "id": 201,
    "caseId": 101,
    "originalFilename": "fundus_left.jpg",
    "fileType": "image/jpeg",
    "fileSize": 2457600,
    "storageBucket": "retina-images",
    "storageObjectKey": "cases/101/fundus_left.jpg",
    "previewUrl": "/api/images/201/preview",
    "imageWidth": 2048,
    "imageHeight": 1536,
    "status": "UPLOADED",
    "uploadedBy": 1,
    "uploadedByName": "张医生",
    "uploadedAt": "2026-06-10 11:00:00"
  }
}
```

说明：

- 后端需要保存原图文件到对象存储或本地文件系统
- 数据库保存文件元数据
- `uploadedBy` 从当前登录用户获取
- 初始状态建议为 `UPLOADED`
- 上传前应校验病例存在且未删除；当前约定病例已归档时不允许上传并返回 `40900`。
- 文件类型或大小不合法返回 `40000`。
- 文件保存失败返回 `50001`。

### 6.3 图像预览

```text
GET /images/{imageId}/preview
```

权限：建议需要 token。

Path 参数：

| 参数 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `imageId` | number | 是 | 图像 ID |

响应：

```text
图片二进制流
```

建议响应头：

```text
Content-Type: image/png 或 image/jpeg 或 image/tiff
Cache-Control: private, max-age=300
```

说明：

- 如果后端返回需要鉴权的二进制流，前端后续可改为 Blob 方式读取
- 当前前端优先使用 `ImageFileItem.previewUrl`，为空时使用 `/images/{imageId}/preview`

### 6.4 删除图像

```text
DELETE /images/{imageId}
```

权限：需要 token。

Path 参数：

| 参数 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `imageId` | number | 是 | 图像 ID |

响应：

```ts
ApiResponse<boolean>
```

说明：

- 建议软删除，将状态更新为 `DELETED`
- 若图像已绑定运行中或成功任务，后端可返回 `40900`
- 删除成功后 `GET /cases/{caseId}/images` 默认不再返回该图像。
- 如果物理文件暂不删除，也应保证后续不能用该图像创建新任务。

错误建议：

```text
imageId 不存在或已删除：40400
图像已绑定运行中或成功任务：40900
未登录或 token 失效：40100 或 HTTP 401
```

## 7. Analysis Task 分析任务模块

### 7.1 分页查询任务

```text
GET /analysis-tasks
```

权限：需要 token。

Query 参数：

| 参数 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `pageNo` | number | 否 | 页码 |
| `pageSize` | number | 否 | 每页数量 |
| `status` | TaskStatus | 否 | 任务状态 |
| `taskType` | TaskType | 否 | 任务类型 |
| `caseNo` | string | 否 | 精确筛选某个病例编号。当前前端任务列表页暂不传该字段，保留给后续“从病例详情查看该病例任务”等场景 |
| `keyword` | string | 否 | 搜索框关键词，用于模糊匹配任务编号或病例编号 |

当前前端实际传参：

```ts
{
  pageNo: number
  pageSize: number
  keyword?: string
  status?: TaskStatus
  taskType?: TaskType
}
```

说明：

- 当前 `TaskList.vue` 只有一个“关键词”输入框，因此前端目前不会主动传 `caseNo`。
- `keyword` 是用户搜索框输入，适合模糊搜索，例如同时查 `taskNo` 和 `caseNo`。
- `caseNo` 是精确筛选字段，适合后续从某个病例上下文进入任务列表时使用。
- 如果请求同时传入 `caseNo` 和 `keyword`，当前约定按 AND 条件处理：先限制 `caseNo`，再在该范围内按 `keyword` 模糊搜索。
- 如果暂时不需要 `caseNo`，后端可以先实现 `keyword/status/taskType/pageNo/pageSize`，但保留 DTO 字段不会影响前端。

响应：

```ts
export interface TaskListItem {
  id: number
  taskNo: string
  caseId: number
  caseNo: string
  imageFileId: number
  originalFilename: string
  taskType: 'VESSEL_SEGMENTATION' | 'IMAGE_QUALITY_CHECK'
  status: 'CREATED' | 'WAITING' | 'RUNNING' | 'SUCCESS' | 'FAILED' | 'RETRYING' | 'CANCELED'
  priority: number
  retryCount: number
  maxRetryCount: number
  errorMessage: string | null
  submittedBy: number
  submittedByName: string
  submittedAt: string
  startedAt: string | null
  finishedAt: string | null
  updatedAt: string
}
```

返回类型：

```ts
ApiResponse<PageResult<TaskListItem>>
```

也就是说，后端实际 JSON 必须是：

```ts
ApiResponse<{
  records: TaskListItem[]
  total: number
  pageNo: number
  pageSize: number
}>
```

成功示例：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "records": [
      {
        "id": 1001,
        "taskNo": "T202606150001",
        "caseId": 101,
        "caseNo": "C202606150001",
        "imageFileId": 201,
        "originalFilename": "fundus_left.jpg",
        "taskType": "VESSEL_SEGMENTATION",
        "status": "WAITING",
        "priority": 5,
        "retryCount": 0,
        "maxRetryCount": 3,
        "errorMessage": null,
        "submittedBy": 1,
        "submittedByName": "张医生",
        "submittedAt": "2026-06-15 10:20:00",
        "startedAt": null,
        "finishedAt": null,
        "updatedAt": "2026-06-15 10:20:00"
      }
    ],
    "total": 1,
    "pageNo": 1,
    "pageSize": 10
  }
}
```

空分页示例：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "records": [],
    "total": 0,
    "pageNo": 1,
    "pageSize": 10
  }
}
```

字段来源建议：

| 返回字段 | 来源 |
|---|---|
| `id` | `analysis_task.id` |
| `taskNo` | `analysis_task.task_no` |
| `caseId` | `analysis_task.case_id` |
| `caseNo` | `medical_case.case_no` |
| `imageFileId` | `analysis_task.image_file_id` |
| `originalFilename` | `image_file.original_filename` |
| `taskType` | `analysis_task.task_type` |
| `status` | `analysis_task.status` |
| `priority` | `analysis_task.priority` |
| `retryCount` | `analysis_task.retry_count` |
| `maxRetryCount` | `analysis_task.max_retry_count` |
| `errorMessage` | `analysis_task.error_message` |
| `submittedBy` | `analysis_task.submitted_by` |
| `submittedByName` | `sys_user.real_name` |
| `submittedAt` | `analysis_task.submitted_at` |
| `startedAt` | `analysis_task.started_at` |
| `finishedAt` | `analysis_task.finished_at` |
| `updatedAt` | `analysis_task.updated_at` |

后端查询建议：

- 主表为 `analysis_task`。
- 需要 join `medical_case` 获取 `caseNo`。
- 需要 join `image_file` 获取 `originalFilename`。
- 需要 join `sys_user` 获取 `submittedByName`。
- 默认按 `analysis_task.submitted_at DESC, analysis_task.id DESC` 排序。
- 如果 `pageNo` 缺失或小于 1，建议按 `1` 处理。
- 如果 `pageSize` 缺失或小于 1，建议按 `10` 处理。
- 建议限制最大 `pageSize`，例如 `100`，避免一次查询过大。
- `status` 和 `taskType` 传入时精确匹配。
- `caseNo` 传入时按病例编号精确匹配。
- `keyword` 传入时建议模糊匹配 `analysis_task.task_no` 和 `medical_case.case_no`。

错误建议：

```text
pageNo/pageSize 非法：后端可归一化处理，不必直接报错
status/taskType 不是合法枚举：40000
未登录或 token 失效：40100 或 HTTP 401
```

容易误解点：

- `caseNo` 和 `keyword` 都可能和病例编号有关，但用途不同：`caseNo` 是结构化精确筛选，`keyword` 是搜索框模糊搜索。
- 不要把 `caseNo` 当成必须字段；当前前端任务列表页不传它。
- 不要只因为存在 `caseNo` 参数，就让 `keyword` 只能搜索任务编号；契约要求 `keyword` 仍可搜索任务编号或病例编号。

### 7.2 创建分析任务

```text
POST /analysis-tasks
```

权限：需要 token。

请求体：

```ts
export interface CreateTaskRequest {
  caseId: number
  imageFileId: number
  taskType: 'VESSEL_SEGMENTATION' | 'IMAGE_QUALITY_CHECK'
  priority: number
}
```

字段规则：

| 字段 | 类型 | 必填 | 规则 |
|---|---|---|---|
| `caseId` | number | 是 | 病例 ID |
| `imageFileId` | number | 是 | 图像 ID，必须属于该病例 |
| `taskType` | enum | 是 | `VESSEL_SEGMENTATION` 或 `IMAGE_QUALITY_CHECK` |
| `priority` | number | 是 | 1-9，建议 1 最高、9 最低 |

响应：

```ts
export interface CreateTaskResponse {
  id: number
  taskNo: string
  status: 'WAITING'
  message: string
}
```

返回类型：

```ts
ApiResponse<CreateTaskResponse>
```

成功示例：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "id": 1001,
    "taskNo": "T202606010001",
    "status": "WAITING",
    "message": "任务已提交，等待处理"
  }
}
```

后端规则：

- 校验病例存在且未删除
- 校验图像存在且属于病例
- 校验图像未删除
- 创建任务和任务日志
- 投递 MQ
- 若重复提交或状态冲突，返回 `40900`
- 若 MQ 投递失败，返回 `50002`

当前约定：

- 创建接口响应和数据库状态均为 `WAITING`。
- 创建成功前必须完成 RabbitMQ 投递确认；投递失败返回 `50002`。
- 创建任务时写入日志：`fromStatus = null`、`toStatus = WAITING`、`operatorType = USER`。

### 7.3 查询任务详情

```text
GET /analysis-tasks/{taskId}
```

权限：需要 token。

Path 参数：

| 参数 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `taskId` | number | 是 | 任务 ID |

响应：

```ts
export interface TaskDetail extends TaskListItem {
  imagePreviewUrl: string
}
```

返回类型：

```ts
ApiResponse<TaskDetail>
```

成功示例：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "id": 1001,
    "taskNo": "T202606100001",
    "caseId": 101,
    "caseNo": "C202606100001",
    "imageFileId": 201,
    "originalFilename": "fundus_left.jpg",
    "taskType": "VESSEL_SEGMENTATION",
    "status": "RUNNING",
    "priority": 5,
    "retryCount": 0,
    "maxRetryCount": 3,
    "errorMessage": null,
    "submittedBy": 1,
    "submittedByName": "张医生",
    "submittedAt": "2026-06-10 11:05:00",
    "startedAt": "2026-06-10 11:06:00",
    "finishedAt": null,
    "updatedAt": "2026-06-10 11:06:00",
    "imagePreviewUrl": "/api/images/201/preview"
  }
}
```

说明：

- 该接口只返回任务详情本身和原图预览地址。
- 分析结果请通过 `GET /analysis-tasks/{taskId}/result` 获取。
- 任务日志请通过 `GET /analysis-tasks/{taskId}/logs` 获取。
- `taskId` 不存在时返回 `40400`。

### 7.4 取消任务

```text
POST /analysis-tasks/{taskId}/cancel
```

权限：需要 token。

Path 参数：

| 参数 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `taskId` | number | 是 | 任务 ID |

响应：

```ts
ApiResponse<boolean>
```

成功示例：

```json
{
  "code": 0,
  "message": "success",
  "data": true
}
```

规则：

- 仅 `CREATED`、`WAITING` 可取消
- 取消成功后状态为 `CANCELED`
- 状态不允许取消时返回 `40900`
- 需要写入任务日志
- 如果任务已经被 Worker 取走并进入 `RUNNING`，当前不允许取消，返回 `40900`。
- 如果需要支持强制取消运行中任务，应设计 Worker 协作取消机制，不要仅改数据库状态。
- 重复取消已 `CANCELED` 的任务返回 `40900`。
- 该接口不需要请求体，`taskId` 从路径中获取。

### 7.5 重试任务

```text
POST /analysis-tasks/{taskId}/retry
```

权限：需要 token。

Path 参数：

| 参数 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `taskId` | number | 是 | 任务 ID |

响应：

```ts
export interface RetryTaskResponse {
  id: number
  taskNo: string
  status: 'WAITING' | 'RETRYING'
  retryCount: number
}
```

返回类型：

```ts
ApiResponse<RetryTaskResponse>
```

成功示例：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "id": 1001,
    "taskNo": "T202606100001",
    "status": "RETRYING",
    "retryCount": 1
  }
}
```

规则：

- 仅 `FAILED` 可手动重试
- `retryCount` 不能超过 `maxRetryCount`
- 重试成功后状态为 `RETRYING` 或 `WAITING`
- 需要重新投递 MQ
- 需要写入任务日志
- 状态冲突返回 `40900`
- 如果原任务关联的病例或图像已删除，建议返回 `40900`，不允许重试。
- 如果 MQ 投递失败，返回 `50002`，任务状态由事务策略回滚或保持 `FAILED`，但必须写入可追踪的失败信息。
- 该接口不需要请求体，`taskId` 从路径中获取。
- 当前重试成功后直接返回 `WAITING`，表示任务已重新排队；若后续使用 `RETRYING`，必须尽快流转到 `WAITING` 或 `RUNNING`，避免状态停滞。

## 8. Analysis Result 分析结果模块

### 8.1 查询任务结果

```text
GET /analysis-tasks/{taskId}/result
```

权限：需要 token。

Path 参数：

| 参数 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `taskId` | number | 是 | 任务 ID |

响应：

```ts
export interface AnalysisResult {
  id: number
  taskId: number
  resultType: 'VESSEL_SEGMENTATION' | 'IMAGE_QUALITY_CHECK'
  resultJson: VesselSegmentationResult | ImageQualityResult | Record<string, unknown>
  maskPreviewUrl: string | null
  reportDownloadUrl: string | null
  modelName: string
  modelVersion: string
  processingTimeMs: number
  createdAt: string
  updatedAt: string
}
```

返回类型：

```ts
ApiResponse<AnalysisResult>
```

规则：

- 通常只有 `SUCCESS` 任务存在结果
- 结果不存在返回 `40400`
- `resultJson` 可以是动态 JSON，但必须是对象

### 8.2 血管分割结果 JSON

当 `resultType = VESSEL_SEGMENTATION`：

```ts
export interface VesselSegmentationResult {
  vesselAreaRatio: number
  imageQualityScore?: number | null
  processingTimeMs: number
  modelVersion: string
  conclusion: string
}
```

示例：

```json
{
  "vesselAreaRatio": 0.143,
  "imageQualityScore": null,
  "processingTimeMs": 3280,
  "modelVersion": "v1.0.0",
  "conclusion": "血管结构清晰，图像质量良好"
}
```

说明：`imageQualityScore` 仅在独立图像质量模型提供可信评分时返回；当前血管分割模型不生成该指标，因此返回 `null` 或省略，前端不展示伪造分数。任务详情与结果详情中的图像质量状态/评分应优先读取 `AnalysisResult.qualitySummary`，没有时回退 `TaskDetail.qualitySummary`；该摘要来自图像质量检测任务投影，不要求血管分割 `resultJson` 内重复保存。

### 8.3 图像质量检测结果 JSON

当 `resultType = IMAGE_QUALITY_CHECK`：

```ts
export interface ImageQualityResult {
  qualityScore: number
  blurScore: number
  brightnessScore: number
  contrastScore: number
  conclusion: string
}
```

示例：

```json
{
  "qualityScore": 0.91,
  "blurScore": 0.12,
  "brightnessScore": 0.86,
  "contrastScore": 0.88,
  "conclusion": "图像质量良好"
}
```

### 8.4 预览分割结果图

```text
GET /results/{resultId}/mask
```

权限：建议需要 token。

Path 参数：

| 参数 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `resultId` | number | 是 | 分析结果 ID |

响应：

```text
图片二进制流
```

说明：

- 若 `AnalysisResult.maskPreviewUrl` 不为空，前端会优先使用该 URL
- 若为空，前端使用 `/results/{resultId}/mask`

### 8.5 下载分析报告

```text
GET /results/{resultId}/report
```

权限：建议需要 token。

Path 参数：

| 参数 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `resultId` | number | 是 | 分析结果 ID |

响应：

```text
PDF 文件流
```

建议响应头：

```text
Content-Disposition: attachment; filename="report-1001.pdf"
Content-Type: application/pdf
```

说明：

- 只有医生审核通过并签发后，前端才显示报告下载入口。
- `/results/{resultId}/report` 只返回最新 `SIGNED`/`SUPERSEDED` 报告，不回退 `analysis_result` 旧报告字段。
- 未审核、未批准、只有草稿或报告文件不存在时，返回明确 409/404。

## 9. Task Log 任务日志模块

### 9.1 查询任务日志

```text
GET /analysis-tasks/{taskId}/logs
```

权限：需要 token。

Path 参数：

| 参数 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `taskId` | number | 是 | 任务 ID |

响应：

```ts
export interface TaskLogItem {
  id: number
  taskId: number
  fromStatus: string | null
  toStatus: string
  message: string
  operatorType: 'USER' | 'SYSTEM' | 'WORKER' | 'ADMIN'
  createdAt: string
}
```

返回类型：

```ts
ApiResponse<TaskLogItem[]>
```

说明：任务日志既包含 AI 推理主任务状态流转，也可以包含 AI 草稿生成、医生审核保存、PDF 签发等临床闭环审计记录。临床闭环记录不代表任务重新流转，`fromStatus` 与 `toStatus` 可以相同，前端应按流程记录展示，任务真实状态仍以 `analysis_task.status` 为准。

示例：

```json
{
  "code": 0,
  "message": "success",
  "data": [
    {
      "id": 1,
      "taskId": 1001,
      "fromStatus": null,
      "toStatus": "WAITING",
      "message": "任务已提交，等待 Worker 处理",
      "operatorType": "USER",
      "createdAt": "2026-06-01 10:20:00"
    }
  ]
}
```

说明：

- 创建任务、开始处理、处理成功、处理失败、取消、重试都建议写日志
- 日志接口失败不应影响任务详情接口

## 10. Dashboard 统计模块

### 10.1 查询任务统计

```text
GET /admin/statistics/tasks
```

权限：需要 token。当前允许登录用户访问；后续可收紧为 `ADMIN` 或明确授权用户。

响应：

```ts
export interface TaskStatistics {
  todaySubmittedCount: number
  todaySuccessCount: number
  todayFailedCount: number
  waitingCount: number
  runningCount: number
  totalTaskCount: number
  successRate: number
  averageProcessingTimeMs: number
}
```

返回类型：

```ts
ApiResponse<TaskStatistics>
```

字段说明：

| 字段 | 说明 |
|---|---|
| `todaySubmittedCount` | 今日提交任务数 |
| `todaySuccessCount` | 今日成功任务数 |
| `todayFailedCount` | 今日失败任务数 |
| `waitingCount` | 当前排队中任务数 |
| `runningCount` | 当前运行中任务数 |
| `totalTaskCount` | 总任务数 |
| `successRate` | 成功率，0-1 小数，例如 0.93 |
| `averageProcessingTimeMs` | 平均处理耗时，毫秒 |

### 10.2 查询队列统计

```text
GET /admin/statistics/queue
```

权限：需要 token。当前允许登录用户访问；后续可收紧为 `ADMIN` 或明确授权用户。

响应：

```ts
export interface QueueStatistics {
  queueName: string
  messageReadyCount: number
  messageUnackedCount: number
  consumerCount: number
  deadLetterCount: number
}
```

返回类型：

```ts
ApiResponse<QueueStatistics>
```

字段说明：

| 字段 | 说明 |
|---|---|
| `queueName` | 队列名称 |
| `messageReadyCount` | 待消费消息数 |
| `messageUnackedCount` | 已投递但未确认消息数 |
| `consumerCount` | 消费者数量 |
| `deadLetterCount` | 死信数量 |

说明：

- 该接口建议从 RabbitMQ 管理接口或队列运行态读取
- 不要求从业务数据库读取

### 10.3 查询任务趋势

```text
GET /admin/statistics/task-trend
```

权限：需要 token。

Query 参数：

| 参数 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `days` | number | 是 | 最近 N 天，前端当前传 `7` |

响应：

```ts
export interface TaskTrendItem {
  date: string
  submittedCount: number
  successCount: number
  failedCount: number
}
```

返回类型：

```ts
ApiResponse<TaskTrendItem[]>
```

示例：

```json
{
  "code": 0,
  "message": "success",
  "data": [
    {
      "date": "2026-05-26",
      "submittedCount": 20,
      "successCount": 18,
      "failedCount": 2
    }
  ]
}
```

说明：

- `date` 使用 `yyyy-MM-dd`
- 前端会根据 `successCount / submittedCount` 自行计算每日成功率
- 若某天无数据，后端可返回 0 值记录，也可不返回，由后端决定；建议返回连续日期，便于展示

### 10.4 查询系统运行状态

```text
GET /system/status
```

权限：需要 token，所有已登录角色均可访问。

返回类型：`ApiResponse<SystemStatus>`。

```ts
export type ComponentStatus = 'UP' | 'DEGRADED' | 'DOWN'

export interface SystemStatus {
  overallStatus: ComponentStatus
  checkedAt: string
  ai: {
    status: ComponentStatus
    reachable: boolean
    ready: boolean
    modelLoaded: boolean
    modelName: string | null
    modelVersion: string | null
    device: string | null
    busy: boolean
    startedAt: string | null
    totalRequests: number | null
    successCount: number | null
    failureCount: number | null
    lastInferenceTimeMs: number | null
    lastSuccessAt: string | null
    lastError: string | null
  }
  queue: {
    status: ComponentStatus
    error: string | null
    queueName: string | null
    messageReadyCount: number | null
    messageUnackedCount: number | null
    consumerCount: number | null
    deadLetterCount: number | null
  }
  tasks: TaskStatistics & {
    retryingCount: number
  }
}
```

AI 或 RabbitMQ 不可用时，接口仍可返回 HTTP 200，并将对应组件和 `overallStatus` 标记为 `DOWN`/`DEGRADED`。该接口不得暴露权重绝对路径、堆栈和环境变量。

## 11. 路由与接口对应关系

| 前端页面 | 前端路由 | 主要接口 |
|---|---|---|
| 注册页 | `/register` | `POST /auth/register` |
| 登录页 | `/login` | `POST /auth/login`, `GET /auth/me` |
| 统计看板 | `/dashboard` | `GET /admin/statistics/tasks`, `GET /admin/statistics/queue`, `GET /admin/statistics/task-trend`, `GET /system/status` |
| 病例管理 | `/cases` | `GET /cases`, `POST /cases`, `GET /cases/{caseId}`, `PUT /cases/{caseId}`, `DELETE /cases/{caseId}` |
| 图像管理 | `/cases/:caseId/images` | `GET /cases/{caseId}`, `GET /cases/{caseId}/images`, `POST /cases/{caseId}/images`, `DELETE /images/{imageId}` |
| 创建任务 | `/tasks/create` | `GET /cases`, `GET /cases/{caseId}`, `GET /cases/{caseId}/images`, `POST /analysis-tasks` |
| 任务列表 | `/tasks` | `GET /analysis-tasks`, `POST /analysis-tasks/{taskId}/retry`, `POST /analysis-tasks/{taskId}/cancel` |
| 任务详情 | `/tasks/:taskId` | `GET /analysis-tasks/{taskId}`, `GET /analysis-tasks/{taskId}/result`, `GET /analysis-tasks/{taskId}/logs`, `GET /results/{resultId}/mask`, `GET /results/{resultId}/report` |
| 医疗知识助手 | `/knowledge` | `POST /knowledge/chat`, `GET /knowledge/chat/sessions`, `GET /knowledge/chat/sessions/{sessionId}/messages` |
| AI 质控 | `/quality-control` | `GET /quality-control/overview`, `GET /quality-control/model-performance`, `GET /quality-control/review-statistics`, `GET /quality-control/risk-alerts` |
| Prompt 管理 | `/admin/prompts` | `GET /prompt-templates`, `GET /prompt-templates/{templateCode}/versions`, `PUT /prompt-templates/{templateCode}/active-version`, `GET /llm-call-logs` |
| Prompt 评测 | `/prompt-evaluations` | `POST /prompt-evaluations/runs`, `GET /prompt-evaluations/runs`, `GET /prompt-evaluations/runs/{id}`, `PUT /prompt-evaluations/runs/{id}/review` |
| RAG 评测 | `/rag-evaluations` | `POST /rag-evaluations/runs`, `GET /rag-evaluations/runs`, `GET /rag-evaluations/runs/{id}`, `PUT /rag-evaluations/runs/{id}/review` |
| 只读智能助手 | `/agent` | `POST /agent/sessions`, `GET /agent/sessions`, `GET /agent/sessions/{sessionId}/messages`, `POST /agent/chat` |

## 12. 当前公共接口清单

```text
POST /auth/register
POST /auth/login
GET  /auth/me
POST /auth/logout

GET    /cases
POST   /cases
GET    /cases/{caseId}
PUT    /cases/{caseId}
DELETE /cases/{caseId}

GET    /cases/{caseId}/images
POST   /cases/{caseId}/images
GET    /images/{imageId}/preview
DELETE /images/{imageId}

GET  /analysis-tasks
POST /analysis-tasks
GET  /analysis-tasks/{taskId}
POST /analysis-tasks/{taskId}/cancel
POST /analysis-tasks/{taskId}/retry
GET  /analysis-tasks/{taskId}/result
GET  /analysis-tasks/{taskId}/logs
GET  /results/{resultId}/mask
GET  /results/{resultId}/report
GET  /analysis-results/{resultId}/report-draft
PUT  /analysis-results/{resultId}/report-draft
POST /analysis-results/{resultId}/report-draft/ai-generate
POST /analysis-results/{resultId}/report-sign

GET /admin/statistics/tasks
GET /admin/statistics/queue
GET /admin/statistics/task-trend?days=7

POST /knowledge/documents
GET  /knowledge/documents
POST /knowledge/documents/{id}/reindex
PUT  /knowledge/documents/{id}/status
DELETE /knowledge/documents/{id}
POST /knowledge/chat/sessions
GET  /knowledge/chat/sessions
GET  /knowledge/chat/sessions/{sessionId}/messages
POST /knowledge/chat

GET /prompt-templates
GET /prompt-templates/{templateCode}/versions
PUT /prompt-templates/{templateCode}/active-version
GET /llm-call-logs
POST /prompt-evaluations/runs
GET /prompt-evaluations/runs
GET /prompt-evaluations/runs/{id}
PUT /prompt-evaluations/runs/{id}/review
POST /rag-evaluations/runs
GET /rag-evaluations/runs
GET /rag-evaluations/runs/{id}
PUT /rag-evaluations/runs/{id}/review
```

## 13. 文件访问规则

### 13.1 图像预览

图像预览有两种可选实现：

1. 后端返回可直接访问的 `previewUrl`
2. 前端使用 `/images/{imageId}/preview`

如果图片接口需要 token，后端应支持带 `Authorization` 请求访问。

### 13.2 分割结果图

分割结果图有两种可选实现：

1. `AnalysisResult.maskPreviewUrl`
2. `/results/{resultId}/mask`

### 13.3 报告下载

报告下载使用后端签发报告接口：

```text
GET /results/{resultId}/report
GET /analysis-results/{resultId}/reports/{version}
```

报告只返回医生签发后的 PDF 文件流：

- PDF

`AnalysisResult.reportDownloadUrl` 是历史兼容字段，当前前端不应把它作为正式报告可下载的依据。报告入口应来自 `/analysis-results/{resultId}/reports` 中的 `SIGNED/SUPERSEDED` 版本。

## 14. 任务状态流转

当前主流程：

```text
WAITING -> RUNNING -> SUCCESS
WAITING -> RUNNING -> FAILED
FAILED -> WAITING -> RUNNING -> SUCCESS / FAILED
WAITING -> CANCELED
```

说明：

- 创建任务成功后直接返回并持久化为 `WAITING`。
- Java 后端进程内的 RabbitMQ listener 消费任务，将 `WAITING` 或 `RETRYING` 更新为 `RUNNING`。
- AI 调用、结果下载和持久化全部成功后更新为 `SUCCESS`；任一环节失败则更新为 `FAILED`。
- 当前手动重试会把 `FAILED` 直接更新为 `WAITING` 并重新投递 MQ；`RETRYING` 枚举保留给兼容或后续扩展。
- 仅 `CREATED`、`WAITING` 可取消；当前正常创建流程不会长期停留在 `CREATED`。
- 仅 `FAILED` 可重试
- `SUCCESS`、`CANCELED` 为终态
- 创建、开始执行、成功、失败、取消和重试都必须写入任务日志
- MQ 消费成功或忽略时 ACK；可恢复状态才重新入队；确定失败时不重新入队并进入死信队列

## 15. 前后端联调注意事项

1. 后端字段命名必须使用前端契约中的 camelCase，例如 `caseNo`、`createdByName`、`imagePreviewUrl`。
2. 后端数据库字段可以使用 snake_case，但返回 JSON 必须转换为 camelCase。
3. 所有 JSON 业务接口都必须套 `ApiResponse<T>`。
4. 分页接口必须返回 `PageResult<T>`。
5. `resultJson` 必须是 JSON object，不要返回字符串化 JSON。
6. 文件上传字段名必须是 `file`。
7. 注册接口不允许创建 `ADMIN`。
8. 密码不允许明文存储，也不允许在任何响应中返回。
9. token 不允许放在 URL 中。
10. 如果接口暂未实现，请返回明确错误码，不要返回 HTML 错误页。

## 16. 核心医疗闭环接口（v1.5）

### 16.1 管理员用户与医生身份

公开注册始终创建 `USER`，不能由浏览器注册为医生或管理员。管理员可维护角色：

```text
GET /admin/users
PUT /admin/users/{userId}/role
```

`GET /admin/users` 返回：

```ts
interface AdminUserItem {
  id: number
  username: string
  realName: string
  roleCode: 'USER' | 'DOCTOR' | 'RESEARCHER' | 'ADMIN'
  professionalNo: string | null
  roleAssignedBy: number | null
  roleAssignedAt: string | null
  status: number
}
```

`PUT /admin/users/{userId}/role` 请求：

```json
{
  "roleCode": "DOCTOR",
  "professionalNo": "DOC-2026-001"
}
```

授予 `DOCTOR` 时必须提交唯一 `professionalNo`。角色变化后，旧 JWT 不立即获得新权限，用户需要重新登录。

### 16.2 图像质量检测与医生决策参考

图像响应增加质量字段：

```ts
interface ImageFileItem {
  qualityStatus: 'NOT_CHECKED' | 'CHECKING' | 'PASS' | 'WARNING' | 'FAIL' | 'ERROR'
  qualityScore: number | null
  qualityResultId: number | null
  qualityCheckedAt: string | null
}
```

上传成功后，后端会自动创建 `IMAGE_QUALITY_CHECK` 任务。也可以幂等触发重检：

```text
POST /images/{imageId}/quality-check
```

规则：

- `NOT_CHECKED` / `ERROR`：创建新的质量检测任务并更新 `qualityTaskId`。
- `CHECKING`：返回当前图像状态，不重复投递。
- `PASS` / `WARNING`：不重复检测，负责医生可继续创建血管分割任务。
- `FAIL`：保留状态与评分供负责医生判断，不阻止创建血管分割任务。
- `NOT_CHECKED` / `CHECKING` / `ERROR`：暂无有效评分时也不作为硬门控，前端需明确提示风险并由医生确认是否继续。

创建血管分割任务时支持：

```ts
interface CreateTaskRequest {
  caseId: number
  imageFileId: number
  taskType: 'VESSEL_SEGMENTATION' | 'IMAGE_QUALITY_CHECK'
  priority: number
  /** @deprecated 仅兼容历史客户端，后端不再据此放行或拒绝任务。 */
  qualityOverride?: boolean
  /** @deprecated 仅兼容历史客户端。 */
  qualityOverrideReason?: string
}
```

`VESSEL_SEGMENTATION` 任务仍仅允许负责医生创建。图像质量状态和评分是工程性参考信息，不再触发任务创建冲突；前端对未通过或无有效评分的图像进行二次确认，后端在任务日志中记录创建时的质量状态和评分。旧 `qualityOverride` 字段仅保留兼容，后端忽略其值。

### 16.3 人工反馈

```text
GET  /analysis-results/{resultId}/feedback
POST /analysis-results/{resultId}/feedback
```

请求：

```json
{
  "verdict": "ACCEPTED",
  "issueCodes": [],
  "comment": "结果可接受"
}
```

响应项：

```ts
interface Feedback {
  id: number
  verdict: 'ACCEPTED' | 'PARTIAL' | 'INCORRECT'
  issueCodes: string
  comment: string
  submittedBy: number
  createdAt: string
}
```

反馈为追加写，不覆盖 AI 原始结果。

### 16.4 修正版本

```text
GET  /analysis-results/{resultId}/corrections
GET  /analysis-results/{resultId}/corrections/{version}
POST /analysis-results/{resultId}/corrections
```

上传修正使用 `multipart/form-data`：

| 字段 | 类型 | 说明 |
|---|---|---|
| `file` | file | PNG、JPEG 或 TIFF 图片 |
| `reason` | string | 修正原因 |
| `correctedResultJson` | string | 合法 JSON |
| `expectedVersion` | number | 当前已知最新版本，冲突返回 409 |

后端会实际解码图片、校验像素数量和尺寸，并将修正 mask 二值化后统一保存为 PNG。AI 原始 mask 不会被覆盖。

响应项：

```ts
interface Correction {
  id: number
  version: number
  correctedResultJson: string
  correctedMaskObjectKey: string
  reason: string
  status: 'DRAFT' | 'SUBMITTED' | 'ACCEPTED' | 'REJECTED'
  submittedBy: number
  createdAt: string
  updatedAt: string
}
```

修正上传仅 `RESEARCHER` 和 `DOCTOR` 可用。

### 16.5 医生审核

```text
GET  /analysis-results/{resultId}/review
POST /analysis-results/{resultId}/review
GET  /doctor/reviews
GET  /doctor/reviews/{resultId}
```

`GET/POST /analysis-results/{resultId}/review` 仅 `DOCTOR` 可用。提交审核：

```json
{
  "correctionVersion": null,
  "status": "APPROVED",
  "findings": "血管分割边界整体清晰",
  "conclusion": "可作为辅助参考",
  "recommendation": "建议结合眼底检查复核",
  "expectedVersion": 1
}
```

`correctionVersion = null` 表示采用 AI 原始结果；非空时必须是接口返回的真实修正版本。并发版本冲突返回 HTTP 409。
当 `status = APPROVED` 且后续需要签发 PDF 时，`findings`、`conclusion`、`recommendation` 必须由医生保存为非空内容。正式 PDF 的医生所见、审核结论和处理建议只读取该审核记录，不从 AI 草稿回退填充。

响应：

```ts
interface Review {
  correctionVersion: number | null
  status: 'PENDING' | 'CHANGES_REQUESTED' | 'APPROVED' | 'REJECTED'
  findings: string
  conclusion: string
  recommendation: string
  reviewerNameSnapshot: string
  professionalNoSnapshot: string
  version: number
  reviewedAt: string | null
}
```

`/doctor/reviews` 是医生审核工作台列表，复用任务列表查询和分页结构；`/doctor/reviews/{resultId}` 校验医生可访问后返回对应任务/结果上下文。

### 16.6 报告草稿、签发与下载

```text
GET  /analysis-results/{resultId}/report-draft
PUT  /analysis-results/{resultId}/report-draft
POST /analysis-results/{resultId}/report-draft/ai-generate
POST /analysis-results/{resultId}/report-sign
GET  /analysis-results/{resultId}/reports
GET  /analysis-results/{resultId}/reports/{version}
GET  /results/{resultId}/report
```

`report-draft`、`report-draft/ai-generate`、`report-sign` 仅 `DOCTOR` 可用。草稿请求：

```json
{
  "correctionVersion": null,
  "draftJson": "{\"findings\":\"...\",\"conclusion\":\"...\",\"recommendation\":\"...\"}"
}
```

AI 生成草稿：

```text
POST /analysis-results/{resultId}/report-draft/ai-generate
```

该接口由 Java 后端调用配置的大模型供应商（Qwen 或 DeepSeek 的 OpenAI-compatible Chat Completions API），浏览器不直接访问大模型。后端只发送已持久化的结构化 AI 结果、图像质量摘要和必要上下文，不发送原始眼底图、mask 图片、token、文件绝对路径或患者真实身份。成功后覆盖当前 `DRAFT` 的 `draftJson` 并返回 `Report`，其中 `draftJson` 至少包含：

```json
{
  "findings": "...",
  "conclusion": "...",
  "recommendation": "...",
  "explanation": "...",
  "disclaimer": "AI辅助分析，不等同于独立医学诊断。"
}
```

AI 草稿仅供医生审核前参考，不代表审核通过、自动诊断或正式报告签发。医生可以将草稿带入审核表单后人工修改并保存，正式 PDF 只采用医生审核记录中的所见、结论和建议。大模型调用失败、返回空内容或非 JSON 时返回业务错误，后端不得覆盖已有草稿。
AI 草稿的 `findings`、`conclusion`、`recommendation` 不得包含“诊断为”“确诊”“排除”“明确患有”“无需复查”等确定性诊断措辞；后端命中高风险措辞时返回业务错误，并且不得覆盖已有 `draftJson`。

报告响应：

```ts
interface Report {
  version: number
  status: 'DRAFT' | 'SIGNED' | 'SUPERSEDED'
  correctionVersion: number | null
  draftJson: string
  reportSha256: string | null
  signerNameSnapshot: string | null
  professionalNoSnapshot: string | null
  signedAt: string | null
  createdAt: string
  updatedAt: string
}
```

签发规则：

- 只有 `APPROVED` 审核结果可签发。
- `APPROVED` 审核结果必须包含医生所见、审核结论和处理建议；否则签发返回业务错误。
- 签发 PDF 的正式医学意见只来自医生审核记录；AI 草稿保留为参考和审计元数据，不冒充医生结论。

## 18. 病例级随访趋势与结果对比

本组接口用于把同一病例下的图像质量、血管分割结果、医生审核和 PDF 签发状态串成纵向视图。趋势分析和对比结论仅供医生复核参考，不构成自动诊断、治疗建议或报告签发依据。

```text
GET  /cases/{caseId}/analysis-timeline
GET  /analysis-results/compare?baselineResultId={id}&targetResultId={id}
POST /cases/{caseId}/trend-summary/ai-generate
```

`GET /cases/{caseId}/analysis-timeline` 支持查询参数：

| 参数 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `eyeSide` | `LEFT` / `RIGHT` / `BOTH` | 否 | 与病例眼别不一致时返回空列表 |
| `taskType` | `VESSEL_SEGMENTATION` / `IMAGE_QUALITY_CHECK` | 否 | 第一版前端主要使用血管分割 |
| `startTime` | ISO date-time | 否 | 按任务事件时间过滤 |
| `endTime` | ISO date-time | 否 | 按任务事件时间过滤 |

响应：

```ts
interface CaseAnalysisTimeline {
  caseId: number
  eyeSide: 'LEFT' | 'RIGHT' | 'BOTH'
  items: Array<{
    taskId: number
    taskNo: string | null
    taskType: 'VESSEL_SEGMENTATION' | 'IMAGE_QUALITY_CHECK'
    taskStatus: string
    imageFileId: number
    resultId: number | null
    qualityScore: number | null
    qualityStatus: string | null
    vesselAreaRatio: number | null
    modelName: string | null
    modelVersion: string | null
    reviewStatus: string | null
    reportStatus: string | null
    finishedAt: string | null
    resultCreatedAt: string | null
  }>
}
```

`GET /analysis-results/compare` 只允许比较同一病例、同一眼别、同类任务的结果；不满足时返回业务参数错误。缺失指标以 `null` 返回，不伪造差值。

`POST /cases/{caseId}/trend-summary/ai-generate` 仅把结构化时间线数据发送给已配置的大模型，不发送原图、mask、患者真实身份、token 或文件绝对路径。生成内容必须使用辅助性语气；命中“确诊、排除、无需复查”等高风险措辞时返回业务错误。

## 19. AI 质控与模型效果评估

AI 质控接口仅供管理员进行系统技术治理，统计 AI 推理、图像质量、医生审核反馈、模型版本表现和高风险结果。不构成自动诊断、治疗建议或报告签发依据。

```text
GET /quality-control/overview
GET /quality-control/model-performance
GET /quality-control/review-statistics
GET /quality-control/risk-alerts
```

权限：仅 `DOCTOR`、`ADMIN` 可访问；普通 `USER`、`RESEARCHER` 默认不可访问。

响应结构：

```ts
interface QualityOverview {
  taskTotalCount: number
  successRate: number
  failedRate: number
  averageProcessingTimeMs: number
  retryTaskCount: number
  qualityDistribution: Record<string, number>
  reviewDistribution: Record<string, number>
  signedReportCount: number
  approvedUnsignedCount: number
  vesselResultCount: number
  averageVesselAreaRatio: number
  abnormalLowVesselRatioCount: number
  abnormalHighVesselRatioCount: number
}

interface ModelPerformanceItem {
  modelName: string
  modelVersion: string
  resultType: string
  resultCount: number
  successRate: number
  averageProcessingTimeMs: number
  averageVesselAreaRatio: number
  reviewApprovedRate: number
  reviewNeedsChangeRate: number
  failedRate: number
}

interface ReviewStatistics {
  totalReviewCount: number
  reviewDistribution: Record<string, number>
  approvedRate: number
  needsChangeRate: number
  rejectedRate: number
  signedReportCount: number
  approvedUnsignedCount: number
}

interface RiskAlertItem {
  level: 'CRITICAL' | 'WARNING' | 'INFO'
  reason: string
  taskId: number | null
  taskNo: string | null
  resultId: number | null
  caseId: number | null
  taskType: string | null
  status: string | null
  createdAt: string | null
}
```

风险提醒第一版包含：质量 `FAIL/ERROR` 后仍血管分割、血管面积比例缺失或异常、医生审核需修改/拒绝、多次重试或最终失败、已审核通过但尚未签发 PDF。阈值为后端固定默认值，后续可升级为管理员配置。

## 20. Knowledge RAG 医疗知识助手

医疗知识助手面向所有已登录用户，用于资料检索、概念解释和辅助理解，不构成诊断、治疗建议或报告签发依据。浏览器只访问 Java 后端；Java 后端负责调用 Qwen Embedding、Qdrant 和现有 Chat Completions。

管理接口仅 `ADMIN` 可用：

```text
POST /knowledge/documents
GET  /knowledge/documents
POST /knowledge/documents/{id}/reindex
PUT  /knowledge/documents/{id}/status
DELETE /knowledge/documents/{id}
```

文档创建请求：

```ts
interface KnowledgeDocumentCreateRequest {
  title: string
  source: string
  category: 'MEDICAL_BASE' | 'AI_RESULT_EXPLANATION' | 'WORKFLOW' | 'FAQ' | 'SAFETY'
  audience: 'PUBLIC' | 'PATIENT' | 'CLINICAL' | 'RESEARCH' | 'ADMIN'
  content: string
}
```

文档响应：

```ts
interface KnowledgeDocument {
  id: number
  title: string
  source: string
  category: string
  audience: 'PUBLIC' | 'PATIENT' | 'CLINICAL' | 'RESEARCH' | 'ADMIN'
  status: 'INDEXING' | 'ACTIVE' | 'DISABLED' | 'FAILED'
  version: number
  chunkCount: number
  failureReason: string | null
  lastIndexedAt: string | null
  createdAt: string
  updatedAt: string
}
```

状态更新请求：

```ts
interface KnowledgeDocumentStatusRequest {
  status: 'ACTIVE' | 'DISABLED'
}
```

聊天接口所有已登录用户可用。检索结果先按角色受众过滤：所有角色可读 `PUBLIC`，患者读 `PATIENT`，医生读 `CLINICAL`，研究员读 `RESEARCH`，管理员读 `ADMIN`；不匹配受众的 chunk 不进入 LLM 上下文：

```text
POST /knowledge/chat/sessions
GET  /knowledge/chat/sessions
GET  /knowledge/chat/sessions/{sessionId}/messages
POST /knowledge/chat
```

聊天请求：

```ts
interface KnowledgeChatRequest {
  sessionId: number | null
  question: string
}
```

聊天响应：

```ts
interface KnowledgeChatResponse {
  sessionId: number
  answer: string
  citations: Array<{
    documentId: number
    documentTitle: string
    chunkId: number
    source: string
    snippet: string
    score: number
  }>
  disclaimer: '医疗知识助手仅供资料检索和理解参考，不构成诊断、治疗建议或报告签发依据。'
}
```

约束：
- RAG 回答必须基于 `ACTIVE` 文档的检索片段；检索为空或低于相似度阈值时返回“知识库未检索到足够依据”。
- `citations` 只包含模型明确指认且通过后端核验的证据：`chunkId` 必须属于本次检索结果，`snippet` 是该 chunk 原文中的连续摘录。模型未给证据、引用未知 chunk 或摘录不符时，返回固定“依据不足”回答和空 `citations`，不展示未经核验的模型回答。逐字核验只证明引用来源，不能证明医学论断成立。
- `reindex` 必须基于保存的原始知识正文重建 chunk 与 Qdrant points；失败时文档状态为 `FAILED` 并返回可读错误。
- `DISABLED` 文档不参与检索；删除文档时必须同步删除 MySQL chunk 与 Qdrant points。
- 不发送患者真实身份、token、文件绝对路径、原始眼底图或 mask 图片到 RAG prompt。
- 上传内容不得包含 token、API Key、文件绝对路径、患者身份字段或原始图像路径等敏感信息。
- 回答不得包含“诊断为”“确诊”“排除”“明确患有”“无需复查”“无需就医”“直接用药”等确定性诊断或处置措辞。
- `GET /analysis-results/{resultId}/reports/{version}` 和 `GET /results/{resultId}/report` 只返回 `SIGNED/SUPERSEDED` PDF 二进制流。
- 未审核、未批准、只有草稿或文件不存在时，返回明确 409/404，不回退 `analysis_result` 旧字段。

## 21. Prompt Engineering 管理

Prompt 管理接口仅 `ADMIN` 可访问。第一版用于只读查看模板与历史版本、切换已经发布的启用版本，以及查看脱敏调用审计；不提供在线新增或修改 Prompt 文本的接口。

```text
GET /prompt-templates
GET /prompt-templates/{templateCode}/versions
PUT /prompt-templates/{templateCode}/active-version
GET /llm-call-logs?scenario=&templateCode=&success=&startTime=&endTime=
```

模板列表响应：

```ts
interface PromptTemplate {
  templateCode: 'REPORT_DRAFT_GENERATION' | 'RAG_KNOWLEDGE_CHAT' | 'CASE_TREND_SUMMARY' | 'CLINICAL_ASSISTANT_AGENT'
  name: string
  scenario: string
  description: string | null
  status: 'ACTIVE' | 'DISABLED'
  activeVersion: number | null
  updatedAt: string
}
```

版本响应：

```ts
interface PromptTemplateVersion {
  id: number
  templateCode: string
  version: number
  systemPrompt: string
  outputContract: string
  safetyPolicy: string
  active: boolean
  createdBy: number | null
  createdAt: string
}
```

启用版本请求：

```ts
interface UpdateActivePromptVersionRequest {
  versionId: number
}
```

调用日志响应：

```ts
interface LlmCallLog {
  id: number
  scenario: string
  templateCode: string
  templateVersion: number
  provider: string
  model: string
  success: boolean
  latencyMs: number
  errorSummary: string | null
  createdAt: string
}
```

约束：

- 三个 LLM 场景只使用数据库中当前启用的版本；模板缺失、停用或启用版本无效时返回业务错误，不调用供应商 API，也不回退到源码硬编码 Prompt。
- `systemPrompt` 来自已发布模板版本；脱敏 user context 仍由对应业务 Service 构造，版本切换不能改变发送字段范围。
- 编排层在调用成功后校验 JSON object、版本输出契约和统一医疗安全词；校验失败作为失败调用审计。
- `llm_call_log` 最多返回最近 200 条，可按场景、模板、成功状态和 ISO-8601 时间范围筛选。
- 调用日志不包含完整 prompt、完整模型响应、API Key、token、患者真实身份或文件绝对路径；错误摘要只提供脱敏异常类型。
- 固定免责声明由后端 `LlmSafetyPolicy` 提供，不采用模型自行生成的声明。

### 21.1 报告草稿 Prompt 离线评测

评测仅使用仓库内版本化的合成结构化样本，不读取真实病例、原图、mask 或患者身份。第一版只评测 `REPORT_DRAFT_GENERATION`。评测运行、人工评审和版本启用均由管理员负责；只有自动校验通过且管理员人工批准的候选版本才能启用。曾启用过的版本允许回滚。评测不会写入病例、医生审核或 PDF。

```text
POST /prompt-evaluations/runs                 ADMIN
GET /prompt-evaluations/runs                  ADMIN
GET /prompt-evaluations/runs/{id}             ADMIN
PUT /prompt-evaluations/runs/{id}/review      ADMIN
```

启动请求：`{ "candidateVersionId": 123 }`。审核请求：`{ "approved": true, "score": 4, "note": "措辞可用" }`。每轮评测的人工评审只允许保存一次；若需改判，应重新运行评测。接口均使用 `ApiResponse<T>`；启动后立即返回 `QUEUED` 记录，前端轮询查询。运行状态为 `QUEUED / RUNNING / COMPLETED / FAILED`。

```ts
interface PromptEvaluationRun {
  id: number
  templateCode: 'REPORT_DRAFT_GENERATION'
  baselineVersionId: number
  candidateVersionId: number
  sampleVersion: string
  provider: string
  model: string
  status: 'QUEUED' | 'RUNNING' | 'COMPLETED' | 'FAILED'
  automatedPass: boolean | null
  reviewDecision: 'APPROVED' | 'REJECTED' | null
  reviewScore: number | null
  reviewNote: string | null
  reviewedBy: number | null
  reviewedAt: string | null
  createdBy: number
  createdAt: string
  completedAt: string | null
  failureReason: string | null
  result: { cases: Array<{
    caseId: string
    title: string
    context: object // 仅固定合成结构化输入，不含真实病例或身份
    baseline: { passed: boolean; output: string; errorCode: string | null; latencyMs: number }
    candidate: { passed: boolean; output: string; errorCode: string | null; latencyMs: number }
  }> } | null
}
```

列表响应不包含 `result` 内容；详情接口返回每例合成输入及对照。自动校验覆盖 JSON 字段、长度、固定声明和不合规诊断措辞；不自动判断医学事实性或报告专业性。失败输出不保存原文，仅保存错误类型。评测结果与常规 `llm_call_log` 分开存储，后者仍不记录完整 Prompt 或模型长响应。管理员启用尚未发布的报告草稿版本时，后端会核对**最新一轮**评测是否针对当前启用版本、当前供应商/模型和样本版本，且自动校验通过、管理员人工批准；不满足则拒绝发布。

### 21.2 RAG 检索与引用评测

`RAG_KNOWLEDGE_CHAT` 的候选 v2 由 Flyway `V8__rag_grounded_evaluation.sql` 写入，默认不启用。管理员启动固定合成样本评测、记录 1–5 分及批准/拒绝，并决定是否启用版本。请求格式、状态及评测记录公共字段与 21.1 相同，`templateCode` 为 `RAG_KNOWLEDGE_CHAT`。

```text
POST /rag-evaluations/runs                 ADMIN
GET /rag-evaluations/runs                  ADMIN
GET /rag-evaluations/runs/{id}             ADMIN
PUT /rag-evaluations/runs/{id}/review      ADMIN
```

详情 `result.retrieval` 包含 `hitAt3`、`mrr`、`passed`；`result.generationPassed` 为候选版逐题引用检查结果；`result.cases[]` 包含 `caseId`、`title`、`question`、`expectedChunkId`、`retrievedChunkIds`、`rank`，以及 `baseline/candidate` 各自的 `passed`、`answer`、`citations`、`latencyMs`、`errorCode`。评测仅使用合成知识片段，并写入独立的 `retina_rag_eval_v1` Qdrant collection，不改正式知识库。自动通过要求 Hit@3 至少 0.75 且候选版所有回答都有可逐字核验的引用；管理员还须人工判断引用是否真正支持论断。未经最新自动评测通过及人工批准，新版不能启用。评测会产生 Embedding 和 LLM 调用费用，不写入病例或 PDF。

RAG 评测记录另返回 `embeddingModel: string` 和 `scoreThreshold: number`；报告草稿评测对应字段为 `null`。启用 RAG 候选版时，后端也会核对当前 Embedding 模型和检索阈值与评测时一致。
这两个快照字段由 Flyway `V9__rag_evaluation_configuration.sql` 添加；已经应用 V8 的数据库直接继续迁移 V9。

## 22. Spring AI 与只读智能助手

Spring AI 是后端内部模型协议与 Tool Calling 实现，不改变浏览器已有报告草稿、趋势摘要和 RAG 问答契约。`retina.ai.framework` 支持 `spring-ai`（默认）和迁移期回退值 `legacy`。两种实现都必须继续使用数据库 Prompt、脱敏调用审计、医疗安全校验和既有输出结构。

下列接口仅 `USER/DOCTOR/ADMIN` 可访问，统一返回 `ApiResponse<T>`；`RESEARCHER` 不可使用 Agent：

```text
POST /agent/sessions
GET  /agent/sessions
GET  /agent/sessions/{sessionId}/messages
POST /agent/chat
```

提问请求：

```ts
interface AgentChatRequest {
  sessionId?: number
  question: string
}
```

若不传 `sessionId`，后端创建当前用户的新会话。用户只能读取和继续自己的会话。

```ts
interface AgentChatResponse {
  sessionId: number
  answer: string
  skill?: {
    code:
      | 'DOCTOR_WORKLOAD_OVERVIEW'
      | 'ASSIGNED_CASE_SEARCH'
      | 'CASE_CLINICAL_SUMMARY'
      | 'CASE_FOLLOWUP_ANALYSIS'
      | 'DOCTOR_TASK_SEARCH'
      | 'DOCTOR_CLINICAL_QUEUE'
      | 'MEDICAL_KNOWLEDGE_QA'
      | 'MY_CASE_LIST'
      | 'MY_CASE_PROGRESS'
      | 'MY_SIGNED_REPORT'
      | 'PATIENT_KNOWLEDGE_QA'
    name: string
    version: number
  }
  data?:
    | { type: 'METRICS'; payload: Record<string, number> }
    | { type: 'CASE_LIST'; payload: { cases: AgentCaseSummary[] | PatientAgentCaseSummary[] } }
    | { type: 'CASE_DETAIL'; payload: Record<string, unknown> }
    | { type: 'COMPARISON'; payload: Record<string, unknown> }
    | { type: 'TASK_LIST'; payload: { tasks: AgentTaskSummary[] } }
    | { type: 'TASK_DETAIL'; payload: { taskDetail: AgentTaskDetail } }
    | { type: 'CLINICAL_QUEUE'; payload: { queueType: 'PENDING_REVIEW' | 'PENDING_REPORT'; items: AgentClinicalQueueItem[] } }
    | { type: 'CASE_PROGRESS'; payload: { progress?: PatientAgentProgress; empty?: boolean } }
    | { type: 'SIGNED_REPORT'; payload: PatientAgentSignedReportPayload }
    | { type: 'KNOWLEDGE_ANSWER'; payload: { answer?: string } }
  pagination?: {
    page: number
    pageSize: number
    total: number
    hasPrevious: boolean
    hasNext: boolean
  }
  actions: Array<{
    type:
      | 'NEXT_PAGE' | 'PREVIOUS_PAGE'
      | 'VIEW_CASE' | 'VIEW_TASK' | 'VIEW_REVIEW'
      | 'VIEW_CASE_PROGRESS' | 'VIEW_SIGNED_REPORT' | 'GO_TO_IMAGE_UPLOAD'
    label: string
    targetId?: number
    targetPath?: string
  }>
  toolCalls: Array<{
    toolName: string
    success: boolean
    latencyMs: number
  }>
  citations: Array<{
    documentId: number
    chunkId: number
    documentTitle: string
    source: string
    snippet: string
    score: number
  }>
  disclaimer: '智能助手仅供资料检索、结构化数据查询和医生复核参考，不构成诊断、治疗建议或报告签发依据。'
}
```

任务与临床队列结构化字段白名单如下，响应不得附带原图、mask、文件路径、完整 `resultJson`、诊断备注或未签发报告正文：

```ts
interface AgentTaskSummary {
  taskId: number
  taskNo: string
  caseId: number
  caseNo: string
  patientNo: string
  taskType: string
  status: string
  retryCount: number
  maxRetryCount: number
  errorSummary?: string
  submittedAt?: string
  startedAt?: string
  finishedAt?: string
  updatedAt: string
}

interface AgentTaskDetail extends AgentTaskSummary {
  logs: Array<{
    fromStatus?: string
    toStatus: string
    message: string
    operatorType: string
    createdAt: string
  }> // 最多返回最近 5 条脱敏日志
}

interface AgentClinicalQueueItem {
  taskId: number
  taskNo: string
  resultId: number
  caseId: number
  caseNo: string
  patientNo: string
  eyeSide: string
  resultType: string
  qualityStatus?: string
  qualityScore?: number
  reviewStatus: string
  reportStatus?: string
  finishedAt?: string
  resultCreatedAt: string
}

interface PatientAgentCaseSummary {
  caseId: number
  caseNo: string
  eyeSide?: 'LEFT' | 'RIGHT' | 'BOTH'
  workflowStatus?: 'DRAFT' | 'SUBMITTED' | 'IN_REVIEW' | 'COMPLETED' | 'WITHDRAWN'
  qualityStatus: 'CHECKING' | 'ACCEPTABLE' | 'REUPLOAD_RECOMMENDED' | 'UNAVAILABLE'
  qualityMessage: string
  signedReportAvailable: boolean
  updatedAt: string
}

interface PatientAgentProgress {
  caseId: number
  caseNo: string
  currentStage: string
  stages: Array<{ code: string; label: string; status: 'PENDING' | 'CURRENT' | 'COMPLETED' }>
  qualityStatus: 'CHECKING' | 'ACCEPTABLE' | 'REUPLOAD_RECOMMENDED' | 'UNAVAILABLE'
  qualityMessage: string
  nextHandler: string
  message: string
  updatedAt: string
  estimatedCompletionAt?: null
}

interface PatientAgentSignedReportPayload {
  reports?: Array<{
    caseId: number
    caseNo: string
    resultId: number
    version: number
    signedAt: string
    signerName: string
    conclusion: string
  }>
  report?: {
    caseId: number
    caseNo: string
    resultId: number
    version: number
    signedAt: string
    signerName: string
    findings: string
    conclusion: string
    recommendation: string
  }
  explanationAvailable?: boolean
  explanation?: string
  explanationMessage?: string
}
```

医生自然语言请求采用两阶段执行：

1. `Skill Router` 仅接收用户问题、当前角色可见的版本化 Skill 定义和受控 JSON 输出契约，由 LLM 返回 `skillCode/confidence/arguments`。Router 阶段不注册 Function Calling 工具，也不接收病例、任务或临床队列数据。
2. Java 校验 Skill、角色、置信度和参数后选择执行模式。`DOCTOR_WORKLOAD_OVERVIEW`、`ASSIGNED_CASE_SEARCH`、`CASE_CLINICAL_SUMMARY`、`CASE_FOLLOWUP_ANALYSIS`、`DOCTOR_TASK_SEARCH` 和 `DOCTOR_CLINICAL_QUEUE` 使用 `DIRECT`：由受控 Service/Mapper 查询并生成固定摘要和结构化数据，查询结果不再发送给 LLM。`MEDICAL_KNOWLEDGE_QA` 使用 `TOOL_CALLING`，且只注册 `searchMedicalKnowledge`。

患者自然语言请求使用相同的 Router 协议，但候选目录只包含患者 Skill：

1. `MY_CASE_LIST`、`MY_CASE_PROGRESS` 和 `MY_SIGNED_REPORT` 使用 `DIRECT`，SQL 直接按当前账号绑定的 `patient_profile.account_user_id` 限定数据范围，列表固定按最近更新时间倒序且每页最多 10 条。
2. `PATIENT_KNOWLEDGE_QA` 使用 `TOOL_CALLING`，只注册 `searchMedicalKnowledge`，并只检索 `PUBLIC/PATIENT` 受众文档。
3. 报告查询只返回已签发版本。通俗解释只发送已授权报告的字段白名单；模型超时、协议错误或安全拒绝时保留医生签发原文，并把解释标记为暂不可用。
4. 患者端质检只返回 `CHECKING/ACCEPTABLE/REUPLOAD_RECOMMENDED/UNAVAILABLE` 和受控通俗说明，不返回评分、阈值、原始错误、模型信息或任务日志。
5. 患者动作目标仅允许 `/cases/{id}/progress`、`/cases/{id}/progress?section=reports` 和 `/cases/{id}/images`。前端从历史消息恢复动作时仍重新执行该路径白名单。

病例和任务列表固定按最近更新时间倒序，每页默认且最多 10 条；患者数量按 `patient_id` 去重。任务查询支持任务类型、任务状态、时间窗口及任务编号/病例编号定位，未明确任务类型时默认血管分割。临床队列使用独立语义：`PENDING_REVIEW` 返回待医生审核结果，`PENDING_REPORT` 返回已审核通过但尚未签发的结果，不再伪装成病例筛选。

服务端保存分页、完整筛选及本页最多 10 个带类型引用的对象，因此支持“继续”“上一页”“只看失败的”“查看第三个”“查询这个任务”和“比较它最近两次结果”等上下文表达；任务、病例与临床队列引用不会混用。引用过期、列表变化或权限失效时要求重新查询。

助手历史消息的 `structuredContentJson` 可选字段保存上述结构化响应，以便重新打开会话后恢复指标、病例卡片、分页和受控动作。页面跳转路径由后端生成，前端只执行白名单动作，不采用模型返回的任意 URL。

角色工具如下：

- `USER/DOCTOR/ADMIN`：`searchMedicalKnowledge`，并按角色过滤 `PUBLIC/PATIENT/CLINICAL/ADMIN` 知识受众。
- 仅 `USER`：病例列表、检查进度和正式报告是 Java `DIRECT` Skill，不作为 Function Calling 工具暴露给模型；患者知识路径只注册 `searchMedicalKnowledge`。报告通俗解释由独立的受控服务处理，不读取原始 AI 结果、mask、草稿或未签发意见。
- 仅 `DOCTOR`：任务列表/详情、临床队列、工作量、病例筛选、病例摘要和随访比较属于 Java `DIRECT` Skill，不作为 Function Calling 工具暴露给模型；所有 Mapper 查询直接限定当前负责医生。医学知识路径仅注册 `searchMedicalKnowledge`。
- 仅 `ADMIN`：`getQualityControlOverview`、`listQualityRiskAlerts`；不注册患者临床工具。
- 不提供创建任务、修改病例、保存审核、签发 PDF、删除数据、切换 Prompt 或管理知识文档等写工具。

后端关闭模型内部自动工具执行，并限制每次请求最多 4 轮、6 次工具调用。协议异常、超限、越权、写操作请求或高风险确定性诊断输出均返回可读的安全错误/提示。Agent 工具审计不保存 API Key、JWT、完整 Prompt、患者真实身份、文件路径或大段工具结果。

### 22.1 Agent Skill 治理

以下接口仅 `ADMIN` 可访问：

```text
GET  /agent-skills
GET  /agent-skills/{skillCode}/versions
POST /agent-skills/{skillCode}/versions/{versionId}/evaluate
PUT  /agent-skills/{skillCode}/active-version
GET  /agent-skill-executions?skillCode=&success=
```

新 Skill 版本默认不启用。评测使用与生产一致的 LLM Router 协议，并把待评测候选版本放入本次候选目录，不会提前改变线上启用版本。正例同时声明目标 Skill 与预期参数，反例用于检查误路由；启用前要求最近一次评测路由准确率至少 90%、参数准确率至少 95%，且安全检查全部通过。每个会话按 Skill 独立绑定首次使用时的启用版本，因此管理员切换版本只影响尚未绑定该 Skill 的会话，不改变旧会话的执行语义。执行日志最多返回最近 100 条，只包含 Skill、版本、路由置信度、成功状态、耗时和错误类型。

### 16.7 角色和对象级访问

- `USER`（患者）：只能管理本人 `DRAFT` 检查资料、上传图像、提交/撤回申请、查看脱敏进度和已签发报告；不能操作技术任务或查看 mask、AI 草稿和未签发意见。
- `DOCTOR`：只能访问分配给自己的病例及级联临床数据，可参考质量评分自主创建分割任务、审核结果、编辑草稿和签发报告。
- `RESEARCHER`：当前仅开放按受众过滤的知识助手，临床病例、任务、结果、修正和 Agent 接口均拒绝访问。
- `ADMIN`：管理用户、角色和死信恢复；默认不能访问病例、图像、结果、审核和报告等临床明细。

对象 ID 猜测导致的越权访问应返回 404 或 403，下载原图、mask 和 PDF 同样必须执行对象级鉴权。

## 17. 版本记录

| 版本 | 日期 | 说明 |
|---|---|---|
| v2.2 | 2026-10-07 | 新增患者 Agent 自然语言检查列表、进度、重传提示、正式报告卡片与安全降级契约 |
| v2.1 | 2026-09-25 | 将 USER 定义为患者，新增匿名患者档案、检查申请状态、患者进度/正式报告接口、知识受众和角色化 Agent |
| v2.0 | 2026-09-24 | 新增病例负责医生归属、角色首页与管理职责隔离，并增强 Agent 业务编号查询 |
| v1.9 | 2026-09-24 | 接入 Spring AI 1.1.8、Qdrant DocumentRetriever 与医生/管理员只读智能助手 |
| v1.8 | 2026-09-12 | RAG 逐字引用校验、独立检索/生成评测及医生批准发布门槛 |
| v1.7 | 2026-09-12 | 新增报告草稿 Prompt 离线评测、医生评分与候选版本发布门槛 |
| v1.6 | 2026-09-11 | 新增 Prompt 模板版本管理、启用版本切换和脱敏 LLM 调用审计 |
| v1.5 | 2026-06-25 | 同步质量检测、医生审核、修正版本、报告签发、医生工作台和管理员角色接口 |
| v1.4 | 2026-06-19 | 明确契约边界、当前 AI 能力、真实 RabbitMQ 推理状态流转和已知实现偏差 |
| v1.3 | 2026-06-11 | 补充病例详情、更新、删除，以及图像和任务关键操作的响应示例、状态规则和错误语义 |
| v1.2 | 2026-06-04 | 根据前端源码补全注册、病例、图像、任务、结果、日志和统计的详细联调契约 |
| v1.1 | 2026-06-01 | 拆分文档职责，保留接口契约为唯一 API 真相源 |
| v1.0 | 2026-06-01 | 初版接口契约 |
