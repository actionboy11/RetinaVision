from dataclasses import dataclass
from time import perf_counter

import cv2
import numpy as np
import torch


@dataclass(frozen=True)
class InferenceConfig:
    window_size: int = 96
    stride: int = 32
    batch_size: int = 16
    threshold: float = 0.5
    minimum_component_area: int = 10


@dataclass(frozen=True)
class SegmentationOutput:
    mask: np.ndarray
    vessel_area_ratio: float
    processing_time_ms: int


def _axis_positions(length: int, window_size: int, stride: int) -> list[int]:
    if length <= window_size:
        return [0]
    positions = list(range(0, length - window_size + 1, stride))
    final_position = length - window_size
    if positions[-1] != final_position:
        positions.append(final_position)
    return positions


def extract_patches(
    image: np.ndarray,
    window_size: int = 96,
    stride: int = 32,
) -> tuple[np.ndarray, list[tuple[int, int]], tuple[int, int]]:
    if image.ndim != 2:
        raise ValueError("Expected a two-dimensional grayscale image")
    if window_size < 1 or stride < 1:
        raise ValueError("window_size and stride must be positive")

    original_shape = image.shape
    pad_bottom = max(0, window_size - image.shape[0])
    pad_right = max(0, window_size - image.shape[1])
    padded = np.pad(image, ((0, pad_bottom), (0, pad_right)), mode="edge")

    positions = [
        (y, x)
        for y in _axis_positions(padded.shape[0], window_size, stride)
        for x in _axis_positions(padded.shape[1], window_size, stride)
    ]
    patches = np.stack(
        [padded[y : y + window_size, x : x + window_size] for y, x in positions]
    ).astype(np.float32)
    patches = (patches / 255.0)[:, np.newaxis, :, :]
    return patches, positions, original_shape


def reconstruct_probability(
    predictions: np.ndarray,
    positions: list[tuple[int, int]],
    padded_shape: tuple[int, int],
    original_shape: tuple[int, int],
    window_size: int,
) -> np.ndarray:
    probability_sum = np.zeros(padded_shape, dtype=np.float32)
    overlap_count = np.zeros(padded_shape, dtype=np.float32)

    for prediction, (y, x) in zip(predictions, positions, strict=True):
        probability_sum[y : y + window_size, x : x + window_size] += prediction.squeeze()
        overlap_count[y : y + window_size, x : x + window_size] += 1

    probability = np.divide(
        probability_sum,
        overlap_count,
        out=np.zeros_like(probability_sum),
        where=overlap_count > 0,
    )
    return probability[: original_shape[0], : original_shape[1]]


def _filter_small_components(binary_mask: np.ndarray, minimum_area: int) -> np.ndarray:
    component_count, labels, stats, _ = cv2.connectedComponentsWithStats(
        binary_mask.astype(np.uint8), connectivity=8
    )
    filtered = np.zeros_like(binary_mask, dtype=np.uint8)
    for component in range(1, component_count):
        if stats[component, cv2.CC_STAT_AREA] >= minimum_area:
            filtered[labels == component] = 1
    return filtered


class VesselSegmenter:
    def __init__(
        self,
        model: torch.nn.Module,
        device: torch.device,
        config: InferenceConfig | None = None,
    ) -> None:
        self.model = model.to(device).eval()
        self.device = device
        self.config = config or InferenceConfig()

    @torch.inference_mode()
    def segment_array(self, image: np.ndarray) -> SegmentationOutput:
        started = perf_counter()
        patches, positions, original_shape = extract_patches(
            image,
            window_size=self.config.window_size,
            stride=self.config.stride,
        )
        padded_shape = (
            max(original_shape[0], self.config.window_size),
            max(original_shape[1], self.config.window_size),
        )

        predictions: list[np.ndarray] = []
        for start in range(0, len(patches), self.config.batch_size):
            batch = torch.from_numpy(patches[start : start + self.config.batch_size]).to(
                self.device
            )
            logits = self.model(batch)
            if isinstance(logits, tuple):
                logits = logits[0]
            predictions.append(torch.sigmoid(logits).cpu().numpy())

        probability = reconstruct_probability(
            np.concatenate(predictions, axis=0),
            positions,
            padded_shape,
            original_shape,
            self.config.window_size,
        )
        binary = (probability > self.config.threshold).astype(np.uint8)
        binary = _filter_small_components(binary, self.config.minimum_component_area)
        mask = binary * 255
        elapsed_ms = max(0, round((perf_counter() - started) * 1000))
        vessel_area_ratio = float(np.count_nonzero(binary) / binary.size)
        return SegmentationOutput(mask, vessel_area_ratio, elapsed_ms)
