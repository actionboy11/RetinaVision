from pathlib import Path
import sys
import threading

import pytest


sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "src"))

from retinavision_ai.runtime_state import RuntimeState  # noqa: E402


def test_runtime_state_starts_idle_with_zero_counters():
    state = RuntimeState("model", "v1", "cpu")

    snapshot = state.snapshot()

    assert snapshot.busy is False
    assert snapshot.total_requests == 0
    assert snapshot.success_count == 0
    assert snapshot.failure_count == 0
    assert snapshot.last_inference_time_ms is None
    assert snapshot.last_error is None


def test_successful_inference_updates_runtime_snapshot():
    state = RuntimeState("model", "v1", "cpu")

    with state.inference_slot("request-1") as attempt:
        assert state.snapshot().busy is True
        attempt.succeed(123)

    snapshot = state.snapshot()
    assert snapshot.busy is False
    assert snapshot.total_requests == 1
    assert snapshot.success_count == 1
    assert snapshot.failure_count == 0
    assert snapshot.last_inference_time_ms == 123
    assert snapshot.last_success_at is not None
    assert snapshot.last_error is None


def test_inference_slot_releases_lock_after_failure():
    state = RuntimeState("model", "v1", "cpu")

    with pytest.raises(RuntimeError, match="boom"):
        with state.inference_slot("request-1"):
            raise RuntimeError("boom")

    snapshot = state.snapshot()
    assert snapshot.busy is False
    assert snapshot.failure_count == 1
    assert snapshot.last_error == "RuntimeError: boom"

    with state.inference_slot("request-2") as attempt:
        attempt.succeed(10)
    assert state.snapshot().success_count == 1


def test_only_one_thread_can_hold_inference_slot():
    state = RuntimeState("model", "v1", "cpu")
    first_entered = threading.Event()
    release_first = threading.Event()
    second_entered = threading.Event()

    def first_request():
        with state.inference_slot("request-1") as attempt:
            first_entered.set()
            assert release_first.wait(timeout=2)
            attempt.succeed(20)

    def second_request():
        assert first_entered.wait(timeout=2)
        with state.inference_slot("request-2") as attempt:
            second_entered.set()
            attempt.succeed(30)

    first = threading.Thread(target=first_request)
    second = threading.Thread(target=second_request)
    first.start()
    second.start()

    assert first_entered.wait(timeout=2)
    assert second_entered.wait(timeout=0.1) is False
    release_first.set()
    assert second_entered.wait(timeout=2)

    first.join(timeout=2)
    second.join(timeout=2)
    assert first.is_alive() is False
    assert second.is_alive() is False
    assert state.snapshot().success_count == 2
