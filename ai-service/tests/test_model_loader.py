from pathlib import Path
import sys

import pytest
import torch


PROJECT_ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(PROJECT_ROOT / "src"))

from retinavision_ai.model_loader import load_fscnet_model  # noqa: E402


@pytest.mark.model_integration
def test_load_fscnet_model_from_training_checkpoint():
    model = load_fscnet_model(PROJECT_ROOT / "models" / "model_new.pth", torch.device("cpu"))

    assert model.training is False
    assert next(model.parameters()).device.type == "cpu"
