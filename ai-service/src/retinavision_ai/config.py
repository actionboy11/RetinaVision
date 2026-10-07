from dataclasses import dataclass
from pathlib import Path
import os


PROJECT_ROOT = Path(__file__).resolve().parents[2]


@dataclass(frozen=True)
class Settings:
    model_path: Path = Path(
        os.getenv("RETINAVISION_AI_MODEL_PATH", PROJECT_ROOT / "models" / "model_new.pth")
    )
    storage_root: Path = Path(
        os.getenv("RETINAVISION_AI_STORAGE_ROOT", PROJECT_ROOT / "storage")
    )
    model_name: str = os.getenv("RETINAVISION_AI_MODEL_NAME", "FSCNet_Final_DMI")
    model_version: str = os.getenv("RETINAVISION_AI_MODEL_VERSION", "model_new-v1")
    threshold: float = float(os.getenv("RETINAVISION_AI_THRESHOLD", "0.5"))
    window_size: int = int(os.getenv("RETINAVISION_AI_WINDOW_SIZE", "96"))
    stride: int = int(os.getenv("RETINAVISION_AI_STRIDE", "32"))
    batch_size: int = int(os.getenv("RETINAVISION_AI_BATCH_SIZE", "4"))
    quality_blur_threshold: float = float(os.getenv("RETINAVISION_QUALITY_BLUR_THRESHOLD", "0.18"))
    quality_underexposed_mean: float = float(os.getenv("RETINAVISION_QUALITY_UNDEREXPOSED_MEAN", "35"))
    quality_overexposed_mean: float = float(os.getenv("RETINAVISION_QUALITY_OVEREXPOSED_MEAN", "220"))
    quality_contrast_threshold: float = float(os.getenv("RETINAVISION_QUALITY_CONTRAST_THRESHOLD", "0.18"))
    quality_field_coverage_threshold: float = float(os.getenv("RETINAVISION_QUALITY_FIELD_COVERAGE_THRESHOLD", "0.35"))
    quality_illumination_uniformity_threshold: float = float(os.getenv("RETINAVISION_QUALITY_ILLUMINATION_UNIFORMITY_THRESHOLD", "0.35"))
    quality_saturated_pixel_ratio_threshold: float = float(os.getenv("RETINAVISION_QUALITY_SATURATED_PIXEL_RATIO_THRESHOLD", "0.30"))
    quality_target_field_coverage: float = float(os.getenv("RETINAVISION_QUALITY_TARGET_FIELD_COVERAGE", "0.65"))
    quality_warning_score: float = float(os.getenv("RETINAVISION_QUALITY_WARNING_SCORE", "70"))
    quality_pass_score: float = float(os.getenv("RETINAVISION_QUALITY_PASS_SCORE", "82"))
