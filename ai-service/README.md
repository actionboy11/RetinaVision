# RetinaVision AI

RetinaVision AI 是独立的 FastAPI 推理服务。它提供 OpenCV/NumPy 图像质量检测和 PyTorch FSCNet 血管分割两个能力：质量检测返回工程门控评分和原因码，血管分割返回结构化指标和分割结果图地址。Java 后端是该服务的调用方；浏览器不直接访问 AI 服务。

## 当前能力

- 应用启动时加载 `models/model_new.pth`
- 自动选择 `cuda:0` 或 CPU
- 接收最大 20 MB 的图像上传
- 使用 OpenCV/NumPy 计算图像质量评分、五项指标和原因码
- 将图像解码为灰度图并保存推理输入
- 96×96 滑窗切块、重叠区域平均重建
- sigmoid、阈值二值化和小连通域过滤
- 输出 PNG mask、血管面积比例、模型版本和处理耗时
- 健康检查和结果图下载
- API、推理、模型加载单元测试

当前实现 `IMAGE_QUALITY_CHECK` 和 `VESSEL_SEGMENTATION`。服务仍不负责浏览器鉴权、业务数据库、RabbitMQ、医生审核、报告生成或任务队列；这些职责属于 Java 后端。

## 目录

```text
ai-service/
├─ models/
│  └─ model_new.pth
├─ src/retinavision_ai/
│  ├─ api.py               # FastAPI 应用和路由
│  ├─ config.py            # 环境变量配置
│  ├─ inference.py         # 滑窗推理与后处理
│  ├─ model_loader.py      # checkpoint 加载
│  ├─ schemas.py           # 请求结果 schema
│  ├─ service.py           # 输入与 mask 文件管理
│  └─ model_def/           # FSCNet 结构
├─ storage/
│  ├─ inputs/              # 归一化保存的输入图像
│  └─ masks/               # 推理结果图
├─ tests/
└─ requirements.txt
```

`storage/` 是运行时数据，不是业务源码。输入图像和 mask 可能包含敏感医疗信息，不应提交到公共仓库。

## 环境要求

- Python 3.11+
- PyTorch（与本机 CPU 或 CUDA 匹配）
- 模型文件 `models/model_new.pth`

`requirements.txt` 当前固定 FastAPI、HTTPX、NumPy、OpenCV、Pydantic、pytest、Uvicorn 等依赖，但没有固定 PyTorch。原因是 CPU 和不同 CUDA 环境需要不同构建；创建环境后必须单独安装 PyTorch。

## 安装

PowerShell：

```powershell
Set-Location .\ai-service
python -m venv .venv
.\.venv\Scripts\Activate.ps1
python -m pip install --upgrade pip
python -m pip install -r requirements.txt
```

随后安装与设备匹配的 PyTorch，并验证：

```powershell
python -c "import torch; print(torch.__version__); print(torch.cuda.is_available())"
```

不要为了方便把某个 CUDA 专用 wheel 固定到通用 `requirements.txt`。

## 配置

所有配置均可通过环境变量覆盖：

| 环境变量 | 默认值 | 说明 |
|---|---|---|
| `RETINAVISION_AI_MODEL_PATH` | `models/model_new.pth` | checkpoint 路径 |
| `RETINAVISION_AI_STORAGE_ROOT` | `storage` | 输入和 mask 根目录 |
| `RETINAVISION_AI_MODEL_NAME` | `FSCNet_Final_DMI` | 返回给业务系统的模型名 |
| `RETINAVISION_AI_MODEL_VERSION` | `model_new-v1` | 模型版本 |
| `RETINAVISION_AI_THRESHOLD` | `0.5` | 二值化阈值 |
| `RETINAVISION_AI_WINDOW_SIZE` | `96` | 滑窗边长 |
| `RETINAVISION_AI_STRIDE` | `32` | 滑窗步长 |
| `RETINAVISION_AI_BATCH_SIZE` | `4` | 推理 batch 大小 |

示例：

```powershell
$env:RETINAVISION_AI_MODEL_PATH = 'D:\models\model_new.pth'
$env:RETINAVISION_AI_STORAGE_ROOT = 'D:\retinavision-ai-data'
$env:RETINAVISION_AI_BATCH_SIZE = '2'
```

## 启动

```powershell
python -m uvicorn retinavision_ai.api:app --app-dir src --host 127.0.0.1 --port 8000
```

服务会在 FastAPI lifespan 启动阶段加载模型。checkpoint 缺失、结构不匹配或 PyTorch 不可用时，进程不会正常就绪。

验证健康状态：

```powershell
Invoke-RestMethod http://127.0.0.1:8000/health
```

响应：

```json
{
  "status": "ready",
  "modelName": "FSCNet_Final_DMI",
  "modelVersion": "model_new-v1"
}
```

## API

### 健康检查

```text
GET /health
```

返回 `ready` 代表服务实例已经持有可调用的推理服务。该接口不执行一次真实图像推理。

### 图像质量检测

```text
POST /v1/inference/image-quality-check
Content-Type: multipart/form-data
file: <image>
```

成功响应：

```json
{
  "resultType": "IMAGE_QUALITY_CHECK",
  "resultJson": {
    "grade": "PASS",
    "score": 87.5,
    "metrics": {
      "sharpness": 0.82,
      "exposure": 0.91,
      "contrast": 0.79,
      "fieldCoverage": 0.94,
      "illuminationUniformity": 0.86
    },
    "reasons": []
  },
  "modelName": "retinavision-rule-quality",
  "modelVersion": "1.0.0",
  "processingTimeMs": 32
}
```

第一版质量检测是工程质量门控，不宣称临床级可判读性模型。阈值统一使用 `RETINAVISION_QUALITY_` 前缀，包括 `BLUR_THRESHOLD`、`UNDEREXPOSED_MEAN`、`OVEREXPOSED_MEAN`、`CONTRAST_THRESHOLD`、`FIELD_COVERAGE_THRESHOLD`、`ILLUMINATION_UNIFORMITY_THRESHOLD`、`SATURATED_PIXEL_RATIO_THRESHOLD`、`TARGET_FIELD_COVERAGE`、`WARNING_SCORE`、`PASS_SCORE`。修改阈值或预处理后必须运行质量测试。

### 血管分割

```text
POST /v1/inference/vessel-segmentation
Content-Type: multipart/form-data
file: <image>
```

PowerShell 示例：

```powershell
curl.exe -X POST http://127.0.0.1:8000/v1/inference/vessel-segmentation `
  -F "file=@C:\path\to\fundus.png;type=image/png"
```

成功响应：

```json
{
  "inferenceId": "bcbb375ce0bd4273a6cd9bb9b6b209b0",
  "resultType": "VESSEL_SEGMENTATION",
  "resultJson": {
    "vesselAreaRatio": 0.143,
    "processingTimeMs": 3280,
    "modelVersion": "model_new-v1",
    "conclusion": "已完成视网膜血管分割；结果仅供辅助分析"
  },
  "modelName": "FSCNet_Final_DMI",
  "modelVersion": "model_new-v1",
  "processingTimeMs": 3280,
  "maskUrl": "/v1/artifacts/bcbb375ce0bd4273a6cd9bb9b6b209b0/mask"
}
```

校验规则：

- `Content-Type` 必须以 `image/` 开头，否则返回 HTTP 415。
- 空文件返回 HTTP 400。
- 超过 20 MB 返回 HTTP 413。
- 无法由 OpenCV 解码的内容返回 HTTP 400。

### 下载 mask

```text
GET /v1/artifacts/{inferenceId}/mask
```

返回 `image/png`。`inferenceId` 只能包含小写十六进制字符；非法 ID 返回 400，不存在返回 404。

## 推理过程

1. OpenCV 将输入解码为二维灰度图。
2. 小于窗口的图像使用边缘值补齐；大图按窗口与步长提取 patch，并确保覆盖右侧和底部边缘。
3. 输入归一化到 `[0, 1]`，形状为 `N×1×H×W`。
4. 模型以 `eval()` 和 `torch.inference_mode()` 分 batch 推理。
5. 对重叠 patch 的 sigmoid 概率取平均。
6. 使用阈值生成二值 mask，并移除面积小于 10 像素的连通域。
7. mask 写为 0/255 PNG；`vesselAreaRatio` 为前景像素占全部像素的比例。

服务同时保存一份解码后的 PNG 输入和 mask：

```text
storage/inputs/{inferenceId}.png
storage/masks/{inferenceId}.png
```

Java 后端会下载 mask 并保存到自己的结果目录，因此两个服务各有一份结果图。

## 模型加载

`load_fscnet_model` 支持两种 checkpoint：

- 顶层直接是 state dict
- 顶层对象包含 `model_state_dict`

模型以 `FSCNet_Final_DMI(in_ch=1, start_ch=64)` 创建，并使用 `strict=True` 加载。模型结构与权重不一致会立即失败，避免静默忽略参数。

## 测试

常规单元测试不读取本地模型权重，适合开发与 CI：

```powershell
python -m pytest -q .\tests -m "not model_integration"
```

放置 `models/model_new.pth` 后，再显式运行真实模型加载测试：

```powershell
python -m pytest -q .\tests\test_model_loader.py -m model_integration
```

测试范围包括：

- 健康检查、推理响应和 mask 下载
- 非图像上传拒绝
- patch 对边缘的完整覆盖
- 小图 padding
- 重叠区域概率平均
- 二值 mask 与血管面积比例
- checkpoint 加载（`model_integration`）

API 测试使用 fake inference service，不需要加载真实模型。推理单元测试仍需要 PyTorch，
只有 `model_integration` 测试需要真实 checkpoint。

## 与 Java 后端联调

Java 后端配置：

```yaml
retina:
  ai:
    base-url: http://127.0.0.1:8000
    connect-timeout: 5s
    read-timeout: 5m
```

Java 上传原图调用 `image-quality-check` 或 `vessel-segmentation`。质量检测响应直接用于 Java 的任务结果和图像质量投影；血管分割响应包含 `maskUrl`，Java 接收 JSON 后再请求该地址下载结果图。`maskUrl` 应保持为同一 AI 服务下的相对路径；不要返回外部主机 URL。

## 常见问题

### `ModuleNotFoundError: torch`

PyTorch 没有写入通用 requirements，请在当前 Python 环境单独安装。

### `Model checkpoint not found`

确认 `RETINAVISION_AI_MODEL_PATH` 或默认的 `models/model_new.pth` 指向普通文件，并确认启动用户有读取权限。

### checkpoint 参数不匹配

当前加载使用 `strict=True`。确认模型定义、`in_ch=1`、`start_ch=64` 与训练时一致，不要通过 `strict=False` 隐藏结构问题。

### CPU 推理很慢或 Java 读取超时

降低 `RETINAVISION_AI_BATCH_SIZE` 主要用于降低内存，并不保证更快。可按硬件调整 batch，并同步增大 Java `retina.ai.read-timeout`；不要把连接超时和推理读取超时混淆。

### storage 持续增长

当前没有自动清理策略。部署时应为 `storage/inputs` 和 `storage/masks` 制定受控保留策略，并确保不会删除仍被排查或审计需要的产物。

## 长期运行与可观测性

AI 服务固定使用单个 Uvicorn Worker，并通过进程内锁保证同一时间只有一个请求进入 PyTorch 推理。`GET /health` 会返回模型、设备、忙碌状态、本进程请求计数、最近推理耗时和最近错误；这些进程指标会在服务重启后清零，长期任务指标由 Java 后端数据库统计。

### Windows Conda 常驻

```powershell
Set-Location .\ai-service
.\scripts\start-ai.ps1
.\scripts\health-ai.ps1
.\scripts\stop-ai.ps1
```

默认 Python 为 `C:\develop\anaconda3\envs\retinavision-ai\python.exe`。启动脚本检查权重和端口，把 PID 写入 `storage/retinavision-ai.pid`，并将标准输出和错误输出写入 `logs/`。

### Docker

```powershell
Set-Location C:\codexcode\RetinaVision\RetinaVision\docker
docker compose up -d retinavision-ai
docker compose ps
```

镜像默认安装 CPU 版 PyTorch。权重以只读方式挂载，`storage/` 和 `logs/` 以可写目录挂载。CUDA 部署应替换为与宿主驱动匹配的 PyTorch/CUDA 基础镜像并配置 GPU 设备，不要直接复用 CPU 镜像。

## 安全与限制

- 服务当前没有鉴权，应仅监听受信任网络或由 Java 后端隔离访问。
- 上传内容会落盘，必须按医疗数据要求控制权限、保留时间和备份。
- 结论文本明确标注“仅供辅助分析”，不能作为自动诊断。
- 服务当前单进程内持有模型；多 worker 会各自加载一份模型并占用独立内存/显存。
