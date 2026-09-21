# Building the Android source

## Toolchain

- Java 17
- Android SDK 36
- Android NDK with CMake 3.22.1 support
- Gradle wrapper included in `android/YiwooTtsCandidate`
- `arm64-v8a` target

## Build

For synthesis, first provision the runtime data from your separately supplied
1.1.0 APK (Python 3, standard library only):

```bash
python3 tools/provision_runtime_assets.py /path/to/YiwooTTS-1.1.0.apk
```

The script accepts only the asset paths and SHA-256 values in
`runtime-assets.json`. It never extracts arbitrary ZIP paths or signing data.
It does not fetch data or change any asset license.

```bash
cd android/YiwooTtsCandidate
./gradlew :app:assembleDebug
```

The source-only debug build compiles without private runtime assets, but it
cannot synthesize speech until separately licensed runtime files are supplied.

## Runtime asset locations

The application expects separately provisioned assets under:

```text
app/src/main/assets/models/
app/src/main/assets/pronunciation/
app/src/main/assets/g2pkk/
app/src/main/assets/normalization/
```

Those assets are not part of this repository or its GPL source-code license.
Do not substitute assets unless their license permits the intended use and
distribution.

## Release signing

Signing keys and passwords must remain outside the repository. The release
build optionally reads a Java properties file from:

```text
~/.config/yiwoo-tts/signing.properties
```

or from the path in `YIWOO_SIGNING_PROPERTIES`. The expected keys are:

```properties
storeFile=/absolute/path/to/release.jks
storePassword=...
keyAlias=...
keyPassword=...
```

Never commit this file, a keystore, or signing credentials.

Without the distributor's signing material, use the debug build or your own
release certificate. Android will not upgrade an installed APK signed with a
different certificate. Use a separate test device/profile for your own build
to avoid deleting existing user settings. Distributor private keys are not
required to compile or sign your own build.
