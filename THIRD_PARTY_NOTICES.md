# Third-Party Notices

## ESP RainMaker Android

The local reference checkout at `references/esp-rainmaker-android` originates from:

- Project: ESP RainMaker Android
- Source: https://github.com/espressif/esp-rainmaker-android
- Reviewed commit: `04f3b3748ff7c500cf5f8e53ee8b47e3db74122f`
- License: Apache License 2.0

The reference checkout is not distributed by this repository. This notice must be expanded with file-level attribution if source code is copied or adapted into the Demo.

## Matter SDK

The Demo consumes generated Android artifacts from the adjacent `connectedhomeip` repository:

- Project: Matter SDK (`connectedhomeip`)
- Source: https://github.com/project-chip/connectedhomeip
- Pinned commit: `b961bbb56e7b9496453e39ed3f6420fe7e644865`
- License: Apache License 2.0
- Generated artifacts: Controller, Platform, Onboarding Payload, Interaction Model, Cluster, JSON/TLV JARs and arm64 JNI libraries

Generated binaries are local build inputs and are excluded from this repository.

## AndroidX CameraX

The Android App uses CameraX for lifecycle-aware camera preview and image analysis:

- Project: AndroidX CameraX
- Source: https://android.googlesource.com/platform/frameworks/support/+/androidx-main/camera/
- Version: `1.3.4`
- License: Apache License 2.0

## Google ML Kit Barcode Scanning

The Android App uses the on-device ML Kit barcode scanner for Matter QR recognition:

- Product: Google ML Kit Barcode Scanning for Android
- Source: https://developers.google.com/ml-kit/vision/barcode-scanning/android
- Artifact: `com.google.mlkit:barcode-scanning:17.2.0`
- Terms: Google APIs Terms of Service and ML Kit SDK terms distributed with the artifact
