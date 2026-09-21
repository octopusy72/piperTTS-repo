# YIWOO TTS Android 1.1.0

This repository contains the Android application source corresponding to
YIWOO TTS 1.1.0, source tag `v1.1.0`.
It includes the Korean 1M and English LJSpeech 1M runtime integration,
notification reading, time announcements, and pronunciation normalization.
The model binaries are distributed separately from this source repository.

## License

Copyright (C) 2026 Yiwoo Solution.

Unless a file or directory carries a different notice, the YIWOO TTS Android
application source code in this repository is licensed under the GNU General
Public License, version 3 or (at your option) any later version
(`GPL-3.0-or-later`). See `LICENSE`.

This source-code license does not grant rights to model weights, training
datasets, generated pronunciation data, release signing material, or YIWOO
names and logos unless those assets explicitly state otherwise. See
`MODEL_ASSETS.md` and `TRADEMARKS.md`.

## Scope

Included:

- Android application, service, native bridge, resources, and Gradle scripts
- The bundled eSpeak NG corresponding source and generated runtime data
- Third-party license texts needed to audit the source tree

Excluded:

- Korean and English ONNX model binaries
- Training corpora, checkpoints, caches, and training scripts
- Pronunciation datasets and generated runtime lexicons
- Internal analysis, device logs, screenshots, reports, and release artifacts

The excluded runtime assets must be provisioned separately before the app can
perform synthesis; `tools/provision_runtime_assets.py` copies them from an
APK supplied by its recipient and checks the published asset hashes.
The production English voice is trained separately from
LJSpeech-derived data; neither its weights nor its training data are
distributed from this source repository.

## Build preparation

The project expects Android SDK 36, NDK support for `arm64-v8a`, Java 17, and
the locally referenced ONNX Runtime Android AAR under `app/libs/`. Model and
frontend runtime assets are intentionally not published here.

See `BUILDING.md` for the reproducible source-build prerequisites, excluded
runtime asset locations, and external signing configuration.

## Third-party source

eSpeak NG is included under `android/YiwooTtsCandidate/app/src/main/cpp/espeak-ng`; its license is in
that directory and in `LICENSES/ESPEAK_NG_GPL-3.0.txt`. Other copied license
texts are under `LICENSES/`. Publication of source code does not grant rights
to separately sourced model weights or datasets. See `SOURCE_RELEASE.md`
for component versions and the source/binary correspondence procedure.
