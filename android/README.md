# Matter Home Android

An independent Android product shell for a Matter controller. The App still uses
the UI-free SDK's in-memory device implementation while M1 is under development.
The SDK now bundles locally generated connectedhomeip Java/JNI artifacts, validates
setup codes with the official parser, owns an internal Controller runtime, and
implements BLE-to-Wi-Fi commissioning. Hardware validation is still required.

## Prepare Matter SDK artifacts

Build `android-arm64-chip-tool` in the sibling `connectedhomeip` checkout, then
copy the pinned Controller artifacts into the SDK module:

```bash
../scripts/prepare-matter-android.sh
```

The copied JAR/JNI files and generated version manifest are local build inputs.
They are ignored by Git and must not be committed.

## Prerequisites

- Android SDK 34 at `/home/yunhao/Android/Sdk` (or set `ANDROID_HOME`)
- JDK 17; Android Studio's JBR is supported
- Kotlin 2.1.10, matching the pinned Matter Android artifacts

## Build and test

```bash
export JAVA_HOME=/opt/android-studio/jbr
export ANDROID_HOME=/home/yunhao/Android/Sdk
./gradlew test
./gradlew :app:assembleDebug
```

The debug APK is generated at `app/build/outputs/apk/debug/app-debug.apk`.

## Hardware commissioning

Connect an arm64 Android 9+ phone and install the debug APK:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Use a factory-reset Matter over Wi-Fi device. The App requests Bluetooth permissions,
accepts a real QR payload or manual setup code, scans the Matter BLE service, connects
GATT, and provisions the supplied Wi-Fi credentials. Camera scanning is not implemented
yet; use manual entry or the official test-code button during development.

## Architecture boundary

The app depends only on `com.example.matter.api`. The SDK implementation owns
commissioning and interaction behavior; future connectedhomeip integration belongs
behind an internal transport and must not expose CHIP/JNI classes to callers.
