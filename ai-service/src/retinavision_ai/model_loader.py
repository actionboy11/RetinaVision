from pathlib import Path

import torch

from .model_def import FSCNet_Final_DMI


def load_fscnet_model(model_path: Path, device: torch.device) -> torch.nn.Module:
    resolved_path = model_path.expanduser().resolve()
    if not resolved_path.is_file():
        raise FileNotFoundError(f"Model checkpoint not found: {resolved_path}")

    checkpoint = torch.load(resolved_path, map_location=device, weights_only=True)
    state_dict = checkpoint.get("model_state_dict", checkpoint)
    if not isinstance(state_dict, dict):
        raise ValueError("Checkpoint does not contain a model_state_dict")

    model = FSCNet_Final_DMI(in_ch=1, start_ch=64)
    model.load_state_dict(state_dict, strict=True)
    return model.to(device).eval()
