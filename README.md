# Matter Demo

这是一个独立的 Android Matter Controller Demo 与 App SDK 骨架。项目目标是让自有 App 通过扫码添加并控制 Matter 设备，并在后续接入自有云端与家庭 Hub。

详细路线见 [plan.md](plan.md)。当前目录旁边的 `connectedhomeip` 是 Matter SDK 的权威源码；`references/` 仅保存本地参考项目说明，完整的 ESP RainMaker checkout 已被 Git 忽略，不会随本仓库提交。

## 当前进度：M0 产品壳

`android/` 已包含可独立构建的 Kotlin + Jetpack Compose 双模块工程：

- `app`：家庭、房间、设备控制、扫码/手动码、Wi-Fi 配置、配网进度、详情、自动化与设置页面
- `matter-app-sdk`：不依赖 UI 的公开接口、数据模型和 `FakeMatterAppSdk`
- Fake SDK 已跑通配网事件流、OnOff 控制和设备移除
- M0 暂未接入真实相机扫码、connectedhomeip Java/JNI、设备持久化或云端

## 构建与测试

```bash
cd android
export JAVA_HOME=/opt/android-studio/jbr
export ANDROID_HOME=/home/yunhao/Android/Sdk
./gradlew test
./gradlew :app:assembleDebug
```

Debug APK 输出到 `android/app/build/outputs/apk/debug/app-debug.apk`。

下一步进入 M1：把 connectedhomeip 的 Android Controller 能力封装到 `matter-app-sdk` 内部，接入真实二维码解析、BLE 配网、Fabric 持久化和 OnOff 订阅。
