# YIWOO TTS Android source preparation

This repository contains the Android application source corresponding to the
YIWOO TTS 1.0.0 internal preparation build. It is not a final product release.

## Scope

Included:

- Android application, service, native bridge, resources, and Gradle scripts
- The bundled eSpeak NG corresponding source and generated runtime data
- Third-party license texts needed to audit the source tree

Excluded:

- Korean and temporary English ONNX model binaries
- Training corpora, checkpoints, caches, and training scripts
- Pronunciation datasets and generated runtime lexicons
- Internal analysis, device logs, screenshots, reports, and release artifacts

The excluded runtime assets must be provisioned separately before the app can
perform synthesis. The temporary evaluation voice used by internal builds is
not distributed from this repository and is planned to be replaced by a
separately trained LJSpeech-based voice.

## Build preparation

The project expects Android SDK 36, NDK support for `arm64-v8a`, Java 17, and
the locally referenced ONNX Runtime Android AAR under `app/libs/`. Model and
frontend runtime assets are intentionally not published here.

## Third-party source

eSpeak NG is included under `app/src/main/cpp/espeak-ng`; its license is in
that directory and in `LICENSES/ESPEAK_NG_GPL-3.0.txt`. Other copied license
texts are under `LICENSES/`. Publication of source code does not grant rights
to separately sourced model weights or datasets.
