# Local model checkpoint

Place the FSCNet checkpoint at `models/model_new.pth`. The file is required for
vessel segmentation and the `model_integration` test, but it must not be
committed because model weights are large binary artifacts with a separate
provenance and release lifecycle.

The default path can be overridden with `RETINAVISION_AI_MODEL_PATH`.

Verify a local checkpoint before use:

```powershell
Get-FileHash .\models\model_new.pth -Algorithm SHA256
```

Run the real checkpoint test explicitly:

```powershell
python -m pytest -q .\tests\test_model_loader.py -m model_integration
```
