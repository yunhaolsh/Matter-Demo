# Matter Home Android M0

An independent Android product shell for a Matter controller. M0 uses the UI-free
`matter-app-sdk` module's in-memory implementation; it does not yet bundle the
connectedhomeip Java/JNI artifacts or connect to a cloud service.

## Prerequisites

- Android SDK 34 at `/home/yunhao/Android/Sdk` (or set `ANDROID_HOME`)
- JDK 17; Android Studio's JBR is supported

## Build and test

```bash
export JAVA_HOME=/opt/android-studio/jbr
export ANDROID_HOME=/home/yunhao/Android/Sdk
./gradlew test
./gradlew :app:assembleDebug
```

The debug APK is generated at `app/build/outputs/apk/debug/app-debug.apk`.

## Architecture boundary

The app depends only on `com.example.matter.api`. The SDK implementation owns
commissioning and interaction behavior; future connectedhomeip integration belongs
behind an internal transport and must not expose CHIP/JNI classes to callers.
