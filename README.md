# RetinaVision Backend

RetinaVision Backend 是系统的业务核心和浏览器 API 入口。它负责 JWT 鉴权、病例与图像、异步分析任务、RabbitMQ 消息消费、Python AI 调用、结果持久化、文件预览和 Dashboard 统计。

系统总览和统一启动顺序见上级目录 `README.md`；浏览器接口字段以 `API_CONTRACT.md` 为准。

## 当前能力

- 用户注册、登录、当前用户和退出登录
- 病例分页、详情、新建、更新和软删除
- 眼底图像上传、本地存储、列表、预览和软删除
- 分析任务分页、创建、详情、取消、重试和状态日志
- RabbitMQ publisher confirm、JSON 消息、手动 ACK 和死信队列
- 调用 FastAPI 血管分割接口，下载 mask 并保存分析结果
- 任务统计、队列统计和近期任务趋势
- 分割结果 JSON 查询与 mask 二进制预览

当前只执行 `VESSEL_SEGMENTATION`。`IMAGE_QUALITY_CHECK` 可以进入公共请求枚举，但执行服务会将其标记为 `FAILED`，直至对应模型和执行分支完成。

## 技术栈

- Java 17
- Spring Boot 3.5
- Spring Web、Validation、Security
- Spring AMQP / RabbitMQ
- MyBatis-Plus 3.5
- MySQL 8
- JJWT
- 本地文件系统（当前主流程）

POM 中包含 Redis 和 MinIO 客户端，但当前核心业务链路未使用它们。

## 目录

```text
RetinaVision/
├─ docker/
│  ├─ docker-compose.yml       # MySQL + RabbitMQ
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

这些凭据仅用于本机开发。

`docker/mysql/init.sql` 创建以下表：

- `sys_user`
- `medical_case`
- `image_file`
- `analysis_task`
- `analysis_result`
- `task_log`

初始化 SQL 只在空数据卷首次启动时执行。修改表结构后，已有数据卷需要单独迁移。

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
```

生产或共享环境必须通过外部配置覆盖 JWT secret 和所有凭据，不能沿用开发值。

## 启动与验证

先启动 MySQL、RabbitMQ 和 Python AI，再执行：

```powershell
mvn spring-boot:run
```

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

## API 概览

所有路径均位于 `/api` 下。JSON 业务接口使用统一响应包装；图片和报告接口直接返回二进制。

| 模块 | 主要路径 |
|---|---|
| Auth | `/auth/register`、`/auth/login`、`/auth/me`、`/auth/logout` |
| Case | `/cases`、`/cases/{caseId}` |
| Image | `/cases/{caseId}/images`、`/images/{imageId}/preview` |
| Task | `/analysis-tasks`、`/analysis-tasks/{taskId}`、`cancel`、`retry` |
| Result | `/analysis-tasks/{taskId}/result`、`/results/{resultId}/mask` |
| Log | `/analysis-tasks/{taskId}/logs`（兼容单数 `/log`） |
| Dashboard | `/admin/statistics/tasks`、`queue`、`task-trend` |

完整请求、响应、错误码和枚举见 `../API_CONTRACT.md`。

## AI 任务执行

### 创建和投递

`TaskServiceImpl` 校验病例与图像后：

1. 新增 `analysis_task`，状态为 `WAITING`。
2. 发布 `AnalysisTaskMessage` 到 `retina.analysis.exchange`。
3. 等待最长 5 秒的 broker confirm；未确认则返回 MQ 投递错误。
4. 写入一条 `USER` 类型任务日志。

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
POST {retina.ai.base-url}/v1/inference/vessel-segmentation
```

随后从响应 `maskUrl` 下载 PNG。为避免服务端请求伪造，下载地址必须与配置的 AI 基础地址具有相同 scheme 和 authority。

### 结果持久化

成功时：

- `resultJson`、模型名、模型版本和耗时写入 `analysis_result`。
- mask 保存到 `uploads/results/tasks/{taskId}/mask.png`。
- `mask_bucket` 记录为 `local`，`mask_object_key` 记录相对路径。
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

当前推理不生成报告，所以 `/results/{resultId}/report` 通常返回不存在；该接口是后续报告能力的预留入口。

## 已知契约差异

以下是当前源码与 `API_CONTRACT.md` 目标契约之间需要后续统一的地方：

- 创建任务 VO 当前返回 `errorMessage`，前端类型期望 `message`；前端已有默认成功文案，因此主流程不阻塞。
- 取消任务 Controller 当前返回 `data: null`，前端类型和契约写作 `boolean`；前端当前不读取该值。
- 公共枚举包含 `IMAGE_QUALITY_CHECK`，但执行服务尚未支持。

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
