from pathlib import Path
import sys

import numpy as np
import torch


sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "src"))

from retinavision_ai.inference import (  # noqa: E402
    InferenceConfig,
    VesselSegmenter,
    extract_patches,
    reconstruct_probability,
)


class BrightPixelModel(torch.nn.Module):
    def forward(self, patches: torch.Tensor) -> torch.Tensor:
        return (patches - 0.5) * 20.0


def test_extract_patches_covers_bottom_and_right_edges():
    image = np.zeros((101, 131), dtype=np.uint8)

    patches, positions, original_shape = extract_patches(image, window_size=96, stride=32)

    assert patches.shape[1:] == (1, 96, 96)
    assert original_shape == (101, 131)
    assert (5, 35) in positions


def test_extract_patches_pads_images_smaller_than_window():
    image = np.zeros((40, 50), dtype=np.uint8)

    patches, positions, original_shape = extract_patches(image, window_size=96, stride=32)

    assert patches.shape == (1, 1, 96, 96)
    assert positions == [(0, 0)]
    assert original_shape == (40, 50)


def test_reconstruct_probability_averages_overlapping_predictions():
    predictions = np.stack(
        [np.ones((4, 4), dtype=np.float32), np.full((4, 4), 3.0, dtype=np.float32)]
    )

    result = reconstruct_probability(
        predictions,
        positions=[(0, 0), (0, 2)],
        padded_shape=(4, 6),
        original_shape=(4, 6),
        window_size=4,
    )

    assert np.all(result[:, :2] == 1.0)
    assert np.all(result[:, 2:4] == 2.0)
    assert np.all(result[:, 4:] == 3.0)


def test_segment_array_returns_binary_mask_and_vessel_ratio():
    image = np.zeros((96, 96), dtype=np.uint8)
    image[:, :48] = 255
    segmenter = VesselSegmenter(
        model=BrightPixelModel(),
        device=torch.device("cpu"),
        config=InferenceConfig(window_size=96, stride=32, batch_size=2, threshold=0.5),
    )

    output = segmenter.segment_array(image)

    assert output.mask.dtype == np.uint8
    assert set(np.unique(output.mask)) == {0, 255}
    assert output.vessel_area_ratio == 0.5
    assert output.processing_time_ms >= 0
