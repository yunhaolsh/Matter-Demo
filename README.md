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
- 已实现真实 Matter Fabric 解绑；仅在设备确认 RemoveFabric 成功后删除本地 Node ID 和设备列表记录
- 已实现 Descriptor 驱动的运行时能力发现：递归枚举 Endpoint，并读取 DeviceType、Server/Client Cluster 及 Cluster 全局元数据
- OnOff 控制已改为从能力快照动态选择 Endpoint，不再固定 Endpoint 1 或假设真实设备都是灯
- 已建立类型化 Capability Registry，覆盖 OnOff、Level、Color、DoorLock、Thermostat 和常见传感器，未知 Cluster 保留为 Raw 能力
- 已实现基于能力对象寻址的亮度读取/控制和温度读取，支持同一 Node 上的多 Endpoint
- 已实现 Hue/Saturation、色温、门锁 Timed Invoke、温控状态/设定点，以及经过能力校验的通用 Raw Read/Write/Invoke
- 已实现 Attribute/Event 通用订阅、自动重订阅状态通知，以及按 Subscription ID 取消和连接资源释放
- BLE 配网链路尚待 arm64 Android 真机和未配网 Matter over Wi-Fi 设备验证
- 已完成设备 Node ID 持久化、Raw/类型化能力快照、常用设备交互和通用 Interaction Model 接口；尚未完成相机扫码、真机订阅验证或云端

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
