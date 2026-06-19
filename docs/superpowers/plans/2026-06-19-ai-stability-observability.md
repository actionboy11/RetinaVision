# AI Stability and Observability Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add single-flight Python inference, runtime health metrics, Java health aggregation, Dashboard status presentation, and repeatable Windows/Docker operation without changing the model or task message contract.

**Architecture:** Python owns process-local inference state and serializes model execution. Java probes Python with a separate short-timeout client, combines that snapshot with existing RabbitMQ and database statistics, and exposes one authenticated system-status endpoint. The Vue Dashboard renders the aggregate; persistent business metrics remain sourced from MySQL.

**Tech Stack:** Python 3.11, FastAPI, PyTorch, pytest/httpx; Java 17, Spring Boot 3.5, RestClient, MyBatis-Plus, RabbitMQ, JUnit 5/Mockito; Vue 3, TypeScript, Element Plus, Axios; Docker Compose and PowerShell.

---

## Repository and file map

The backend Git repository is `C:/codexcode/RetinaVision/RetinaVision`. The sibling frontend and AI directories currently are not Git repositories, so implementation commits can only safely contain backend files. Existing backend worktree changes must be preserved; commits must stage explicit files only and must not sweep unrelated changes into a commit.

Python AI responsibilities:

- Create `C:/codexcode/RetinaVision/retinavision-ai/src/retinavision_ai/runtime_state.py`: thread-safe process metrics and the single-flight inference lock.
- Modify `.../api.py`: request correlation, timing, failure isolation, and expanded health response.
- Modify `.../schemas.py`: stable health contract.
- Modify `.../service.py`: expose device metadata without changing model behavior.
- Modify `.../tests/test_api.py`: contract, counters, errors, and request ID tests.
- Create `.../tests/test_runtime_state.py`: concurrency and lock-release tests.
- Create `.../scripts/start-ai.ps1`, `stop-ai.ps1`, `health-ai.ps1`: Windows operation.
- Create `.../Dockerfile` and `.../.dockerignore`: container operation.

Java responsibilities:

- Create `src/main/java/com/example/retinavision/ai/dto/AiHealthResponse.java`: Python health DTO.
- Create `src/main/java/com/example/retinavision/ai/AiHealthClient.java` and `HttpAiHealthClient.java`: isolated health probe.
- Modify `AiServiceProperties.java`: health timeout properties.
- Create `pojo/VO/SystemStatusVO.java`: aggregate API contract.
- Create `service/SystemStatusService.java` and `service/impl/SystemStatusServiceImpl.java`: best-effort aggregation.
- Create `controller/SystemStatusController.java`: authenticated `GET /system/status`.
- Modify `HttpAiInferenceClient.java`, `AiInferenceClient.java`, and `AnalysisTaskExecutionServiceImpl.java`: request ID, timing, and normalized error logs.
- Add focused tests for health probing, aggregation, and invocation correlation.

Frontend responsibilities:

- Create `C:/codexcode/RetinaVision/src/types/system.ts`: aggregate status types.
- Create `C:/codexcode/RetinaVision/src/api/system.ts`: API wrapper.
- Create `C:/codexcode/RetinaVision/src/components/SystemStatusPanel.vue`: isolated status UI.
- Modify `C:/codexcode/RetinaVision/src/views/Dashboard.vue`: independent loading and failure handling.

Deployment responsibilities:

- Modify backend `docker/docker-compose.yml`: add AI service, mounts, environment, and healthcheck.
- Modify both backend and AI README files with Windows and Docker commands.

---

### Task 1: Python runtime state and single-flight inference

**Files:**
- Create: `C:/codexcode/RetinaVision/retinavision-ai/src/retinavision_ai/runtime_state.py`
- Test: `C:/codexcode/RetinaVision/retinavision-ai/tests/test_runtime_state.py`

- [ ] **Step 1: Write failing runtime-state tests**

Add tests that assert initial counters are zero, `busy` is true only inside the context, success/failure updates are atomic, and a raised exception releases the lock:

```python
def test_inference_slot_releases_lock_after_failure():
    state = RuntimeState("model", "v1", "cpu")
    with pytest.raises(RuntimeError):
        with state.inference_slot("request-1") as attempt:
            raise RuntimeError("boom")
    assert state.snapshot().busy is False
    assert state.snapshot().failure_count == 1
```

Use two threads and `threading.Event` to prove the second request cannot enter before the first exits.

- [ ] **Step 2: Run tests and verify RED**

Run:

```powershell
& C:\develop\anaconda3\envs\retinavision-ai\python.exe -m pytest tests/test_runtime_state.py -q
```

Expected: collection fails because `RuntimeState` does not exist.

- [ ] **Step 3: Implement the minimal thread-safe state**

Implement immutable `RuntimeSnapshot`, mutable attempt timing, and a `threading.Lock`-backed context manager. The context manager records queue wait, sets `busy`, increments total requests, and exposes `succeed(inference_ms)`; if success is not recorded or an exception escapes, increment failure count and store a sanitized error string.

Core interface:

```python
class RuntimeState:
    def __init__(self, model_name: str, model_version: str, device: str): ...
    def inference_slot(self, request_id: str) -> AbstractContextManager[InferenceAttempt]: ...
    def snapshot(self) -> RuntimeSnapshot: ...
```

- [ ] **Step 4: Run test and verify GREEN**

Run the command from Step 2. Expected: all runtime-state tests pass.

---

### Task 2: Python health contract, request correlation, and structured logs

**Files:**
- Modify: `C:/codexcode/RetinaVision/retinavision-ai/src/retinavision_ai/api.py`
- Modify: `C:/codexcode/RetinaVision/retinavision-ai/src/retinavision_ai/schemas.py`
- Modify: `C:/codexcode/RetinaVision/retinavision-ai/src/retinavision_ai/service.py`
- Modify: `C:/codexcode/RetinaVision/retinavision-ai/tests/test_api.py`

- [ ] **Step 1: Expand the failing health test**

Change `test_health_reports_ready_model` to assert camel-case fields `status`, `ready`, `modelLoaded`, `modelName`, `modelVersion`, `device`, `busy`, `startedAt`, `totalRequests`, `successCount`, `failureCount`, `lastInferenceTimeMs`, `lastSuccessAt`, and `lastError`.

- [ ] **Step 2: Add failing request lifecycle tests**

Add tests that send `X-Request-ID: java-task-100`, assert it is returned as a response header, then assert health counters update. Add a fake service that raises `RuntimeError`; assert HTTP 500, `busy=false`, `failureCount=1`, and a later request can still succeed.

- [ ] **Step 3: Run API tests and verify RED**

```powershell
& C:\develop\anaconda3\envs\retinavision-ai\python.exe -m pytest tests/test_api.py -q
```

Expected: new health keys and request ID behavior are absent.

- [ ] **Step 4: Implement the expanded health schema**

Use this Pydantic shape:

```python
class HealthResponse(BaseModel):
    model_config = ConfigDict(populate_by_name=True)
    status: str
    ready: bool
    model_loaded: bool = Field(alias="modelLoaded")
    model_name: str = Field(alias="modelName")
    model_version: str = Field(alias="modelVersion")
    device: str
    busy: bool
    started_at: datetime = Field(alias="startedAt")
    total_requests: int = Field(alias="totalRequests")
    success_count: int = Field(alias="successCount")
    failure_count: int = Field(alias="failureCount")
    last_inference_time_ms: int | None = Field(alias="lastInferenceTimeMs")
    last_success_at: datetime | None = Field(alias="lastSuccessAt")
    last_error: str | None = Field(alias="lastError")
```

- [ ] **Step 5: Wrap inference with state and correlation**

Accept `Request` in the segment route. Normalize `X-Request-ID` to a bounded safe value or generate `uuid4().hex`. Run `service.infer` inside `runtime_state.inference_slot(request_id)`, emit one start and one finish JSON-style log with `extra`, and set `X-Request-ID` on both success and handled error responses. Preserve 400 for input `ValueError`; map unexpected inference failures to HTTP 500 with a generic detail.

- [ ] **Step 6: Run Python API and full Python tests**

```powershell
& C:\develop\anaconda3\envs\retinavision-ai\python.exe -m pytest -q
```

Expected: all API, inference, model-loader, and runtime-state tests pass.

---

### Task 3: Java short-timeout AI health probe

**Files:**
- Create: `src/main/java/com/example/retinavision/ai/dto/AiHealthResponse.java`
- Create: `src/main/java/com/example/retinavision/ai/AiHealthClient.java`
- Create: `src/main/java/com/example/retinavision/ai/HttpAiHealthClient.java`
- Modify: `src/main/java/com/example/retinavision/ai/AiServiceProperties.java`
- Modify: `src/main/resources/application.yaml`
- Create test: `src/test/java/com/example/retinavision/ai/HttpAiHealthClientTest.java`

- [ ] **Step 1: Write failing health-client tests**

Use `MockRestServiceServer` against an injectable `RestClient.Builder`. Assert a valid Python response maps to `AiHealthResponse`, and connection/HTTP/JSON errors return a `DOWN` result instead of escaping to callers.

Expected interface:

```java
public interface AiHealthClient {
    AiHealthResponse checkHealth();
}
```

- [ ] **Step 2: Run the focused test and verify RED**

```powershell
mvn.cmd -Dtest=HttpAiHealthClientTest test
```

Expected: compilation fails because the client and DTO do not exist.

- [ ] **Step 3: Implement properties and client**

Add defaults:

```java
private Duration healthConnectTimeout = Duration.ofSeconds(2);
private Duration healthReadTimeout = Duration.ofSeconds(3);
```

Add YAML keys `health-connect-timeout: 2s` and `health-read-timeout: 3s`. Build a dedicated `SimpleClientHttpRequestFactory` and `RestClient`; do not reuse the inference client's five-minute read timeout. Return `reachable=false`, `status="DOWN"`, and a sanitized `lastError` when probing fails.

- [ ] **Step 4: Run focused and existing AI client tests**

```powershell
mvn.cmd "-Dtest=HttpAiHealthClientTest,HttpAiInferenceClientTest" test
```

Expected: both test classes pass.

---

### Task 4: Java aggregate system status endpoint

**Files:**
- Create: `src/main/java/com/example/retinavision/pojo/VO/SystemStatusVO.java`
- Create: `src/main/java/com/example/retinavision/service/SystemStatusService.java`
- Create: `src/main/java/com/example/retinavision/service/impl/SystemStatusServiceImpl.java`
- Create: `src/main/java/com/example/retinavision/controller/SystemStatusController.java`
- Create test: `src/test/java/com/example/retinavision/service/impl/SystemStatusServiceImplTest.java`

- [ ] **Step 1: Write failing aggregation tests**

Mock `AiHealthClient` and `StatisticsService`. Cover all-UP, AI-DOWN/Rabbit-UP, AI-UP/Rabbit-exception, and busy AI. Assert dependency exceptions produce component `DOWN` and overall `DEGRADED` while available data remains present.

- [ ] **Step 2: Run focused test and verify RED**

```powershell
mvn.cmd -Dtest=SystemStatusServiceImplTest test
```

Expected: compilation fails because aggregate service types do not exist.

- [ ] **Step 3: Implement focused aggregate types**

`SystemStatusVO` contains `overallStatus`, `checkedAt`, `ai`, `queue`, and `tasks`. Reuse values from `TaskStatisticsVO` and `QueueStatisticsVO`; add `retryingCount` to the existing task statistics SQL/VO/type chain so persistent retry activity is visible.

Aggregation rules:

```java
String overall = aiUp && queueUp ? "UP" : "DEGRADED";
```

Do not catch database statistics failure as healthy; let the standard server-error handler respond if persistent task statistics cannot be read.

- [ ] **Step 4: Add authenticated controller**

```java
@RestController
@RequestMapping("/system")
public class SystemStatusController {
    @GetMapping("/status")
    public Result<SystemStatusVO> status() {
        return Result.success(systemStatusService.getStatus());
    }
}
```

No new SecurityConfig matcher is needed because `.anyRequest().authenticated()` already protects it.

- [ ] **Step 5: Run aggregate and statistics tests**

```powershell
mvn.cmd "-Dtest=SystemStatusServiceImplTest,RetinaVisionApplicationTests" test
```

Expected: focused tests and Spring context test pass.

---

### Task 5: Correlated Java inference logs and error classification

**Files:**
- Create: `src/main/java/com/example/retinavision/ai/AiFailureCategory.java`
- Modify: `src/main/java/com/example/retinavision/ai/AiInferenceClient.java`
- Modify: `src/main/java/com/example/retinavision/ai/HttpAiInferenceClient.java`
- Modify: `src/main/java/com/example/retinavision/service/impl/AnalysisTaskExecutionServiceImpl.java`
- Modify tests: `src/test/java/com/example/retinavision/ai/HttpAiInferenceClientTest.java`
- Modify tests: `src/test/java/com/example/retinavision/service/impl/AnalysisTaskExecutionServiceImplTest.java`

- [ ] **Step 1: Write failing request-ID tests**

Change the inference client contract to accept `requestId`, capture outgoing headers, and assert `X-Request-ID` is sent on segmentation and artifact download requests.

```java
AiInferenceResponse segment(Path imagePath, String filename, String contentType, String requestId);
byte[] downloadMask(String maskUrl, String requestId);
```

- [ ] **Step 2: Write failing error-category tests**

Assert connection failures, read timeout, 4xx, 5xx, incomplete response, untrusted artifact URL, and empty artifact map to fixed `AiFailureCategory` values carried by `AiInferenceException`.

- [ ] **Step 3: Run focused tests and verify RED**

```powershell
mvn.cmd "-Dtest=HttpAiInferenceClientTest,AnalysisTaskExecutionServiceImplTest" test
```

- [ ] **Step 4: Implement minimal classification and logs**

Generate one UUID per task attempt in `AnalysisTaskExecutionServiceImpl`, pass it through both HTTP calls, and log start/end with SLF4J placeholders for `taskId`, `taskNo`, `retryCount`, `requestId`, phase, duration, and category. Never log token headers, image bytes, or result bytes.

- [ ] **Step 5: Run focused tests**

Run the Step 3 command. Expected: all focused tests pass.

---

### Task 6: Frontend system status panel

**Files:**
- Create: `C:/codexcode/RetinaVision/src/types/system.ts`
- Create: `C:/codexcode/RetinaVision/src/api/system.ts`
- Create: `C:/codexcode/RetinaVision/src/components/SystemStatusPanel.vue`
- Modify: `C:/codexcode/RetinaVision/src/views/Dashboard.vue`

- [ ] **Step 1: Define exact TypeScript contract**

Create `SystemStatus`, `AiRuntimeStatus`, `QueueRuntimeStatus`, and `TaskRuntimeStatus` interfaces matching Java camel-case JSON. Status is the union `'UP' | 'DEGRADED' | 'DOWN'`.

- [ ] **Step 2: Add API wrapper**

```typescript
export const getSystemStatus = (): Promise<SystemStatus> =>
  request.get<unknown, SystemStatus>('/system/status')
```

- [ ] **Step 3: Implement isolated panel states**

`SystemStatusPanel.vue` accepts `status`, `loading`, and `error`. Render overall status, AI state/busy badge, model/version/device, recent inference time/error, queue counts, and task metrics. Use Element Plus alerts/tags and existing formatting utilities; do not add a chart library.

- [ ] **Step 4: Integrate independent loading**

In `Dashboard.vue`, request system status alongside existing statistics with `Promise.allSettled`. A system-status failure sets only `systemStatusError`; it must not clear task statistics, queue statistics, or trend data. Existing refresh button reloads all sections.

- [ ] **Step 5: Run frontend verification**

```powershell
npm run type-check
npm run build
```

If `type-check` is absent, run `npx vue-tsc --noEmit` and record that substitution. Expected: no TypeScript errors and production build succeeds.

---

### Task 7: Windows and Docker operation

**Files:**
- Create: `C:/codexcode/RetinaVision/retinavision-ai/scripts/start-ai.ps1`
- Create: `C:/codexcode/RetinaVision/retinavision-ai/scripts/stop-ai.ps1`
- Create: `C:/codexcode/RetinaVision/retinavision-ai/scripts/health-ai.ps1`
- Create: `C:/codexcode/RetinaVision/retinavision-ai/Dockerfile`
- Create: `C:/codexcode/RetinaVision/retinavision-ai/.dockerignore`
- Modify: `C:/codexcode/RetinaVision/RetinaVision/docker/docker-compose.yml`
- Modify: `C:/codexcode/RetinaVision/retinavision-ai/README.md`
- Modify: `C:/codexcode/RetinaVision/RetinaVision/README.md`

- [ ] **Step 1: Implement Windows scripts**

`start-ai.ps1` validates the exact Python executable and model path, creates `storage` and `logs`, rejects an occupied port 8000, and launches hidden with one Uvicorn worker. Store PID in `storage/retinavision-ai.pid`. `stop-ai.ps1` validates the PID belongs to the recorded process before stopping it. `health-ai.ps1` calls `/health` and exits nonzero unless `status=UP` and `ready=true`.

- [ ] **Step 2: Smoke-test Windows scripts**

```powershell
& .\scripts\start-ai.ps1
& .\scripts\health-ai.ps1
& .\scripts\stop-ai.ps1
```

Expected: start succeeds, health prints model/device state, stop removes the PID file.

- [ ] **Step 3: Add CPU-capable Docker image**

Use Python 3.11 slim, install system OpenCV dependencies and `requirements.txt`, install CPU PyTorch from the official CPU index, copy only source code, expose 8000, and run one worker. Do not copy `models/`, `storage/`, or logs into the image.

- [ ] **Step 4: Add Compose service and healthcheck**

Add service `retinavision-ai`, mount `models/model_new.pth` read-only plus writable storage/log directories, publish 8000, and run a Python standard-library HTTP healthcheck. Document that host-run Java uses `http://127.0.0.1:8000`; container-run Java uses `http://retinavision-ai:8000` through `RETINA_AI_BASE_URL`.

- [ ] **Step 5: Validate Compose**

```powershell
docker compose -f C:\codexcode\RetinaVision\RetinaVision\docker\docker-compose.yml config
docker compose -f C:\codexcode\RetinaVision\RetinaVision\docker\docker-compose.yml up -d retinavision-ai
docker compose -f C:\codexcode\RetinaVision\RetinaVision\docker\docker-compose.yml ps
```

Expected: configuration resolves and AI container becomes healthy. If image download is unavailable, record the external blocker after `docker compose config` succeeds.

---

### Task 8: Full regression and handoff

**Files:**
- Modify only documentation if verification reveals command drift.

- [ ] **Step 1: Run Python suite**

```powershell
& C:\develop\anaconda3\envs\retinavision-ai\python.exe -m pytest -q
```

- [ ] **Step 2: Run Java suite**

```powershell
mvn.cmd test
git diff --check
```

- [ ] **Step 3: Run frontend checks**

```powershell
npm run type-check
npm run build
```

- [ ] **Step 4: Execute failure-path acceptance**

Start all services, verify `/api/system/status` is `UP`, stop Python, verify `DEGRADED`, submit a task and observe finite retry, restart Python, verify status returns to `UP`, and confirm the task succeeds or can be recovered from dead letter using the existing admin endpoint.

- [ ] **Step 5: Review repository scope**

Use explicit `git status --short` and `git diff --name-only`. Do not stage or overwrite unrelated pre-existing changes. Since frontend and AI folders are outside the backend Git repository, list their modified files explicitly in the final handoff.
