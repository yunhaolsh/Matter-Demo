# Matter Demo

这是一个独立的 Android Matter Controller Demo 与 App SDK 骨架。项目目标是让自有 App 通过扫码添加并控制 Matter 设备，并在后续接入自有云端与家庭 Hub。

详细路线见 [plan.md](plan.md)。当前目录旁边的 `connectedhomeip` 是 Matter SDK 的权威源码；`references/` 仅保存本地参考项目说明，完整的 ESP RainMaker checkout 已被 Git 忽略，不会随本仓库提交。

## 当前进度：M1 Matter SDK 基础接入

`android/` 已包含可独立构建的 Kotlin + Jetpack Compose 双模块工程：

- `app`：家庭、房间、设备控制、扫码/手动码、Wi-Fi 配置、配网进度、详情、自动化与设置页面
- `matter-app-sdk`：不依赖 UI 的公开接口、数据模型和 `FakeMatterAppSdk`
- Fake SDK 已跑通配网事件流、OnOff 控制和设备移除
- 已从锁定的 connectedhomeip 提交封装 Android Controller JAR/JNI 产物
- 已使用官方 `OnboardingPayloadParser` 校验二维码和手动配对码
- 已建立 SDK 内部的长生命周期 Controller Runtime
- 已实现 Android BLE 广播过滤、扫描、GATT/MTU 连接和 Wi-Fi Commissioning 状态机
- 已按 Android 版本处理 BLE/Location 权限、超时、取消和单会话互斥
- BLE 配网链路尚待 arm64 Android 真机和未配网 Matter over Wi-Fi 设备验证
- 尚未完成相机扫码、设备目录持久化、能力发现、OnOff 控制或云端

## 构建与测试

```bash
cd android
export JAVA_HOME=/opt/android-studio/jbr
export ANDROID_HOME=/home/yunhao/Android/Sdk
./gradlew test
./gradlew :app:assembleDebug
```

Debug APK 输出到 `android/app/build/outputs/apk/debug/app-debug.apk`。

下一步继续 M1：真机验证配网，然后实现设备目录持久化、Descriptor 能力发现和 OnOff 控制/订阅。
