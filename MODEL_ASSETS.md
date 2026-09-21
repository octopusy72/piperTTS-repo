# Model and data assets

The `GPL-3.0-or-later` license at the repository root applies to the YIWOO TTS
Android application source code. It does not, by itself, license separately
distributed model weights, training datasets, checkpoints, generated
pronunciation dictionaries, or other data assets.

This public repository intentionally excludes:

- Korean and English ONNX model weights
- Original audio and metadata used for training
- Training checkpoints, optimizer state, and caches
- Generated pronunciation lexicons and CMUdict runtime tables
- Release signing keys and credentials

The current English runtime model is a separately trained LJSpeech-derived
voice. Its versioned filename may appear in application source as a runtime
contract, but the weight file itself remains an independently distributed
asset with separately recorded provenance.

Each model or data asset distributed with an application build must have its
own documented provenance and distribution permission. A model filename or
reference in source code is not a grant of rights to that model.
