from pathlib import Path
import sys

import httpx
import pytest


sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "src"))

from retinavision_ai.api import create_app  # noqa: E402
from retinavision_ai.schemas import InferenceRecord  # noqa: E402
from retinavision_ai.quality import QualityEvaluation  # noqa: E402


class FakeInferenceService:
    model_name = "fake-fscnet"
    model_version = "test-v1"
    device = "cpu"

    def __init__(self, mask_path: Path):
        self._mask_path = mask_path

    def infer(self, filename: str, content: bytes) -> InferenceRecord:
        assert filename == "retina.png"
        assert content == b"fake-image"
        return InferenceRecord(
            inference_id="abc123",
            vessel_area_ratio=0.25,
            processing_time_ms=12,
            mask_path=self._mask_path,
        )

    def get_mask_path(self, inference_id: str) -> Path:
        assert inference_id == "abc123"
        return self._mask_path


class FakeQualityEvaluator:
    model_name = "retinavision-rule-quality"
    model_version = "1.0.0"

    def evaluate(self, content: bytes) -> QualityEvaluation:
        assert content == b"fake-image"
        return QualityEvaluation(
            grade="PASS",
            score=88.5,
            metrics={
                "sharpness": 0.8,
                "exposure": 0.9,
                "contrast": 0.7,
                "fieldCoverage": 0.95,
                "illuminationUniformity": 0.85,
            },
            reasons=[],
            processing_time_ms=7,
        )


@pytest.fixture
def anyio_backend():
    return "asyncio"


@pytest.mark.anyio
async def test_health_reports_ready_model(tmp_path: Path):
    mask = tmp_path / "mask.png"
    mask.write_bytes(b"png")
    transport = httpx.ASGITransport(app=create_app(FakeInferenceService(mask)))
    async with httpx.AsyncClient(transport=transport, base_url="http://test") as client:
        response = await client.get("/health")

    assert response.status_code == 200
    assert response.json() == {
        "status": "UP",
        "ready": True,
        "modelLoaded": True,
        "modelName": "fake-fscnet",
        "modelVersion": "test-v1",
        "device": "cpu",
        "busy": False,
        "startedAt": response.json()["startedAt"],
        "totalRequests": 0,
        "successCount": 0,
        "failureCount": 0,
        "lastInferenceTimeMs": None,
        "lastSuccessAt": None,
        "lastError": None,
    }


@pytest.mark.anyio
async def test_segment_returns_contract_and_mask_download(tmp_path: Path):
    mask = tmp_path / "mask.png"
    mask.write_bytes(b"png-mask")
    transport = httpx.ASGITransport(app=create_app(FakeInferenceService(mask)))
    async with httpx.AsyncClient(transport=transport, base_url="http://test") as client:
        response = await client.post(
            "/v1/inference/vessel-segmentation",
            headers={"X-Request-ID": "java-task-100"},
            files={"file": ("retina.png", b"fake-image", "image/png")},
        )
        assert response.status_code == 200
        body = response.json()
        mask_response = await client.get(body["maskUrl"])
        health = await client.get("/health")

    assert body["inferenceId"] == "abc123"
    assert body["resultType"] == "VESSEL_SEGMENTATION"
    assert body["resultJson"]["vesselAreaRatio"] == 0.25
    assert body["modelName"] == "fake-fscnet"
    assert body["maskUrl"] == "/v1/artifacts/abc123/mask"
    assert mask_response.status_code == 200
    assert mask_response.content == b"png-mask"
    assert response.headers["X-Request-ID"] == "java-task-100"
    assert health.json()["totalRequests"] == 1
    assert health.json()["successCount"] == 1
    assert health.json()["failureCount"] == 0
    assert health.json()["lastInferenceTimeMs"] == 12
    assert health.json()["lastSuccessAt"] is not None


@pytest.mark.anyio
async def test_unexpected_inference_failure_updates_health_and_releases_slot(tmp_path: Path):
    class FailOnceInferenceService(FakeInferenceService):
        def __init__(self, mask_path: Path):
            super().__init__(mask_path)
            self.calls = 0

        def infer(self, filename: str, content: bytes) -> InferenceRecord:
            self.calls += 1
            if self.calls == 1:
                raise RuntimeError("GPU unavailable at C:/secret/model.pth")
            return super().infer(filename, content)

    mask = tmp_path / "mask.png"
    mask.write_bytes(b"png")
    app = create_app(FailOnceInferenceService(mask))
    transport = httpx.ASGITransport(app=app, raise_app_exceptions=False)
    async with httpx.AsyncClient(transport=transport, base_url="http://test") as client:
        failed = await client.post(
            "/v1/inference/vessel-segmentation",
            headers={"X-Request-ID": "java-task-failed"},
            files={"file": ("retina.png", b"fake-image", "image/png")},
        )
        failed_health = await client.get("/health")
        succeeded = await client.post(
            "/v1/inference/vessel-segmentation",
            files={"file": ("retina.png", b"fake-image", "image/png")},
        )
        final_health = await client.get("/health")

    assert failed.status_code == 500
    assert failed.headers["X-Request-ID"] == "java-task-failed"
    assert failed.json() == {"detail": "AI inference failed"}
    assert failed_health.json()["busy"] is False
    assert failed_health.json()["failureCount"] == 1
    assert "C:/secret" not in failed_health.json()["lastError"]
    assert succeeded.status_code == 200
    assert final_health.json()["successCount"] == 1


@pytest.mark.anyio
async def test_segment_rejects_non_image_upload(tmp_path: Path):
    mask = tmp_path / "mask.png"
    mask.write_bytes(b"png")
    transport = httpx.ASGITransport(app=create_app(FakeInferenceService(mask)))
    async with httpx.AsyncClient(transport=transport, base_url="http://test") as client:
        response = await client.post(
            "/v1/inference/vessel-segmentation",
            files={"file": ("notes.txt", b"hello", "text/plain")},
        )

    assert response.status_code == 415


@pytest.mark.anyio
async def test_quality_check_returns_stable_contract(tmp_path: Path):
    mask = tmp_path / "mask.png"
    mask.write_bytes(b"png")
    app = create_app(FakeInferenceService(mask), FakeQualityEvaluator())
    transport = httpx.ASGITransport(app=app)
    async with httpx.AsyncClient(transport=transport, base_url="http://test") as client:
        response = await client.post(
            "/v1/inference/image-quality-check",
            files={"file": ("retina.png", b"fake-image", "image/png")},
        )

    assert response.status_code == 200
    assert response.json() == {
        "resultType": "IMAGE_QUALITY_CHECK",
        "resultJson": {
            "grade": "PASS",
            "score": 88.5,
            "metrics": {
                "sharpness": 0.8,
                "exposure": 0.9,
                "contrast": 0.7,
                "fieldCoverage": 0.95,
                "illuminationUniformity": 0.85,
            },
            "reasons": [],
        },
        "modelName": "retinavision-rule-quality",
        "modelVersion": "1.0.0",
        "processingTimeMs": 7,
    }
