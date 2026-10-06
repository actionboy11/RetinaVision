from pathlib import Path
import sys

import cv2
import numpy as np

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "src"))

from retinavision_ai.quality import QualityConfig, RuleBasedImageQualityEvaluator


def encode(image: np.ndarray) -> bytes:
    ok, data = cv2.imencode(".png", image)
    assert ok
    return data.tobytes()


def test_uniform_image_is_rejected_for_low_contrast_and_blur():
    image = np.full((256, 256, 3), 120, dtype=np.uint8)
    evaluator = RuleBasedImageQualityEvaluator(QualityConfig())

    result = evaluator.evaluate(encode(image))

    assert result.grade == "FAIL"
    assert "BLUR" in result.reasons
    assert "LOW_CONTRAST" in result.reasons


def test_dark_image_reports_underexposure():
    image = np.full((256, 256, 3), 8, dtype=np.uint8)
    evaluator = RuleBasedImageQualityEvaluator(QualityConfig())

    result = evaluator.evaluate(encode(image))

    assert result.grade == "FAIL"
    assert "UNDEREXPOSED" in result.reasons


def test_overexposed_image_reports_stable_reason_code():
    image = np.full((256, 256, 3), 252, dtype=np.uint8)
    result = RuleBasedImageQualityEvaluator(QualityConfig()).evaluate(encode(image))
    assert "OVEREXPOSED" in result.reasons


def test_excessive_black_border_reports_insufficient_field():
    image = np.zeros((256, 256, 3), dtype=np.uint8)
    cv2.circle(image, (128, 128), 45, (120, 120, 120), -1)
    result = RuleBasedImageQualityEvaluator(QualityConfig()).evaluate(encode(image))
    assert "INSUFFICIENT_FIELD" in result.reasons


def test_unreadable_image_is_rejected():
    evaluator = RuleBasedImageQualityEvaluator(QualityConfig())

    try:
        evaluator.evaluate(b"not-an-image")
        raise AssertionError("expected ValueError")
    except ValueError as exception:
        assert str(exception) == "Uploaded file is not a readable image"
