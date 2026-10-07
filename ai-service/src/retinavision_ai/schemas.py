from dataclasses import dataclass
from datetime import datetime
from pathlib import Path

from pydantic import BaseModel, ConfigDict, Field


@dataclass(frozen=True)
class InferenceRecord:
    inference_id: str
    vessel_area_ratio: float
    processing_time_ms: int
    mask_path: Path


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


class VesselResultJson(BaseModel):
    model_config = ConfigDict(populate_by_name=True)

    vessel_area_ratio: float = Field(alias="vesselAreaRatio")
    processing_time_ms: int = Field(alias="processingTimeMs")
    model_version: str = Field(alias="modelVersion")
    conclusion: str


class VesselSegmentationResponse(BaseModel):
    model_config = ConfigDict(populate_by_name=True)

    inference_id: str = Field(alias="inferenceId")
    result_type: str = Field(alias="resultType")
    result_json: VesselResultJson = Field(alias="resultJson")
    model_name: str = Field(alias="modelName")
    model_version: str = Field(alias="modelVersion")
    processing_time_ms: int = Field(alias="processingTimeMs")
    mask_url: str = Field(alias="maskUrl")


class QualityResultJson(BaseModel):
    grade: str
    score: float
    metrics: dict[str, float]
    reasons: list[str]


class ImageQualityResponse(BaseModel):
    model_config = ConfigDict(populate_by_name=True)

    result_type: str = Field(alias="resultType")
    result_json: QualityResultJson = Field(alias="resultJson")
    model_name: str = Field(alias="modelName")
    model_version: str = Field(alias="modelVersion")
    processing_time_ms: int = Field(alias="processingTimeMs")
