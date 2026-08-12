# Android Matter Demo 实施计划

## 1. 项目目标

在当前目录 `/home/yunhao/github/Matter/matter demo` 中实现一个接近真实智能家居产品的 Android Demo，并产出可复用的 `matter-app-sdk` Android Library。

首个可运行版本需要完成：

- 产品化的家庭、房间、设备列表和设备控制界面；
- 扫描 Matter 二维码或输入手动配对码；
- 通过 BLE 为 Matter over Wi-Fi 设备完成配网；
- 自动识别设备 Endpoint 和 OnOff 能力；
- 支持 On、Off、Toggle、状态读取和订阅；
- App/进程重启后无需重新配网；
- 对接自有云的账号、家庭和设备元数据接口；
- 预留家庭 Hub 传输层，使远程控制最终走“App -> 自有云 -> Hub -> Matter 设备”。

首版限定为 Android API 28+、arm64、Matter over Wi-Fi 和提供 OnOff Cluster 的灯/插座类设备。Thread、NFC、ICD/LIT、OTA、Group、Scene 和完整通用 Cluster 面板后续实现。

## 2. 分层参考方案

本项目不会以 CHIPTool 作为 App 产品框架。不同开源项目只在各自擅长的层次作为参考：

| 层次 | 主要参考 | 使用范围 |
|---|---|---|
| 产品 App 信息架构 | ESP RainMaker Android | 登录、家庭、房间、设备列表、设备详情、状态反馈和控制面板的组织方式 |
| Android 配网体验 | Google Sample Apps for Matter | 系统式扫码配网、配网状态、错误恢复和 Multi-Admin 交互；该项目已归档，只作为固定版本参考 |
| Matter 协议实现 | connectedhomeip Android Controller | `AndroidChipPlatform`、Java/JNI、Commissioning、Read/Invoke/Subscribe |
| Hub Controller | Open Home Foundation Matter Server | Hub 持有 Fabric、持久化、订阅和 WebSocket/RPC 服务边界 |
| 自有云 | 本项目独立设计 | 用户、家庭、Hub、设备影子、远程命令、事件和审计 |

使用外部代码前固定具体 Commit，并在 `THIRD_PARTY_NOTICES.md` 中记录来源、Commit 和许可证。RainMaker、Google Sample 和 Home Assistant 的业务代码不直接整体复制；优先借鉴流程和边界，只复用确实必要且许可证兼容的小段实现。

CHIPTool 仅允许作为以下底层调用的参考：

- `AndroidChipPlatform` 和 `ChipDeviceController` 初始化；
- `pairDeviceThroughBLE` Commissioning 调用；
- OnOff Cluster 的 Read、Invoke 和 Subscribe 调用。

不得复制 CHIPTool 的页面结构、Fragment 导航、手工输入 Node ID/Endpoint 的工具式交互、Controller 生命周期错误、固定 Endpoint 假设和通用调试面板。

## 3. 目标架构

### 3.1 快速 Demo 架构

第一阶段由 Android 手机持有一个开发用 Matter Fabric，直接在局域网控制设备，以最快速度验证真实硬件闭环：

```text
Android App
  -> matter-app-sdk
  -> connectedhomeip Java/JNI
  -> BLE Commissioning / IPv6 CASE
  -> Matter 设备
```

### 3.2 最终远程控制架构

产品目标由家庭 Hub 持有长期 Matter Fabric：

```text
Android App
  -> 自有云 HTTPS/WebSocket
  -> 家庭 Hub 安全长连接
  -> Matter Server/Controller
  -> Matter 设备
```

手机 Fabric 与 Hub Fabric 是不同 Controller 身份，不能通过复制 Preferences、NOC、IPK 或私钥完成迁移。第二阶段应让 Hub 直接配网，或者由手机打开 Enhanced Commissioning Window，通过 Multi-Admin 将设备加入 Hub Fabric。验证 Hub 控制成功后，可移除临时手机 Fabric。

## 4. Demo 工作区结构

所有新增代码都放在已创建的独立工作区，不修改 connectedhomeip 的示例目录结构：

```text
matter demo/
  plan.md
  README.md
  THIRD_PARTY_NOTICES.md
  docs/
    architecture.md
    commissioning-flow.md
    cloud-api.md
    hub-contract.md
  android/
    settings.gradle.kts
    build.gradle.kts
    gradle.properties
    app/
    matter-app-sdk/
  hub/
    README.md
    compose.yaml
    config/
  scripts/
    prepare-matter-android.sh
    run-demo-device.sh
    verify-demo.sh
```

`connectedhomeip` 继续作为相邻的官方 SDK 源码仓库。`prepare-matter-android.sh` 从固定的 SDK Commit 构建并复制 Android 所需 JNI/Java 产物，不在 Demo 中复制整套 Matter 源码。

## 5. Android App 设计

使用 Kotlin、Jetpack Compose、Navigation Compose、ViewModel、StateFlow、Room 和 Retrofit/OkHttp。App 采用接近 RainMaker 的产品信息架构，但所有云接口和数据模型由本项目定义。

首版页面：

1. 登录页：Demo Token 登录，后续替换为自有账号系统。
2. 家庭页：家庭名称、房间切换、设备在线状态和添加设备入口。
3. 扫码页：CameraX/ML Kit 扫描，支持手动配对码。
4. 配网页：Wi-Fi 输入、分阶段进度、可理解的错误和重试。
5. 设备详情页：设备名称、房间、连接方式、OnOff 控制和实时状态。
6. 设置页：本地/Hub 模式、诊断信息、移除设备。

UI 不显示 Node ID、Cluster ID、Endpoint ID、JNI 错误码等调试概念。相关信息只出现在脱敏诊断日志中。

App 分层：

```text
Compose UI
  -> ViewModel
  -> Use Case
  -> Device/Home Repository
  -> Local Matter Data Source | Cloud/Hub Data Source
```

页面首先使用 Fake Repository 完成完整导航和状态设计，再接真实 Matter SDK，避免底层调试阻塞产品流程实现。

## 6. `matter-app-sdk` 设计

SDK 是无 UI 的 Android Library/AAR。Camera、页面、导航、云账号和房间 UI 不进入 SDK。

建议包结构：

```text
matter-app-sdk/src/main/java/com/example/matter/
  api/
  commissioning/
  controller/
  interaction/
  storage/
  transport/
  diagnostics/
```

主要职责：

- 进程级唯一 `AndroidChipPlatform` 和长生命周期 `ChipDeviceController`；
- 二维码/手动码解析；
- BLE Commissioning 状态机；
- Device Attestation 策略；
- Node ID 分配和设备目录；
- Descriptor/Endpoint 能力发现；
- OnOff Read/Invoke/Subscribe；
- Local 和 Hub 两种控制 Transport；
- JNI 指针、订阅和 Controller 生命周期管理。

公共接口保持稳定并隐藏所有 CHIP/JNI 类型：

```kotlin
interface MatterAppSdk : Closeable {
    val devices: Flow<List<MatterDevice>>

    fun parseSetupCode(rawCode: String): SetupCode

    fun commissionWifi(
        setupCode: SetupCode,
        credentials: WifiCredentials,
    ): Flow<CommissioningEvent>

    suspend fun refresh(deviceId: String): MatterDevice
    suspend fun setOnOff(deviceId: String, value: Boolean)
    suspend fun toggle(deviceId: String)
    suspend fun readOnOff(deviceId: String): Boolean
    fun observeOnOff(deviceId: String): Flow<OnOffState>
    suspend fun removeDevice(deviceId: String)
}
```

Commissioning 使用显式状态机：

```text
Parsing
-> ScanningBle
-> ConnectingBle
-> EstablishingPase
-> ConfiguringWifi
-> EstablishingCase
-> DiscoveringCapabilities
-> Completed | Failed | Cancelled
```

同时只能运行一个 Commissioning Session，避免 BLE 和原生 Controller 状态竞态。

## 7. 第一阶段实施步骤：本地真实设备闭环

### 7.1 工程与产品壳

1. 初始化 `matter demo/android` 多模块 Gradle 工程。
2. 建立 Compose Design System、导航、错误/空/加载状态。
3. 使用 Fake Repository 实现登录、家庭、房间、设备列表和控制页。
4. 增加 `THIRD_PARTY_NOTICES.md`，记录外部参考项目。

### 7.2 Matter 构建与 AAR

1. 固定 connectedhomeip Commit，并检查 Android SDK 34、NDK `28.2.13676358`、JDK 17。
2. 编写 `prepare-matter-android.sh`，从相邻 connectedhomeip 构建 `libCHIPController.so`、`libc++_shared.so` 及 Controller、Platform、Onboarding、TLV、Cluster 类。
3. 将原生库和所需类打包进 `matter-app-sdk`。
4. Demo App 只能通过 SDK 公共 API 调用 Matter，不能直接依赖 `chip.devicecontroller.*`。
5. 输出并验证 `matter-app-sdk-debug.aar` 和 `app-debug.apk`。

### 7.3 扫码与配网

1. App 使用 CameraX/ML Kit 获取原始二维码字符串，也支持手动输入。
2. SDK 使用 `OnboardingPayloadParser` 解析 `MT:` 和手动码。
3. 根据 Android 版本申请 Camera、BLE Scan/Connect、Location 兼容权限、Wi-Fi 状态和 Multicast 权限。
4. SDK 根据 Discriminator 扫描并连接设备 BLE GATT。
5. 构造 Wi-Fi `NetworkCredentials` 和 `CommissionParameters`，调用 `pairDeviceThroughBLE`。
6. 将 Device Attestation 失败映射为明确业务错误。仅 Debug 构建允许用户显式继续，Release 必须失败关闭。
7. 配网成功后立即保存 `PENDING_DISCOVERY` 记录，避免后续发现失败时丢失已创建 Fabric 的设备。

Wi-Fi 密码和原始 Setup Code 只存在于配网 Session 内存中，不写入 Room、日志或云端。

### 7.4 能力发现和控制

1. 读取 Root Endpoint 的 Descriptor `PartsList`。
2. 读取各 Endpoint 的 Descriptor `ServerList`。
3. 选择第一个提供 OnOff Cluster `0x0006` 的 Endpoint；不写死 Endpoint 1。
4. 对不支持 OnOff 的设备展示“当前 Demo 尚不支持此设备类型”，同时保留已配网记录以便后续扩展。
5. 实现 On、Off、Toggle、OnOff Attribute Read 和 Subscribe。
6. 用 `ConnectedDeviceLease` 管理 Operational Device Pointer，在命令结束或订阅取消时可靠释放，避免复制 CHIPTool 的已知指针泄漏。
7. App 重启后复用同一原生存储命名空间，通过 CASE/mDNS 恢复连接、读取状态并重建订阅。
8. 移除设备时同时执行 Matter Unpair/Fabric 移除和本地目录删除；部分失败必须可恢复。

## 8. 第二阶段实施步骤：自有云与 Hub

### 8.1 自有云接口

定义最小 API：

- 登录和 Token 刷新；
- 家庭、房间和成员；
- Hub 注册、在线状态和授权；
- 设备元数据和能力；
- 创建/查询 Commissioning Session；
- On/Off/Toggle Command；
- Device State WebSocket；
- 移除和审计记录。

云端不保存 Fabric Secret、NOC Private Key、IPK、Wi-Fi 密码或原始 Setup Code。

### 8.2 Hub

1. Demo 使用固定版本的 Open Home Foundation Matter Server 或等价 Controller 服务，不在 Android 项目中重新实现 Matter 协议栈。
2. Hub 使用持久化存储保存 FabricTable、NOC、IPK、Operational Key 和订阅状态。
3. Hub 通过双向认证长连接接收自有云命令，并映射到 Matter Read/Invoke/Subscribe。
4. 首选 Hub 自带 BLE，由 Android 上传短期 Onboarding Payload，Hub 直接完成配网。
5. Hub 没有 BLE 时，手机先临时配网，再打开 Enhanced Commissioning Window，将 Hub Fabric 加入设备。
6. Hub 重启后必须继续控制设备，不得重新配网。

Android SDK 通过统一 Transport 切换控制路径：

```text
LocalMatterTransport -> 手机 Fabric -> Matter 设备
HubMatterTransport   -> 自有云 -> Hub Fabric -> Matter 设备
```

UI 和 ViewModel 不感知底层控制路径差异。

## 9. 测试计划

### 9.1 单元测试

- 二维码/手动码解析和敏感数据脱敏；
- Commissioning 状态转换、串行化、取消和重试；
- 单调递增 Node ID 分配；
- Repository 从 `PENDING_DISCOVERY` 到 `READY/UNSUPPORTED/FAILED` 的转换；
- Descriptor 和 OnOff Endpoint 选择；
- OnOff TLV 编解码；
- Local/Hub Transport 路由；
- Device Pointer 和订阅释放。

JNI、BLE、Storage 和 Cloud API 均通过接口注入 Fake，使 JVM 测试无需真实设备。

### 9.2 Android Instrumentation/UI 测试

- Runtime 只初始化一次；
- Room 持久化和 Migration；
- Camera/BLE/Location 权限拒绝；
- 蓝牙关闭、无 IPv6/mDNS 和 Wi-Fi 切换；
- 登录、家庭、设备列表、扫码、配网进度和控制页面；
- App Kill/Restart 后设备恢复。

### 9.3 物理端到端验收

1. 使用 Android 真机扫描 Wi-Fi OnOff 设备或 Linux Lighting App 的二维码。
2. 通过 BLE 完成 Wi-Fi Commissioning。
3. 自动发现 OnOff Endpoint。
4. 执行 On/Off/Toggle，并验证物理状态和订阅状态一致。
5. 杀死并重启 App，不重新配网即可继续控制。
6. 重启设备和路由器，验证自动重连与重新订阅。
7. 验证错误二维码、BLE 超时、Attestation 失败和不支持设备类型。
8. 删除设备后重新配网，验证无孤立 Fabric/目录记录。
9. 第二阶段让手机离开家庭局域网，通过自有云和 Hub 控制。
10. 重启 Hub，验证 Hub Fabric 和设备控制仍然有效。

## 10. 里程碑与交付物

### M0：工程和产品壳

状态：进行中。Android 双模块工程、产品壳、Fake Matter SDK、配网状态流、设备控制/移除、单元测试和 Debug APK 已完成；Fake 登录仍未实现。

- 可安装的产品化 Android App；
- Fake 登录、家庭、房间、设备列表和控制流程；
- 文档和第三方许可证记录。

### M1：Matter 本地闭环

- `matter-app-sdk-debug.aar`；
- 二维码/手动码解析；
- BLE + Wi-Fi Commissioning；
- OnOff 控制、读取、订阅；
- 重启恢复和设备移除；
- `app-debug.apk` 与端到端验证记录。

### M2：自有云接入

- 账号、家庭和设备元数据 API；
- Hub 注册和安全通道；
- App 的 `HubMatterTransport`；
- 局域网外远程控制。

### M3：产品化准备

- Thread 和更多标准设备类型；
- 严格 Device Attestation；
- Keystore/硬件保护存储；
- Release 多 ABI AAR；
- 日志脱敏、审计、限流和防重放；
- OTA、共享、场景和自动化；
- 威胁建模与 Matter 认证评估。

## 11. Demo 与产品化边界

仅允许在 Debug 构建中使用测试 Controller Vendor ID、测试设备 Attestation 显式忽略、自签名开发证书、手动 Wi-Fi 输入和 arm64-only 构建。

产品版本必须使用正式 Vendor ID、批准的 PAA Root、失败关闭的 Attestation、`allowBackup=false`、安全密钥存储、双向 TLS、命令防重放、按家庭授权、审计与限流、数据库迁移、孤立记录修复、完整 ABI 矩阵、威胁建模和认证测试。
