from __future__ import annotations

from contextlib import contextmanager
from dataclasses import dataclass
from datetime import datetime, timezone
from threading import Lock
from time import perf_counter
from typing import Iterator
import re


@dataclass(frozen=True)
class RuntimeSnapshot:
    model_name: str
    model_version: str
    device: str
    started_at: datetime
    busy: bool
    total_requests: int
    success_count: int
    failure_count: int
    last_inference_time_ms: int | None
    last_success_at: datetime | None
    last_error: str | None


@dataclass
class InferenceAttempt:
    request_id: str
    queue_wait_time_ms: int
    _succeeded: bool = False
    _inference_time_ms: int | None = None

    def succeed(self, inference_time_ms: int) -> None:
        self._succeeded = True
        self._inference_time_ms = inference_time_ms


class RuntimeState:
    def __init__(self, model_name: str, model_version: str, device: str):
        self._model_name = model_name
        self._model_version = model_version
        self._device = device
        self._started_at = datetime.now(timezone.utc)
        self._inference_lock = Lock()
        self._state_lock = Lock()
        self._busy = False
        self._total_requests = 0
        self._success_count = 0
        self._failure_count = 0
        self._last_inference_time_ms: int | None = None
        self._last_success_at: datetime | None = None
        self._last_error: str | None = None

    @contextmanager
    def inference_slot(self, request_id: str) -> Iterator[InferenceAttempt]:
        wait_started = perf_counter()
        self._inference_lock.acquire()
        queue_wait_time_ms = round((perf_counter() - wait_started) * 1000)
        attempt = InferenceAttempt(request_id, queue_wait_time_ms)
        with self._state_lock:
            self._busy = True
            self._total_requests += 1
        try:
            yield attempt
        except Exception as exception:
            self._record_failure(exception)
            raise
        else:
            if attempt._succeeded:
                self._record_success(attempt._inference_time_ms or 0)
            else:
                self._record_failure(RuntimeError("Inference did not complete"))
        finally:
            with self._state_lock:
                self._busy = False
            self._inference_lock.release()

    def snapshot(self) -> RuntimeSnapshot:
        with self._state_lock:
            return RuntimeSnapshot(
                model_name=self._model_name,
                model_version=self._model_version,
                device=self._device,
                started_at=self._started_at,
                busy=self._busy,
                total_requests=self._total_requests,
                success_count=self._success_count,
                failure_count=self._failure_count,
                last_inference_time_ms=self._last_inference_time_ms,
                last_success_at=self._last_success_at,
                last_error=self._last_error,
            )

    def _record_success(self, inference_time_ms: int) -> None:
        with self._state_lock:
            self._success_count += 1
            self._last_inference_time_ms = inference_time_ms
            self._last_success_at = datetime.now(timezone.utc)
            self._last_error = None

    def _record_failure(self, exception: Exception) -> None:
        detail = re.sub(r"(?:[A-Za-z]:[\\/]|/)[^\s]+", "<path>", str(exception))
        message = f"{type(exception).__name__}: {detail}"[:300]
        with self._state_lock:
            self._failure_count += 1
            self._last_error = message
