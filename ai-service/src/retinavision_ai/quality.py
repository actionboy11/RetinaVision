from dataclasses import dataclass
from time import perf_counter
from typing import Protocol

import cv2
import numpy as np


@dataclass(frozen=True)
class QualityConfig:
    blur_threshold: float = 0.18
    underexposed_mean: float = 35.0
    overexposed_mean: float = 220.0
    contrast_threshold: float = 0.18
    field_coverage_threshold: float = 0.35
    illumination_uniformity_threshold: float = 0.35
    saturated_pixel_ratio_threshold: float = 0.30
    target_field_coverage: float = 0.65
    warning_score: float = 70.0
    pass_score: float = 82.0


@dataclass(frozen=True)
class QualityEvaluation:
    grade: str
    score: float
    metrics: dict[str, float]
    reasons: list[str]
    processing_time_ms: int


class ImageQualityEvaluator(Protocol):
    model_name: str
    model_version: str

    def evaluate(self, content: bytes) -> QualityEvaluation: ...


class RuleBasedImageQualityEvaluator:
    model_name = "retinavision-rule-quality"
    model_version = "1.0.0"

    def __init__(self, config: QualityConfig) -> None:
        self.config = config

    def evaluate(self, content: bytes) -> QualityEvaluation:
        started = perf_counter()
        encoded = np.frombuffer(content, dtype=np.uint8)
        image = cv2.imdecode(encoded, cv2.IMREAD_COLOR)
        if image is None:
            raise ValueError("Uploaded file is not a readable image")

        gray = cv2.cvtColor(image, cv2.COLOR_BGR2GRAY)
        foreground = gray > 10
        foreground_pixels = gray[foreground]
        if foreground_pixels.size == 0:
            foreground_pixels = gray.reshape(-1)

        laplacian_variance = float(cv2.Laplacian(gray, cv2.CV_64F).var())
        sharpness = min(1.0, laplacian_variance / 500.0)
        mean_luminance = float(foreground_pixels.mean())
        p05, p95 = np.percentile(foreground_pixels, [5, 95])
        dark_ratio = float((foreground_pixels <= 5).mean())
        bright_ratio = float((foreground_pixels >= 250).mean())
        exposure = max(0.0, 1.0 - abs(mean_luminance - 128.0) / 128.0)
        contrast = min(1.0, ((float(foreground_pixels.std()) / 64.0) + (float(p95 - p05) / 180.0)) / 2.0)
        field_coverage = float(foreground.mean())
        low_frequency = cv2.GaussianBlur(gray, (0, 0), sigmaX=21)
        illumination_std = float(low_frequency[foreground].std()) if foreground.any() else 255.0
        illumination_uniformity = max(0.0, 1.0 - illumination_std / 80.0)

        metrics = {
            "sharpness": round(sharpness, 4),
            "exposure": round(exposure, 4),
            "contrast": round(contrast, 4),
            "fieldCoverage": round(field_coverage, 4),
            "illuminationUniformity": round(illumination_uniformity, 4),
        }
        reasons: list[str] = []
        if sharpness < self.config.blur_threshold:
            reasons.append("BLUR")
        if mean_luminance < self.config.underexposed_mean:
            reasons.append("UNDEREXPOSED")
        if mean_luminance > self.config.overexposed_mean:
            reasons.append("OVEREXPOSED")
        if contrast < self.config.contrast_threshold:
            reasons.append("LOW_CONTRAST")
        if field_coverage < self.config.field_coverage_threshold:
            reasons.append("INSUFFICIENT_FIELD")
        if dark_ratio > self.config.saturated_pixel_ratio_threshold and "UNDEREXPOSED" not in reasons:
            reasons.append("UNDEREXPOSED")
        if bright_ratio > self.config.saturated_pixel_ratio_threshold and "OVEREXPOSED" not in reasons:
            reasons.append("OVEREXPOSED")
        if illumination_uniformity < self.config.illumination_uniformity_threshold:
            reasons.append("UNEVEN_ILLUMINATION")

        weighted_score = 100.0 * (
            sharpness * 0.25
            + exposure * 0.20
            + contrast * 0.20
            + min(1.0, field_coverage / self.config.target_field_coverage) * 0.20
            + illumination_uniformity * 0.15
        )
        score = round(weighted_score, 2)
        if reasons or score < self.config.warning_score:
            grade = "FAIL"
        elif score < self.config.pass_score:
            grade = "WARNING"
        else:
            grade = "PASS"
        return QualityEvaluation(
            grade=grade,
            score=score,
            metrics=metrics,
            reasons=reasons,
            processing_time_ms=max(1, round((perf_counter() - started) * 1000)),
        )
