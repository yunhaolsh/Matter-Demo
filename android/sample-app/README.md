# Sample App

本仓库的 `android/app` 就是 `matter-app-sdk` 的完整 Sample App，它仅依赖 SDK 公共 API，不直接引用 `chip.devicecontroller.*`。

演示内容：

- CameraX/ML Kit 扫描 Matter 二维码；
- BLE Commissioning Matter over Wi-Fi；
- Descriptor 能力发现和 Device Profile；
- Capability UI Registry 动态面板；
- 所有类型化能力的实时状态；
- Multi-Endpoint 控制和 RemoveFabric。

运行：

```bash
cd android
export JAVA_HOME=/opt/android-studio/jbr
export ANDROID_HOME=$HOME/Android/Sdk
./gradlew :app:installDebug
```

Sample 的 Debug 构建显式允许测试 DAC，Release 构建使用严格 Device Attestation。
