from pathlib import Path
from uuid import uuid4

import cv2
import numpy as np

from .inference import VesselSegmenter
from .schemas import InferenceRecord


class VesselInferenceService:
    def __init__(
        self,
        segmenter: VesselSegmenter,
        storage_root: Path,
        model_name: str,
        model_version: str,
    ) -> None:
        self.segmenter = segmenter
        self.storage_root = storage_root.resolve()
        self.model_name = model_name
        self.model_version = model_version
        self.device = str(segmenter.device)
        self.input_root = self.storage_root / "inputs"
        self.mask_root = self.storage_root / "masks"
        self.input_root.mkdir(parents=True, exist_ok=True)
        self.mask_root.mkdir(parents=True, exist_ok=True)

    def infer(self, filename: str, content: bytes) -> InferenceRecord:
        encoded = np.frombuffer(content, dtype=np.uint8)
        image = cv2.imdecode(encoded, cv2.IMREAD_GRAYSCALE)
        if image is None:
            raise ValueError("Uploaded file is not a readable image")

        inference_id = uuid4().hex
        input_path = self.input_root / f"{inference_id}.png"
        mask_path = self.mask_root / f"{inference_id}.png"
        if not cv2.imwrite(str(input_path), image):
            raise OSError("Failed to store inference input")

        output = self.segmenter.segment_array(image)
        if not cv2.imwrite(str(mask_path), output.mask):
            raise OSError("Failed to store segmentation mask")
        return InferenceRecord(
            inference_id=inference_id,
            vessel_area_ratio=output.vessel_area_ratio,
            processing_time_ms=output.processing_time_ms,
            mask_path=mask_path,
        )

    def get_mask_path(self, inference_id: str) -> Path:
        if not inference_id or any(character not in "0123456789abcdef" for character in inference_id):
            raise ValueError("Invalid inference ID")
        target = (self.mask_root / f"{inference_id}.png").resolve()
        if not target.is_relative_to(self.mask_root):
            raise ValueError("Invalid mask path")
        if not target.is_file():
            raise FileNotFoundError("Segmentation mask not found")
        return target
