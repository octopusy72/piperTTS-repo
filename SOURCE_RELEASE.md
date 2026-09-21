# Source release v1.1.0

Application version: 1.1.0 (10100), arm64-v8a, Android API 28 or later.
Source date: 2026-09-21.

The tag identifies the application source, JNI bridge, eSpeak NG source/data,
resources and build configuration used for this release. The APK's
NOTICE links to this tag. APK and runtime asset hashes accompany the binary
distribution; `runtime-assets.json` records the assets expected by this source.

## Components

| Component | Version / provenance | Terms |
| --- | --- | --- |
| YIWOO Android application | This source tag | GPL-3.0-or-later |
| eSpeak NG | Vendored 1.52.0.1, static library linked into JNI | GPL-3.0-or-later; upstream additional component notices retained |
| ONNX Runtime | Bundled arm64-only AAR, 1.29.0 | MIT plus upstream third-party notices |
| Sonic Java | Vendored source, Bill Cox | Apache-2.0 |
| Kotlin standard library | Gradle-resolved dependency | Apache-2.0 |
| CMUdict | Separately provisioned runtime data | Carnegie Mellon terms reproduced in NOTICE |
| English voice | Yiwoo LJSpeech-derived 1M model | Separate model asset; dataset provenance is not a model-license grant |

eSpeak source and its upstream build/dictionary documentation are included.
The JNI build switches are in `app/src/main/cpp/CMakeLists.txt`. The Android
source is maintained by Yiwoo Solution; changes relative to earlier releases
are recorded in Git. Binary-only runtime data must not be mistaken for a grant
of rights to the original training corpora.

## Rebuilding

Follow `BUILDING.md`. Runtime data can be restored from the recipient's APK
using the allowlisted provisioning tool. Model weights and training corpora
are not committed to this repository. License obligations applicable to any
particular asset remain applicable; this separation does not override them.

This source release is not a claim that publication alone resolves all
third-party data permissions. GPL license text, all packaged notices and
build instructions are provided. The release signing key is private; users
can build and install with their own signing identity.
