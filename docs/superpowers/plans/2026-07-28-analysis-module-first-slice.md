# Analysis Module First Slice Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Establish enforceable module boundaries and migrate the analysis-task execution path into a domain-oriented vertical slice with explicit ports, compatible adapters, and reliable task-message publication.

**Architecture:** Add a new `com.example.retinavision.analysis` module organized as `api/application/domain/infrastructure`. Keep current HTTP DTOs, controllers, database tables, queue names, and public response structures stable. Existing services become compatibility facades while the new application use case, domain state machine, and infrastructure adapters take ownership of execution behavior.

**Tech Stack:** Java 17, Spring Boot 3.5.14, MyBatis-Plus 3.5.14, Spring AMQP, MySQL 8.4, Flyway, JUnit 5, Mockito, AssertJ, ArchUnit.

## Global Constraints

- The browser API remains under `/api` and continues returning `ApiResponse<T>` and `PageResult<T>`.
- Do not change public paths, JSON field names, enum codes, error codes, queue names, routing keys, or binary response behavior in this plan.
- Keep Java as the only browser-facing business API and RabbitMQ consumer.
- Keep Python AI as an independent inference-only service; Java continues calling `POST /v1/inference/vessel-segmentation` with multipart field `file`.
- Keep Java 17, Spring Boot, Spring Security, MyBatis-Plus, Spring AMQP, MySQL, RabbitMQ, Redis, Vue 3, and FastAPI.
- Preserve all pre-existing uncommitted changes; stage and commit only files named by the current task.
- Domain code under `analysis/domain` must not import Spring, MyBatis, Jackson, RabbitMQ, Redis, HTTP, or filesystem classes.
- All task status transitions must validate the source state and produce an audit transition.
- Retain bounded, single-line error messages and never expose local paths, credentials, tokens, or patient data.
- This plan does not reorganize clinical review, reporting, knowledge, frontend, or Python packages; those require separate implementation plans.

---

## File Map

### New domain files

- `src/main/java/com/example/retinavision/analysis/domain/model/AnalysisTask.java`: task aggregate and legal state transitions.
- `src/main/java/com/example/retinavision/analysis/domain/model/AnalysisTaskStatus.java`: framework-free task states.
- `src/main/java/com/example/retinavision/analysis/domain/model/AnalysisTaskType.java`: framework-free task types.
- `src/main/java/com/example/retinavision/analysis/domain/model/TaskTransition.java`: immutable audit transition.
- `src/main/java/com/example/retinavision/analysis/domain/model/IllegalTaskTransitionException.java`: domain rejection for invalid transitions.

### New application files

- `src/main/java/com/example/retinavision/analysis/application/ExecuteAnalysisTaskUseCase.java`: public execution use case.
- `src/main/java/com/example/retinavision/analysis/application/ExecuteAnalysisTaskHandler.java`: execution orchestration.
- `src/main/java/com/example/retinavision/analysis/application/command/ExecuteAnalysisTaskCommand.java`: message-independent input.
- `src/main/java/com/example/retinavision/analysis/application/model/ExecutionDisposition.java`: ACK decision returned to the MQ adapter.
- `src/main/java/com/example/retinavision/analysis/application/model/InferenceOutput.java`: provider-neutral AI output.
- `src/main/java/com/example/retinavision/analysis/application/model/SourceImage.java`: provider-neutral image reference.
- `src/main/java/com/example/retinavision/analysis/application/model/AnalysisTaskRequestedEvent.java`: provider-neutral task publication payload.
- `src/main/java/com/example/retinavision/analysis/application/port/in/`: input-use-case contracts.
- `src/main/java/com/example/retinavision/analysis/application/port/out/`: task, image, inference, artifact, result, audit, report-draft, and Outbox ports.

### New infrastructure files

- `src/main/java/com/example/retinavision/analysis/infrastructure/persistence/LegacyAnalysisTaskRepository.java`: maps existing `TaskEntity` and `TaskMapper` to the new domain aggregate.
- `src/main/java/com/example/retinavision/analysis/infrastructure/persistence/MyBatisAnalysisResultStore.java`: maps application results to `AnalysisResultEntity`.
- `src/main/java/com/example/retinavision/analysis/infrastructure/persistence/MyBatisTaskAuditLog.java`: persists `TaskTransition` through `LogMapper`.
- `src/main/java/com/example/retinavision/analysis/infrastructure/image/MyBatisSourceImageReader.java`: loads image metadata through `ImageMapper`.
- `src/main/java/com/example/retinavision/analysis/infrastructure/ai/HttpAiInferenceGateway.java`: wraps the existing `AiInferenceClient`.
- `src/main/java/com/example/retinavision/analysis/infrastructure/storage/LocalArtifactStore.java`: resolves source paths and atomically stores masks.
- `src/main/java/com/example/retinavision/analysis/infrastructure/reporting/LegacyReportDraftGateway.java`: delegates draft creation to the existing report service.
- `src/main/java/com/example/retinavision/analysis/infrastructure/mq/AnalysisTaskMessageAdapter.java`: converts AMQP messages to application commands.
- `src/main/java/com/example/retinavision/analysis/infrastructure/outbox/`: task-event record, mapper, publisher, and scheduler.
- `src/main/resources/mapper/AnalysisOutboxMapper.xml`: Outbox SQL.
- `src/main/resources/db/migration/V5__analysis_outbox.sql`: Outbox schema.

### Modified compatibility files

- `pom.xml`: ArchUnit test dependency.
- `src/main/java/com/example/retinavision/service/impl/AnalysisTaskExecutionServiceImpl.java`: compatibility facade delegating to the new use case.
- `src/main/java/com/example/retinavision/mq/AnalysisTaskListener.java`: use application disposition through the compatibility facade without changing ACK semantics.
- `src/main/java/com/example/retinavision/service/impl/TaskServiceImpl.java`: enqueue a task through the Outbox port instead of publishing inside the transaction.
- `src/main/java/com/example/retinavision/mq/AnalysisTaskMessagePublisher.java`: retain confirmed RabbitMQ delivery as the Outbox delivery adapter.
- `src/main/resources/application.yaml`: Outbox polling and batch settings with safe defaults.
- `README.md`: document the module boundary and Outbox runtime behavior.

---

### Task 1: Enforce the New Analysis Boundary

**Files:**
- Modify: `pom.xml:32`
- Create: `src/test/java/com/example/retinavision/architecture/AnalysisModuleArchitectureTest.java`
- Create: `src/main/java/com/example/retinavision/analysis/package-info.java`
- Create: `src/main/java/com/example/retinavision/analysis/domain/package-info.java`
- Create: `src/main/java/com/example/retinavision/analysis/application/package-info.java`
- Create: `src/main/java/com/example/retinavision/analysis/infrastructure/package-info.java`

**Interfaces:**
- Consumes: existing base package `com.example.retinavision`.
- Produces: build-time rules that constrain all later files under `com.example.retinavision.analysis`.

- [ ] **Step 1: Add the failing architecture test**

```java
package com.example.retinavision.architecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

@AnalyzeClasses(
        packages = "com.example.retinavision",
        importOptions = ImportOption.DoNotIncludeTests.class)
class AnalysisModuleArchitectureTest {

    @ArchTest
    static final ArchRule domain_is_framework_free = noClasses()
            .that().resideInAPackage("..analysis.domain..")
            .should().dependOnClassesThat()
            .resideInAnyPackage(
                    "org.springframework..",
                    "com.baomidou.mybatisplus..",
                    "com.fasterxml.jackson..",
                    "com.rabbitmq..",
                    "java.nio.file..");

    @ArchTest
    static final ArchRule application_does_not_depend_on_infrastructure = noClasses()
            .that().resideInAPackage("..analysis.application..")
            .should().dependOnClassesThat()
            .resideInAPackage("..analysis.infrastructure..");

    @ArchTest
    static final ArchRule application_does_not_use_provider_or_filesystem_types = noClasses()
            .that().resideInAPackage("..analysis.application..")
            .should().dependOnClassesThat()
            .resideInAnyPackage(
                    "..retinavision.ai..",
                    "..retinavision.mq..",
                    "java.nio.file..");

    @ArchTest
    static final ArchRule infrastructure_is_not_used_by_legacy_controllers = noClasses()
            .that().resideInAPackage("..controller..")
            .should().dependOnClassesThat()
            .resideInAPackage("..analysis.infrastructure..");
}
```

- [ ] **Step 2: Run the test and verify the dependency is missing**

Run:

```powershell
mvn.cmd -Dtest=AnalysisModuleArchitectureTest test
```

Expected: test compilation fails because `com.tngtech.archunit` is unavailable.

- [ ] **Step 3: Add ArchUnit and package documentation**

Add to `pom.xml` test dependencies:

```xml
<dependency>
    <groupId>com.tngtech.archunit</groupId>
    <artifactId>archunit-junit5</artifactId>
    <version>1.4.2</version>
    <scope>test</scope>
</dependency>
```

Each `package-info.java` must describe its allowed responsibility in one paragraph. The domain package documentation must explicitly state that framework annotations and persistence entities are forbidden.

- [ ] **Step 4: Run the architecture test**

Run:

```powershell
mvn.cmd -Dtest=AnalysisModuleArchitectureTest test
```

Expected: PASS with four ArchUnit rules evaluated.

- [ ] **Step 5: Commit the boundary**

```powershell
git add pom.xml src/main/java/com/example/retinavision/analysis src/test/java/com/example/retinavision/architecture/AnalysisModuleArchitectureTest.java
git commit -m "test: establish analysis module boundary"
```

### Task 2: Introduce the Framework-Free Task State Machine

**Files:**
- Create: `src/main/java/com/example/retinavision/analysis/domain/model/AnalysisTaskStatus.java`
- Create: `src/main/java/com/example/retinavision/analysis/domain/model/AnalysisTaskType.java`
- Create: `src/main/java/com/example/retinavision/analysis/domain/model/TaskTransition.java`
- Create: `src/main/java/com/example/retinavision/analysis/domain/model/IllegalTaskTransitionException.java`
- Create: `src/main/java/com/example/retinavision/analysis/domain/model/AnalysisTask.java`
- Test: `src/test/java/com/example/retinavision/analysis/domain/model/AnalysisTaskTest.java`

**Interfaces:**
- Consumes: `java.time.LocalDateTime` only.
- Produces:
  - `AnalysisTask.waiting(long id, String taskNo, long imageFileId, AnalysisTaskType type, int retryCount, int maxRetryCount)`
  - `TaskTransition claim(LocalDateTime now)`
  - `TaskTransition succeed(LocalDateTime now)`
  - `TaskTransition fail(String safeMessage, LocalDateTime now)`
  - `TaskTransition prepareRetry(LocalDateTime now)`
  - `TaskTransition cancel(LocalDateTime now)`

- [ ] **Step 1: Write state transition tests**

```java
class AnalysisTaskTest {
    private final LocalDateTime now = LocalDateTime.of(2026, 7, 28, 12, 0);

    @Test
    void waitingTaskCanBeClaimedAndCompleted() {
        AnalysisTask task = AnalysisTask.waiting(
                100L, "TASK-100", 20L,
                AnalysisTaskType.VESSEL_SEGMENTATION, 0, 3);

        TaskTransition running = task.claim(now);
        TaskTransition success = task.succeed(now.plusSeconds(5));

        assertThat(running).isEqualTo(new TaskTransition(
                AnalysisTaskStatus.WAITING, AnalysisTaskStatus.RUNNING,
                "AI Worker 已接收任务，开始处理", now));
        assertThat(success.to()).isEqualTo(AnalysisTaskStatus.SUCCESS);
        assertThat(task.status()).isEqualTo(AnalysisTaskStatus.SUCCESS);
    }

    @Test
    void completedTaskCannotBeClaimedAgain() {
        AnalysisTask task = AnalysisTask.rehydrate(
                100L, "TASK-100", 20L,
                AnalysisTaskType.VESSEL_SEGMENTATION,
                AnalysisTaskStatus.SUCCESS, 0, 3, null);

        assertThatThrownBy(() -> task.claim(now))
                .isInstanceOf(IllegalTaskTransitionException.class)
                .hasMessage("任务不能从 SUCCESS 转换为 RUNNING");
    }

    @Test
    void failedTaskCanRetryOnlyBelowLimit() {
        AnalysisTask task = AnalysisTask.rehydrate(
                100L, "TASK-100", 20L,
                AnalysisTaskType.VESSEL_SEGMENTATION,
                AnalysisTaskStatus.FAILED, 2, 3, "previous failure");

        task.prepareRetry(now);

        assertThat(task.status()).isEqualTo(AnalysisTaskStatus.RETRYING);
        assertThat(task.retryCount()).isEqualTo(3);
        assertThat(task.errorMessage()).isNull();
    }
}
```

- [ ] **Step 2: Run tests and verify the domain types do not exist**

Run:

```powershell
mvn.cmd -Dtest=AnalysisTaskTest test
```

Expected: test compilation fails on unresolved `AnalysisTask`.

- [ ] **Step 3: Implement the minimal domain model**

Use plain Java enums with the exact codes:

```java
public enum AnalysisTaskStatus {
    CREATED, WAITING, RUNNING, SUCCESS, FAILED, RETRYING, CANCELED
}

public enum AnalysisTaskType {
    IMAGE_QUALITY_CHECK, VESSEL_SEGMENTATION
}
```

Implement `AnalysisTask` as a final class with private fields, static factories, read-only accessor methods, and transition methods. `fail` must reject blank messages. `prepareRetry` must require `FAILED` and `retryCount < maxRetryCount`. `cancel` must accept only `CREATED` or `WAITING`. No framework annotation is permitted.

- [ ] **Step 4: Run domain and architecture tests**

Run:

```powershell
mvn.cmd -Dtest=AnalysisTaskTest,AnalysisModuleArchitectureTest test
```

Expected: PASS.

- [ ] **Step 5: Commit the domain model**

```powershell
git add src/main/java/com/example/retinavision/analysis/domain src/test/java/com/example/retinavision/analysis/domain
git commit -m "feat: model analysis task transitions"
```

### Task 3: Define the Execution Use Case and Output Ports

**Files:**
- Create: `src/main/java/com/example/retinavision/analysis/application/ExecuteAnalysisTaskUseCase.java`
- Create: `src/main/java/com/example/retinavision/analysis/application/ExecuteAnalysisTaskHandler.java`
- Create: `src/main/java/com/example/retinavision/analysis/application/command/ExecuteAnalysisTaskCommand.java`
- Create: `src/main/java/com/example/retinavision/analysis/application/model/ExecutionDisposition.java`
- Create: `src/main/java/com/example/retinavision/analysis/application/model/InferenceOutput.java`
- Create: `src/main/java/com/example/retinavision/analysis/application/model/SourceImage.java`
- Create: `src/main/java/com/example/retinavision/analysis/application/port/out/AnalysisTaskRepository.java`
- Create: `src/main/java/com/example/retinavision/analysis/application/port/out/SourceImageReader.java`
- Create: `src/main/java/com/example/retinavision/analysis/application/port/out/AiInferencePort.java`
- Create: `src/main/java/com/example/retinavision/analysis/application/port/out/ArtifactStore.java`
- Create: `src/main/java/com/example/retinavision/analysis/application/port/out/AnalysisResultStore.java`
- Create: `src/main/java/com/example/retinavision/analysis/application/port/out/TaskAuditLog.java`
- Create: `src/main/java/com/example/retinavision/analysis/application/port/out/ReportDraftPort.java`
- Test: `src/test/java/com/example/retinavision/analysis/application/ExecuteAnalysisTaskHandlerTest.java`

**Interfaces:**
- Consumes: domain types from Task 2.
- Produces:

```java
public interface ExecuteAnalysisTaskUseCase {
    ExecutionDisposition execute(ExecuteAnalysisTaskCommand command);
}

public record ExecuteAnalysisTaskCommand(long taskId, String traceId) {}

public enum ExecutionDisposition { SUCCESS, FAILED, IGNORED, REQUEUE }

public interface AnalysisTaskRepository {
    Optional<AnalysisTask> findById(long taskId);
    boolean claim(AnalysisTask task, LocalDateTime startedAt);
    void save(AnalysisTask task);
}

public interface AiInferencePort {
    InferenceOutput checkQuality(SourceImage image, String traceId);
    InferenceOutput segment(SourceImage image, String traceId);
    byte[] downloadMask(String artifactUrl, String traceId);
}
```

- [ ] **Step 1: Write orchestration tests with in-memory ports**

Create tests for:

1. A missing task returns `REQUEUE`.
2. A stale or completed task returns `IGNORED`.
3. Losing the atomic claim returns `IGNORED` and never calls AI.
4. Vessel segmentation calls AI, stores the mask, saves one result, moves the task to `SUCCESS`, and writes `WAITING -> RUNNING -> SUCCESS` audit entries.
5. AI failure moves the claimed task to `FAILED`, writes a bounded single-line message, and returns `FAILED`.
6. Quality detection stores the quality projection but never downloads a mask.

The success assertion must use the exact command:

```java
ExecutionDisposition result = handler.execute(
        new ExecuteAnalysisTaskCommand(100L, "trace-100"));

assertThat(result).isEqualTo(ExecutionDisposition.SUCCESS);
assertThat(taskRepository.saved().status())
        .isEqualTo(AnalysisTaskStatus.SUCCESS);
assertThat(auditLog.transitions())
        .extracting(TaskTransition::to)
        .containsExactly(AnalysisTaskStatus.RUNNING, AnalysisTaskStatus.SUCCESS);
```

- [ ] **Step 2: Run tests and verify the use case is missing**

Run:

```powershell
mvn.cmd -Dtest=ExecuteAnalysisTaskHandlerTest test
```

Expected: test compilation fails on unresolved application types.

- [ ] **Step 3: Implement contracts and the minimal handler**

The handler constructor must receive all seven output ports plus `java.time.Clock`. It must:

1. Return `REQUEUE` when `findById` is empty.
2. Return `REQUEUE` for a persisted `FAILED` task to preserve the existing transaction-visibility behavior.
3. Return `IGNORED` for non-consumable states.
4. Call `task.claim(clock time)` and then `repository.claim`; if the atomic claim returns false, return `IGNORED`.
5. Load the image only after the claim succeeds.
6. Branch only on `IMAGE_QUALITY_CHECK` and `VESSEL_SEGMENTATION`.
7. Store result data before marking the aggregate successful.
8. Sanitize exceptions to `"AI 任务执行失败：" + singleLineMessage`, capped at 1024 characters.
9. Persist failure and audit it before returning `FAILED`.

- [ ] **Step 4: Run application, domain, and architecture tests**

Run:

```powershell
mvn.cmd -Dtest=ExecuteAnalysisTaskHandlerTest,AnalysisTaskTest,AnalysisModuleArchitectureTest test
```

Expected: PASS.

- [ ] **Step 5: Commit the use case**

```powershell
git add src/main/java/com/example/retinavision/analysis/application src/test/java/com/example/retinavision/analysis/application
git commit -m "feat: add analysis execution use case"
```

### Task 4: Implement Persistence, AI, Image, and Storage Adapters

**Files:**
- Create: `src/main/java/com/example/retinavision/analysis/infrastructure/persistence/LegacyAnalysisTaskRepository.java`
- Create: `src/main/java/com/example/retinavision/analysis/infrastructure/persistence/MyBatisAnalysisResultStore.java`
- Create: `src/main/java/com/example/retinavision/analysis/infrastructure/persistence/MyBatisTaskAuditLog.java`
- Create: `src/main/java/com/example/retinavision/analysis/infrastructure/image/MyBatisSourceImageReader.java`
- Create: `src/main/java/com/example/retinavision/analysis/infrastructure/ai/HttpAiInferenceGateway.java`
- Create: `src/main/java/com/example/retinavision/analysis/infrastructure/storage/LocalArtifactStore.java`
- Create: `src/main/java/com/example/retinavision/analysis/infrastructure/reporting/LegacyReportDraftGateway.java`
- Test: `src/test/java/com/example/retinavision/analysis/infrastructure/persistence/LegacyAnalysisTaskRepositoryTest.java`
- Test: `src/test/java/com/example/retinavision/analysis/infrastructure/storage/LocalArtifactStoreTest.java`
- Test: `src/test/java/com/example/retinavision/analysis/infrastructure/ai/HttpAiInferenceGatewayTest.java`

**Interfaces:**
- Consumes: all Task 3 output-port signatures and the existing `TaskMapper`, `ImageMapper`, `AnalysisResultMapper`, `LogMapper`, `AiInferenceClient`, and `AnalysisReportService`.
- Produces: Spring beans implementing every output port required by `ExecuteAnalysisTaskHandler`.

- [ ] **Step 1: Write adapter tests**

Repository mapping must preserve:

```java
assertThat(repository.findById(100L).orElseThrow())
        .extracting(AnalysisTask::status, AnalysisTask::type)
        .containsExactly(
                AnalysisTaskStatus.WAITING,
                AnalysisTaskType.VESSEL_SEGMENTATION);
```

Storage tests must verify:

- `../outside.png` is rejected.
- an absent source file is rejected.
- an empty mask is rejected.
- a valid mask is first written to a sibling temporary file and then moved to `tasks/{taskId}/mask.png`.
- no temporary file remains after success.

AI gateway tests must verify that application types contain no `AiInferenceResponse` or `Path` from the provider adapter.

- [ ] **Step 2: Run adapter tests and verify implementations are missing**

Run:

```powershell
mvn.cmd -Dtest=LegacyAnalysisTaskRepositoryTest,LocalArtifactStoreTest,HttpAiInferenceGatewayTest test
```

Expected: test compilation fails on unresolved adapter classes.

- [ ] **Step 3: Implement mapping and safe storage**

`LegacyAnalysisTaskRepository` maps the legacy persistence enums by name:

```java
private AnalysisTaskStatus toDomain(TaskStatus status) {
    return AnalysisTaskStatus.valueOf(status.name());
}

private TaskStatus toPersistence(AnalysisTaskStatus status) {
    return TaskStatus.valueOf(status.name());
}
```

`LocalArtifactStore` must normalize configured roots once in its constructor. Mask storage uses:

```java
Path target = resultRoot.resolve("tasks/" + taskId + "/mask.png").normalize();
if (!target.startsWith(resultRoot)) {
    throw new IllegalArgumentException("分割结果图存储路径无效");
}
Files.createDirectories(target.getParent());
Path temporary = Files.createTempFile(target.getParent(), "mask-", ".tmp");
try {
    Files.write(temporary, bytes);
    Files.move(temporary, target,
            StandardCopyOption.REPLACE_EXISTING,
            StandardCopyOption.ATOMIC_MOVE);
} finally {
    Files.deleteIfExists(temporary);
}
```

If the filesystem does not support `ATOMIC_MOVE`, fail the operation; do not silently leave a partially written target.

- [ ] **Step 4: Run adapter and architecture tests**

Run:

```powershell
mvn.cmd -Dtest=LegacyAnalysisTaskRepositoryTest,LocalArtifactStoreTest,HttpAiInferenceGatewayTest,AnalysisModuleArchitectureTest test
```

Expected: PASS.

- [ ] **Step 5: Commit adapters**

```powershell
git add src/main/java/com/example/retinavision/analysis/infrastructure src/test/java/com/example/retinavision/analysis/infrastructure
git commit -m "feat: add analysis infrastructure adapters"
```

### Task 5: Route the Existing Worker Through the New Use Case

**Files:**
- Modify: `src/main/java/com/example/retinavision/service/impl/AnalysisTaskExecutionServiceImpl.java:40`
- Modify: `src/main/java/com/example/retinavision/mq/AnalysisTaskListener.java:12`
- Create: `src/main/java/com/example/retinavision/analysis/infrastructure/mq/AnalysisTaskMessageAdapter.java`
- Modify: `src/test/java/com/example/retinavision/service/impl/AnalysisTaskExecutionServiceImplTest.java`
- Modify: `src/test/java/com/example/retinavision/mq/AnalysisTaskListenerTest.java`
- Test: `src/test/java/com/example/retinavision/analysis/infrastructure/mq/AnalysisTaskMessageAdapterTest.java`

**Interfaces:**
- Consumes:
  - `ExecuteAnalysisTaskUseCase.execute(ExecuteAnalysisTaskCommand)`
  - legacy `AnalysisTaskExecutionService.process(AnalysisTaskMessage)`
- Produces: unchanged legacy `ExecutionDisposition` values for the existing listener.

- [ ] **Step 1: Replace implementation-focused tests with delegation tests**

The compatibility service test must assert:

```java
when(useCase.execute(any())).thenReturn(
        com.example.retinavision.analysis.application.model.ExecutionDisposition.SUCCESS);

AnalysisTaskExecutionService.ExecutionDisposition result =
        service.process(AnalysisTaskMessage.builder().taskId(100L).build());

assertThat(result).isEqualTo(
        AnalysisTaskExecutionService.ExecutionDisposition.SUCCESS);
verify(useCase).execute(argThat(command ->
        command.taskId() == 100L &&
        command.traceId().startsWith("task-100-")));
```

Retain listener tests for ACK, requeue, delayed retry, dead letter, and duplicate ACK behavior.

- [ ] **Step 2: Run tests and verify the old constructor no longer matches**

Run:

```powershell
mvn.cmd -Dtest=AnalysisTaskExecutionServiceImplTest,AnalysisTaskMessageAdapterTest,AnalysisTaskListenerTest test
```

Expected: FAIL until the facade and adapter are implemented.

- [ ] **Step 3: Turn the legacy service into a compatibility facade**

`AnalysisTaskExecutionServiceImpl` must contain only:

- `ExecuteAnalysisTaskUseCase`
- `AnalysisTaskMessageAdapter`
- mapping between the new and legacy `ExecutionDisposition` enums

Remove direct Mapper, filesystem, JSON, AI, and report-service dependencies from this class. `AnalysisTaskMessageAdapter` returns an empty optional for null messages or null task IDs; the facade maps that to `IGNORED`.

- [ ] **Step 4: Run worker and architecture tests**

Run:

```powershell
mvn.cmd -Dtest=AnalysisTaskExecutionServiceImplTest,AnalysisTaskMessageAdapterTest,AnalysisTaskListenerTest,AnalysisModuleArchitectureTest test
```

Expected: PASS and the listener's manual ACK semantics remain unchanged.

- [ ] **Step 5: Commit the worker migration**

```powershell
git add src/main/java/com/example/retinavision/service/impl/AnalysisTaskExecutionServiceImpl.java src/main/java/com/example/retinavision/mq/AnalysisTaskListener.java src/main/java/com/example/retinavision/analysis/infrastructure/mq src/test/java/com/example/retinavision/service/impl/AnalysisTaskExecutionServiceImplTest.java src/test/java/com/example/retinavision/mq/AnalysisTaskListenerTest.java src/test/java/com/example/retinavision/analysis/infrastructure/mq
git commit -m "refactor: route analysis worker through use case"
```

### Task 6: Replace Transactional MQ Publication with an Outbox

**Files:**
- Create: `src/main/resources/db/migration/V5__analysis_outbox.sql`
- Create: `src/main/java/com/example/retinavision/analysis/infrastructure/outbox/AnalysisOutboxEntity.java`
- Create: `src/main/java/com/example/retinavision/analysis/infrastructure/outbox/AnalysisOutboxMapper.java`
- Create: `src/main/resources/mapper/AnalysisOutboxMapper.xml`
- Create: `src/main/java/com/example/retinavision/analysis/application/model/AnalysisTaskRequestedEvent.java`
- Create: `src/main/java/com/example/retinavision/analysis/application/port/out/AnalysisTaskEventOutbox.java`
- Create: `src/main/java/com/example/retinavision/analysis/infrastructure/outbox/MyBatisAnalysisTaskEventOutbox.java`
- Create: `src/main/java/com/example/retinavision/analysis/infrastructure/outbox/AnalysisOutboxPublisher.java`
- Create: `src/main/java/com/example/retinavision/analysis/infrastructure/outbox/AnalysisOutboxProperties.java`
- Modify: `src/main/java/com/example/retinavision/service/impl/TaskServiceImpl.java:107`
- Modify: `src/main/resources/application.yaml`
- Modify: `src/test/java/com/example/retinavision/service/impl/TaskServiceImplTest.java`
- Test: `src/test/java/com/example/retinavision/analysis/infrastructure/outbox/AnalysisOutboxPublisherTest.java`

**Interfaces:**
- Consumes: existing `AnalysisTaskMessagePublisher.publish(AnalysisTaskMessage)`.
- Produces:

```java
public record AnalysisTaskRequestedEvent(
        long taskId,
        String taskNo,
        long caseId,
        long imageFileId,
        AnalysisTaskType taskType,
        int priority,
        int submittedBy,
        LocalDateTime submittedAt) {}

public interface AnalysisTaskEventOutbox {
    void append(AnalysisTaskRequestedEvent event, String eventType, int eventVersion);
}
```

The persisted event key is `taskId:eventType:eventVersion`.

- [ ] **Step 1: Add migration and mapper tests**

Create the migration with these exact columns:

```sql
CREATE TABLE analysis_outbox (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    event_key VARCHAR(160) NOT NULL,
    aggregate_id BIGINT NOT NULL,
    event_type VARCHAR(80) NOT NULL,
    event_version INT NOT NULL,
    payload_json JSON NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    attempt_count INT NOT NULL DEFAULT 0,
    next_attempt_at DATETIME NOT NULL,
    published_at DATETIME NULL,
    last_error VARCHAR(1024) NULL,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    UNIQUE KEY uk_analysis_outbox_event_key (event_key),
    INDEX idx_analysis_outbox_poll (status, next_attempt_at, id)
);
```

`AnalysisOutboxPublisherTest` must verify:

- confirmed publication marks an event `PUBLISHED`;
- publication failure increments `attempt_count`, retains `PENDING`, stores a bounded single-line error, and moves `next_attempt_at` forward;
- the publisher never marks an event published before RabbitMQ confirm;
- two publisher invocations do not publish an already published event.
- concurrent pollers cannot claim the same `PENDING` row;
- a row left in `PROCESSING` by a crashed publisher is returned to `PENDING` after the configured claim timeout.

- [ ] **Step 2: Run Outbox and task-service tests**

Run:

```powershell
mvn.cmd -Dtest=AnalysisOutboxPublisherTest,TaskServiceImplTest test
```

Expected: FAIL because the Outbox types and mapper do not exist.

- [ ] **Step 3: Implement Outbox append and polling**

`TaskServiceImpl.createTask` must call:

```java
analysisTaskEventOutbox.append(
        new AnalysisTaskRequestedEvent(
                taskEntity.getId(),
                taskEntity.getTaskNo(),
                taskEntity.getCaseId(),
                taskEntity.getImageFileId(),
                AnalysisTaskType.valueOf(taskEntity.getTaskType().name()),
                taskEntity.getPriority(),
                taskEntity.getSubmittedBy(),
                taskEntity.getSubmittedAt()),
        "ANALYSIS_TASK_REQUESTED",
        1);
```

Remove direct `analysisTaskMessagePublisher.publish(...)` from task creation. Keep task insert, quality projection, task log, and Outbox append in the same `@Transactional` method.

`AnalysisOutboxPublisher` atomically claims at most `batchSize` due rows by changing `PENDING` to `PROCESSING`. It converts each provider-neutral event into the existing `AnalysisTaskMessage`, publishes through the confirmed publisher, then marks the claimed row `PUBLISHED`. A failed delivery returns the row to `PENDING` with a delayed `next_attempt_at`. Configure:

```yaml
retina:
  outbox:
    fixed-delay: 1s
    batch-size: 50
    retry-delay: 10s
    claim-timeout: 2m
```

Use conditional update predicates on `id` and current `status` for claiming and finalization. At the start of each poll, recover `PROCESSING` rows whose `updated_at` is older than `claim-timeout`. A crash after broker confirm but before `PUBLISHED` may cause a duplicate delivery; the task consumer's atomic claim must reduce that duplicate to `IGNORED`. Preserve publisher confirm behavior.

- [ ] **Step 4: Update task-service expectations**

Change the task creation test name to `createTaskCommitsWaitingTaskAndOutboxEvent`. Capture the appended message and assert:

```java
verify(analysisTaskEventOutbox).append(
        argThat(event -> event.taskId() == task.getId()),
        eq("ANALYSIS_TASK_REQUESTED"),
        eq(1));
verifyNoInteractions(analysisTaskMessagePublisher);
```

Delete the old test that expects task creation to fail when immediate MQ publication fails. Replace it with a test proving that Outbox append failure rolls back task creation and releases the quota reservation.

- [ ] **Step 5: Run focused tests and full backend tests**

Run:

```powershell
mvn.cmd -Dtest=AnalysisOutboxPublisherTest,TaskServiceImplTest,AnalysisTaskListenerTest test
mvn.cmd test
```

Expected: both commands PASS.

- [ ] **Step 6: Commit reliable publication**

```powershell
git add src/main/resources/db/migration/V5__analysis_outbox.sql src/main/resources/mapper/AnalysisOutboxMapper.xml src/main/resources/application.yaml src/main/java/com/example/retinavision/analysis/application/model/AnalysisTaskRequestedEvent.java src/main/java/com/example/retinavision/analysis/application/port/out/AnalysisTaskEventOutbox.java src/main/java/com/example/retinavision/analysis/infrastructure/outbox src/main/java/com/example/retinavision/service/impl/TaskServiceImpl.java src/test/java/com/example/retinavision/analysis/infrastructure/outbox src/test/java/com/example/retinavision/service/impl/TaskServiceImplTest.java
git commit -m "feat: publish analysis tasks through outbox"
```

### Task 7: Document and Verify the First Slice

**Files:**
- Modify: `README.md`
- Modify: `docs/superpowers/specs/2026-07-28-retinavision-modular-architecture-design.md`
- Create: `docs/architecture/adr/0001-analysis-module-boundary.md`
- Create: `docs/architecture/adr/0002-transactional-outbox.md`

**Interfaces:**
- Consumes: implemented module boundaries and runtime behavior from Tasks 1–6.
- Produces: durable architectural decisions and verified operator guidance.

- [ ] **Step 1: Write ADR 0001**

Record:

- Context: layer-oriented packages created broad Mapper and infrastructure coupling.
- Decision: migrate one vertical slice to `analysis/api/application/domain/infrastructure`.
- Consequences: temporary legacy compatibility facades remain; cross-module migration is incremental.
- Enforcement: `AnalysisModuleArchitectureTest`.

- [ ] **Step 2: Write ADR 0002**

Record:

- Context: synchronous MQ publication inside task creation creates a database/message dual-write window.
- Decision: write `analysis_outbox` in the task transaction and publish asynchronously with broker confirm.
- Consequences: task creation no longer proves immediate queue delivery; operations must monitor pending age and failures.
- Recovery: pending rows retry automatically; published rows are retained according to the documented retention policy.

- [ ] **Step 3: Update README and design status**

README must describe:

- the new analysis module boundary;
- task creation now means “persisted and scheduled for delivery,” not “already present in RabbitMQ”;
- Outbox configuration keys;
- how to inspect pending Outbox records without printing payloads containing sensitive fields;
- the exact verification command `mvn test`.

In the design document, mark only architecture baseline, analysis execution slice, and Outbox publication as implemented. Do not mark the remaining modules, frontend reorganization, Python reorganization, OpenTelemetry, or Testcontainers as complete.

- [ ] **Step 4: Run final verification**

Run:

```powershell
mvn.cmd test
git diff --check
git status --short
```

Expected:

- Maven reports all tests passing.
- `git diff --check` prints no whitespace errors.
- `git status --short` shows only intended documentation changes for this task plus pre-existing user changes.

- [ ] **Step 5: Commit documentation**

```powershell
git add README.md docs/architecture/adr/0001-analysis-module-boundary.md docs/architecture/adr/0002-transactional-outbox.md docs/superpowers/specs/2026-07-28-retinavision-modular-architecture-design.md
git commit -m "docs: record analysis architecture decisions"
```

---

## Deferred Follow-Up Plans

The approved design is intentionally split into independently testable plans. After this plan, create separate plans in this order:

1. `clinical-workflow` and `reporting` module extraction, including optimistic locking and versioned report signing.
2. `case-management` and `identity` boundaries, including object-level authorization.
3. observability baseline with shared trace context, structured logging, metrics, and health semantics.
4. frontend feature slicing, beginning with `TaskDetail.vue`.
5. Python AI internal layering and Java—Python contract tests.
6. Testcontainers-based infrastructure tests and a minimal end-to-end clinical workflow.

Each follow-up plan must preserve the public browser contract and produce working, independently verifiable software.
