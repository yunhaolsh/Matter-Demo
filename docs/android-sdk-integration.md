# Android Matter App SDK 接入

## 产物

执行：

```bash
cd android
./gradlew :matter-app-sdk:assembleRelease
```

产物位于 `matter-app-sdk/build/outputs/aar/matter-app-sdk-release.aar`。AAR 包含 arm64 Matter Controller JNI、Java Controller 运行时、Onboarding Payload 和 SDK 公共 API。

## App 依赖

将 AAR 放入 App 的 `libs/`，并添加：

```kotlin
dependencies {
    implementation(files("libs/matter-app-sdk-release.aar"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
}
```

最低 Android API 28，当前原生产物仅支持 `arm64-v8a`。宿主 App 需要声明相机、蓝牙扫描/连接、Wi-Fi/网络状态和 Multicast 权限；可直接参考本仓库 `app/src/main/AndroidManifest.xml`。

## 初始化

Controller 是进程级单例，只初始化一次：

```kotlin
val sdk = MatterAppSdkFactory.create(
    applicationContext,
    MatterSdkConfiguration(
        attestationPolicy = DeviceAttestationPolicy.Strict,
        vendorPlugins = listOf(myVendorPlugin),
    ),
)
```

生产环境使用 `Strict`。`AllowDevelopmentDevices` 只适用于使用测试 DAC 的开发固件。进程不再使用 Matter 时调用 `MatterAppSdkFactory.shutdown()`。

## 配网、发现与状态

```kotlin
val setupCode = sdk.parseSetupCode(scannedQrText)
sdk.commissionWifi(setupCode, WifiCredentials(ssid, password)).collect { event ->
    // 用 CommissioningEvent 驱动产品 UI
}

sdk.devices.collect { devices -> /* 设备目录 */ }
sdk.capabilityStates.collect { states -> /* 所有类型化实时状态 */ }
```

能力发现结果位于 `MatterDevice.capabilities`，产品身份位于 `MatterDevice.profile`。实时状态键由 Endpoint 和能力种类组成，适用于多 Endpoint 节点。

## Multi-Admin

让其他 Fabric 加入已有设备：

```kotlin
val window = sdk.openCommissioningWindow(
    deviceId = device.id,
    durationSeconds = 300,
    enhanced = true,
)
val qrOrManualCode = window.setupCode
```

Enhanced Window 使用 Controller 生成的新 Setup Code；Basic Window 可能不返回新码。窗口仅授权另一个 Commissioner 加入，不会复制或上传本机 Fabric 私钥。

## 厂商 Cluster 插件

插件只能映射设备已发现的厂商 Cluster，并声明最小路径白名单：

```kotlin
class DemoVendorPlugin : MatterVendorClusterPlugin {
    override val id = "com.example.vendor"
    override val clusterIds = setOf(0xFFF1_0001L)

    override fun map(endpointId: Int, cluster: MatterClusterCapabilities) =
        VendorClusterDefinition(
            displayName = "Vendor mode",
            readableAttributeIds = setOf(1),
            writableAttributeIds = setOf(2),
            invokableCommandIds = setOf(3),
        )
}
```

插件不能覆盖标准 Cluster，不能声明设备没有发现的路径，也不会得到 Controller、Fabric 或 Wi-Fi 凭据。

## 已支持的类型化控制

- OnOff、Level、Color、色温
- Door Lock、Thermostat
- 温度、湿度、占用、照度、压力传感器
- Fan 百分比
- Window Covering 开、关、停止、升降位置
- Media Playback 播放、暂停、停止、上一首、下一首
- Speaker 的 OnOff/Level 以 Sound/Volume 语义显示

未适配的标准 Cluster 保留为 `RawClusterCapability`，厂商插件映射为 `VendorClusterCapability`。
