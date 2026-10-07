from contextlib import asynccontextmanager
import logging
from pathlib import Path
import re
from time import perf_counter
from typing import Protocol
from uuid import uuid4

from fastapi import FastAPI, File, HTTPException, Request, Response, UploadFile
from fastapi.responses import FileResponse
from starlette.concurrency import run_in_threadpool
import torch

from .config import Settings
from .inference import InferenceConfig, VesselSegmenter
from .model_loader import load_fscnet_model
from .runtime_state import RuntimeState
from .schemas import HealthResponse, ImageQualityResponse, InferenceRecord, QualityResultJson, VesselResultJson, VesselSegmentationResponse
from .service import VesselInferenceService
from .quality import ImageQualityEvaluator, QualityConfig, RuleBasedImageQualityEvaluator


MAX_UPLOAD_BYTES = 20 * 1024 * 1024
REQUEST_ID_PATTERN = re.compile(r"^[A-Za-z0-9._:-]{1,128}$")
logger = logging.getLogger(__name__)


class InferenceService(Protocol):
    model_name: str
    model_version: str
    device: str

    def infer(self, filename: str, content: bytes) -> InferenceRecord: ...

    def get_mask_path(self, inference_id: str) -> Path: ...


def build_default_service() -> VesselInferenceService:
    settings = Settings()
    device = torch.device("cuda:0" if torch.cuda.is_available() else "cpu")
    model = load_fscnet_model(settings.model_path, device)
    segmenter = VesselSegmenter(
        model,
        device,
        InferenceConfig(
            window_size=settings.window_size,
            stride=settings.stride,
            batch_size=settings.batch_size,
            threshold=settings.threshold,
        ),
    )
    return VesselInferenceService(
        segmenter,
        settings.storage_root,
        settings.model_name,
        settings.model_version,
    )


def build_default_quality_evaluator() -> RuleBasedImageQualityEvaluator:
    settings = Settings()
    return RuleBasedImageQualityEvaluator(QualityConfig(
        blur_threshold=settings.quality_blur_threshold,
        underexposed_mean=settings.quality_underexposed_mean,
        overexposed_mean=settings.quality_overexposed_mean,
        contrast_threshold=settings.quality_contrast_threshold,
        field_coverage_threshold=settings.quality_field_coverage_threshold,
        illumination_uniformity_threshold=settings.quality_illumination_uniformity_threshold,
        saturated_pixel_ratio_threshold=settings.quality_saturated_pixel_ratio_threshold,
        target_field_coverage=settings.quality_target_field_coverage,
        warning_score=settings.quality_warning_score,
        pass_score=settings.quality_pass_score,
    ))


def create_app(
    service: InferenceService | None = None,
    quality_evaluator: ImageQualityEvaluator | None = None,
) -> FastAPI:
    def install_service(app: FastAPI, inference_service: InferenceService, evaluator: ImageQualityEvaluator) -> None:
        app.state.inference_service = inference_service
        app.state.quality_evaluator = evaluator
        app.state.runtime_state = RuntimeState(
            inference_service.model_name,
            inference_service.model_version,
            inference_service.device,
        )

    @asynccontextmanager
    async def lifespan(app: FastAPI):
        if service is None:
            install_service(app, build_default_service(), quality_evaluator or build_default_quality_evaluator())
        yield

    app = FastAPI(title="RetinaVision AI", version="1.0.0", lifespan=lifespan)
    if service is not None:
        install_service(app, service, quality_evaluator or build_default_quality_evaluator())

    def current_service() -> InferenceService:
        return app.state.inference_service

    def current_runtime_state() -> RuntimeState:
        return app.state.runtime_state

    def current_quality_evaluator() -> ImageQualityEvaluator:
        return app.state.quality_evaluator

    @app.get("/health", response_model=HealthResponse, response_model_by_alias=True)
    def health() -> HealthResponse:
        snapshot = current_runtime_state().snapshot()
        return HealthResponse(
            status="UP",
            ready=True,
            model_loaded=True,
            model_name=snapshot.model_name,
            model_version=snapshot.model_version,
            device=snapshot.device,
            busy=snapshot.busy,
            started_at=snapshot.started_at,
            total_requests=snapshot.total_requests,
            success_count=snapshot.success_count,
            failure_count=snapshot.failure_count,
            last_inference_time_ms=snapshot.last_inference_time_ms,
            last_success_at=snapshot.last_success_at,
            last_error=snapshot.last_error,
        )

    @app.post(
        "/v1/inference/vessel-segmentation",
        response_model=VesselSegmentationResponse,
        response_model_by_alias=True,
    )
    async def segment(
        request: Request,
        response: Response,
        file: UploadFile = File(...),
    ) -> VesselSegmentationResponse:
        request_id = _normalize_request_id(request.headers.get("X-Request-ID"))
        if not file.content_type or not file.content_type.startswith("image/"):
            raise HTTPException(
                status_code=415,
                detail="Only image uploads are supported",
                headers={"X-Request-ID": request_id},
            )
        content = await file.read(MAX_UPLOAD_BYTES + 1)
        if not content:
            raise HTTPException(
                status_code=400,
                detail="Uploaded image is empty",
                headers={"X-Request-ID": request_id},
            )
        if len(content) > MAX_UPLOAD_BYTES:
            raise HTTPException(
                status_code=413,
                detail="Uploaded image exceeds 20 MB",
                headers={"X-Request-ID": request_id},
            )
        started = perf_counter()
        try:
            inference_service = current_service()
            runtime_state = current_runtime_state()

            def execute_inference() -> InferenceRecord:
                with runtime_state.inference_slot(request_id) as attempt:
                    logger.info(
                        "ai_inference_started requestId=%s filename=%s fileSize=%d queueWaitMs=%d",
                        request_id,
                        file.filename or "retina-image",
                        len(content),
                        attempt.queue_wait_time_ms,
                    )
                    inference_record = inference_service.infer(
                        file.filename or "retina-image", content
                    )
                    attempt.succeed(inference_record.processing_time_ms)
                    return inference_record

            record = await run_in_threadpool(execute_inference)
        except ValueError as exception:
            logger.warning(
                "ai_inference_rejected requestId=%s errorType=%s durationMs=%d",
                request_id,
                type(exception).__name__,
                round((perf_counter() - started) * 1000),
            )
            raise HTTPException(
                status_code=400,
                detail=str(exception),
                headers={"X-Request-ID": request_id},
            ) from exception
        except Exception as exception:
            logger.error(
                "ai_inference_failed requestId=%s errorType=%s durationMs=%d",
                request_id,
                type(exception).__name__,
                round((perf_counter() - started) * 1000),
            )
            raise HTTPException(
                status_code=500,
                detail="AI inference failed",
                headers={"X-Request-ID": request_id},
            ) from exception

        response.headers["X-Request-ID"] = request_id
        logger.info(
            "ai_inference_succeeded requestId=%s inferenceMs=%d durationMs=%d",
            request_id,
            record.processing_time_ms,
            round((perf_counter() - started) * 1000),
        )

        conclusion = "已完成视网膜血管分割；结果仅供辅助分析"
        result_json = VesselResultJson(
            vessel_area_ratio=record.vessel_area_ratio,
            processing_time_ms=record.processing_time_ms,
            model_version=inference_service.model_version,
            conclusion=conclusion,
        )
        return VesselSegmentationResponse(
            inference_id=record.inference_id,
            result_type="VESSEL_SEGMENTATION",
            result_json=result_json,
            model_name=inference_service.model_name,
            model_version=inference_service.model_version,
            processing_time_ms=record.processing_time_ms,
            mask_url=f"/v1/artifacts/{record.inference_id}/mask",
        )

    @app.get("/v1/artifacts/{inference_id}/mask")
    def get_mask(inference_id: str) -> FileResponse:
        try:
            mask_path = current_service().get_mask_path(inference_id)
        except ValueError as exception:
            raise HTTPException(status_code=400, detail=str(exception)) from exception
        except FileNotFoundError as exception:
            raise HTTPException(status_code=404, detail=str(exception)) from exception
        return FileResponse(mask_path, media_type="image/png", filename=f"{inference_id}-mask.png")

    @app.post(
        "/v1/inference/image-quality-check",
        response_model=ImageQualityResponse,
        response_model_by_alias=True,
    )
    async def image_quality_check(file: UploadFile = File(...)) -> ImageQualityResponse:
        if not file.content_type or not file.content_type.startswith("image/"):
            raise HTTPException(status_code=415, detail="Only image uploads are supported")
        content = await file.read(MAX_UPLOAD_BYTES + 1)
        if not content:
            raise HTTPException(status_code=400, detail="Uploaded image is empty")
        if len(content) > MAX_UPLOAD_BYTES:
            raise HTTPException(status_code=413, detail="Uploaded image exceeds 20 MB")
        try:
            evaluator = current_quality_evaluator()
            result = await run_in_threadpool(evaluator.evaluate, content)
        except ValueError as exception:
            raise HTTPException(status_code=400, detail=str(exception)) from exception
        return ImageQualityResponse(
            result_type="IMAGE_QUALITY_CHECK",
            result_json=QualityResultJson(
                grade=result.grade,
                score=result.score,
                metrics=result.metrics,
                reasons=result.reasons,
            ),
            model_name=evaluator.model_name,
            model_version=evaluator.model_version,
            processing_time_ms=result.processing_time_ms,
        )

    return app


def _normalize_request_id(value: str | None) -> str:
    if value and REQUEST_ID_PATTERN.fullmatch(value):
        return value
    return uuid4().hex


app = create_app()
