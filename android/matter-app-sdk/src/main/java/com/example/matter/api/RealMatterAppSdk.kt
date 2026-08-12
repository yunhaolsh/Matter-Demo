package com.example.matter.api

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import chip.devicecontroller.ChipDeviceController
import chip.devicecontroller.ChipClusters
import chip.devicecontroller.DeviceAttestationDelegate
import chip.devicecontroller.CommissionParameters
import chip.devicecontroller.GetConnectedDeviceCallbackJni.GetConnectedDeviceCallback
import chip.devicecontroller.ICDDeviceInfo
import chip.devicecontroller.NetworkCredentials
import chip.devicecontroller.OpenCommissioningCallback
import chip.devicecontroller.ReportCallback
import chip.devicecontroller.ResubscriptionAttemptCallback
import chip.devicecontroller.SubscriptionEstablishedCallback
import chip.devicecontroller.model.ChipAttributePath
import chip.devicecontroller.model.ChipEventPath
import chip.devicecontroller.model.NodeState
import com.example.matter.commissioning.MatterBleTransport
import com.example.matter.commissioning.MatterBleDeviceNotFoundException
import com.example.matter.controller.MatterControllerRuntime
import com.example.matter.controller.MatterCapabilityDiscovery
import com.example.matter.controller.MatterRawInteraction
import com.example.matter.storage.NodeIdStore
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.ConcurrentHashMap
import java.util.Optional
import java.security.SecureRandom
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withTimeout
import matter.onboardingpayload.OnboardingPayload
import matter.onboardingpayload.OnboardingPayloadParser
import matter.tlv.AnonymousTag
import matter.tlv.ContextSpecificTag
import matter.tlv.TlvWriter

internal class RealMatterAppSdk(
    context: Context,
    private val configuration: MatterSdkConfiguration = MatterSdkConfiguration(),
) : MatterAppSdk {
    private val applicationContext = context.applicationContext
    private val runtime = MatterControllerRuntime.create(applicationContext)
    private val capabilityDiscovery = MatterCapabilityDiscovery(
        runtime.controller,
        MatterVendorClusterRegistry(configuration.vendorPlugins),
    )
    private val rawInteraction =
        MatterRawInteraction(runtime.controller, INTERACTION_TIMEOUT_MILLIS.toInt())
    private val nodeIdStore = NodeIdStore(applicationContext)
    private val setupCodeParser = MatterSetupCodeParser()
    private val payloadParser = OnboardingPayloadParser()
    private val commissioningMutex = Mutex()
    private val deviceUnpairer = MatterDeviceUnpairer()
    private val closed = AtomicBoolean(false)
    private val activeSubscriptionClosers = ConcurrentHashMap.newKeySet<() -> Unit>()
    private val deviceStateObservers = ConcurrentHashMap<String, Job>()
    private val sdkScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val capabilitySubscriptionManager = MatterCapabilitySubscriptionManager { deviceId, capability, attributes, min, max ->
        subscribeRaw(
            deviceId = deviceId,
            capability = capability,
            attributeIds = attributes,
            minIntervalSeconds = min,
            maxIntervalSeconds = max,
        )
    }

    private val defaultRoom = MatterRoom("unassigned", "Unassigned")
    private val mutableHome = MutableStateFlow(MatterHome("local-home", "Matter Home"))
    override val home: StateFlow<MatterHome> = mutableHome.asStateFlow()
    private val mutableRooms = MutableStateFlow(listOf(defaultRoom))
    override val rooms: StateFlow<List<MatterRoom>> = mutableRooms.asStateFlow()
    private val mutableDevices =
        MutableStateFlow(
            nodeIdStore.commissionedNodeIds().sorted().map { nodeId ->
                nodeIdStore.restoredDevice(nodeId) ?: restoredDevice(nodeId)
            },
        )
    override val devices: StateFlow<List<MatterDevice>> = mutableDevices.asStateFlow()
    private val mutableCapabilityStates =
        MutableStateFlow<Map<String, Map<CapabilityStateKey, MatterCapabilityState>>>(emptyMap())
    override val capabilityStates: StateFlow<Map<String, Map<CapabilityStateKey, MatterCapabilityState>>> =
        mutableCapabilityStates.asStateFlow()

    init {
        runtime.controller.setDeviceAttestationDelegate(ATTESTATION_DECISION_TIMEOUT_SECONDS, DeviceAttestationDelegate { pointer, info, result ->
            val attestation = DeviceAttestationResult(info.vendorId, info.productId, result)
            val accepted = when (val policy = configuration.attestationPolicy) {
                DeviceAttestationPolicy.Strict -> attestation.passed
                DeviceAttestationPolicy.AllowDevelopmentDevices -> true
                is DeviceAttestationPolicy.Custom -> runCatching { policy.evaluate(attestation) }.getOrDefault(false)
            }
            runtime.controller.continueCommissioning(pointer, accepted)
        })
        mutableDevices.value.forEach { device ->
            sdkScope.launch {
                runCatching {
                    discoverCapabilities(device.id)
                }
                    .onFailure {
                        Log.w(TAG, "Unable to refresh restored Matter node ${device.id}", it)
                        updateDevice(device.id) { copy(availability = DeviceAvailability.OFFLINE) }
                    }
            }
        }
    }

    override fun parseSetupCode(rawCode: String): SetupCode = setupCodeParser.parse(rawCode)

    override suspend fun openCommissioningWindow(
        deviceId: String,
        durationSeconds: Int,
        enhanced: Boolean,
    ): CommissioningWindow {
        require(durationSeconds in 1..MAX_COMMISSIONING_WINDOW_SECONDS) {
            "Commissioning window duration must be between 1 and $MAX_COMMISSIONING_WINDOW_SECONDS seconds"
        }
        val nodeId = requireNodeId(deviceId)
        return withConnectedDevice(nodeId) { devicePointer ->
            val completed = CompletableDeferred<SetupCode?>()
            val callback = object : OpenCommissioningCallback {
                override fun onSuccess(deviceId: Long, manualPairingCode: String?, qrCode: String?) {
                    val raw = qrCode?.takeIf { it.isNotBlank() } ?: manualPairingCode?.takeIf { it.isNotBlank() }
                    completed.complete(raw?.let(::parseSetupCode))
                }

                override fun onError(status: Int, deviceId: Long) {
                    completed.completeExceptionally(IllegalStateException("Unable to open commissioning window ($status)"))
                }
            }
            val started = if (enhanced) {
                runtime.controller.openPairingWindowWithPINCallback(
                    devicePointer,
                    durationSeconds,
                    ENHANCED_WINDOW_ITERATIONS,
                    SecureRandom().nextInt(MAX_DISCRIMINATOR + 1),
                    null,
                    callback,
                )
            } else {
                runtime.controller.openPairingWindowCallback(devicePointer, durationSeconds, callback)
            }
            check(started) { "Unable to start the commissioning window request" }
            CommissioningWindow(
                setupCode = withTimeout(INTERACTION_TIMEOUT_MILLIS) { completed.await() },
                durationSeconds = durationSeconds,
                enhanced = enhanced,
            )
        }
    }

    @SuppressLint("MissingPermission")
    override fun commissionWifi(
        setupCode: SetupCode,
        credentials: WifiCredentials,
    ): Flow<CommissioningEvent> = flow {
        check(!closed.get()) { "Matter SDK is closed" }
        require(credentials.ssid.isNotBlank()) { "Wi-Fi network is required" }
        check(commissioningMutex.tryLock()) { "Another device is already being added" }

        var transport: MatterBleTransport? = null
        try {
            transport = MatterBleTransport(applicationContext, runtime.bleManager)
            emit(CommissioningEvent.Preparing)
            val payload = parsePayload(setupCode)
            val nodeId = nodeIdStore.reserve()

            emit(CommissioningEvent.FindingDevice)
            val bluetoothDevice =
                transport.scan(payload.discriminator, payload.hasShortDiscriminator)

            emit(CommissioningEvent.Connecting)
            val gatt = transport.connect(bluetoothDevice)

            emit(CommissioningEvent.JoiningNetwork)
            val result = CompletableDeferred<CommissioningResult>()
            runtime.controller.setCompletionListener(CommissioningListener(result))
            val networkCredentials =
                NetworkCredentials.forWiFi(
                    NetworkCredentials.WiFiCredentials(credentials.ssid, credentials.password),
                )
            val parameters =
                CommissionParameters.Builder()
                    .setNetworkCredentials(networkCredentials)
                    .build()

            runtime.controller.pairDeviceThroughBLE(
                gatt,
                transport.connectionId,
                nodeId,
                payload.setupPinCode,
                parameters,
            )

            when (val commissioningResult = withTimeout(COMMISSIONING_TIMEOUT_MILLIS) { result.await() }) {
                is CommissioningResult.Failure -> error(commissioningResult.message)
                is CommissioningResult.Success -> {
                    emit(CommissioningEvent.AddingToHome)
                    nodeIdStore.markCommissioned(commissioningResult.nodeId)
                    val device = restoredDevice(commissioningResult.nodeId)
                    mutableDevices.value =
                        mutableDevices.value.filterNot { it.id == device.id } + device
                    val discoveredDevice =
                        runCatching {
                            discoverCapabilities(device.id)
                            requireDevice(device.id)
                        }.getOrElse { error ->
                            Log.w(TAG, "Capability discovery failed for node ${device.id}", error)
                            updateDevice(device.id) { copy(availability = DeviceAvailability.OFFLINE) }
                            requireDevice(device.id)
                        }
                    emit(CommissioningEvent.Completed(discoveredDevice))
                }
            }
        } catch (notFound: MatterBleDeviceNotFoundException) {
            emit(CommissioningEvent.Failed(requireNotNull(notFound.message)))
        } catch (timeout: TimeoutCancellationException) {
            runtime.controller.shutdownCommissioning()
            emit(CommissioningEvent.Failed("Matter commissioning timed out"))
        } catch (cancelled: CancellationException) {
            runtime.controller.shutdownCommissioning()
            throw cancelled
        } catch (error: SecurityException) {
            emit(CommissioningEvent.Failed("Bluetooth permission is required to add this device"))
        } catch (error: Throwable) {
            emit(CommissioningEvent.Failed(error.message ?: "Unable to add the Matter device"))
        } finally {
            runtime.controller.setCompletionListener(null)
            transport?.close()
            commissioningMutex.unlock()
        }
    }

    override suspend fun discoverCapabilities(deviceId: String): MatterNodeCapabilities {
        val nodeId = requireNodeId(deviceId)
        val capabilities = withConnectedDevice(nodeId, CAPABILITY_DISCOVERY_TIMEOUT_MILLIS) { devicePointer ->
            capabilityDiscovery.discover(nodeId, devicePointer)
        }
        updateDevice(deviceId) {
            val profile = requireNotNull(capabilities.profile)
            copy(
                name = if (name == DEFAULT_DEVICE_NAME) profile.displayName else name,
                type = profile.type,
                capabilities = capabilities,
                profile = profile,
                availability = DeviceAvailability.ONLINE,
            )
        }
        nodeIdStore.saveDevice(requireDevice(deviceId))
        Log.i(TAG, "Discovered ${capabilities.endpoints.size} endpoints for Matter node $nodeId")
        startDeviceStateObservation(deviceId)
        return capabilities
    }

    override suspend fun refresh(deviceId: String): MatterDevice {
        discoverCapabilities(deviceId)
        if (onOffEndpoint(deviceId) != null) readOnOff(deviceId)
        return requireDevice(deviceId)
    }

    override suspend fun setOnOff(deviceId: String, value: Boolean) {
        setOnOff(deviceId, requireOnOffCapability(deviceId), value)
    }

    override suspend fun setOnOff(deviceId: String, capability: OnOffCapability, value: Boolean) {
        requireCapability(deviceId, capability)
        check(if (value) capability.supportsOn else capability.supportsOff) {
            "Device does not accept the requested On/Off command"
        }
        val nodeId = requireNodeId(deviceId)
        invokeOnOff(nodeId, capability.endpointId) { cluster, callback ->
            if (value) cluster.on(callback) else cluster.off(callback)
        }
        val reportedValue = readOnOff(deviceId, capability)
        check(reportedValue == value) { "Device reported an unexpected On/Off state" }
    }

    override suspend fun toggle(deviceId: String) {
        val nodeId = requireNodeId(deviceId)
        val endpointId = requireOnOffEndpoint(deviceId)
        invokeOnOff(nodeId, endpointId) { cluster, callback -> cluster.toggle(callback) }
        readOnOff(deviceId)
    }

    override suspend fun readOnOff(deviceId: String): Boolean {
        return readOnOff(deviceId, requireOnOffCapability(deviceId))
    }

    override suspend fun readOnOff(deviceId: String, capability: OnOffCapability): Boolean {
        requireCapability(deviceId, capability)
        val nodeId = requireNodeId(deviceId)
        return try {
            val value = withOnOffCluster(nodeId, capability.endpointId) { cluster ->
                awaitCallback<Boolean> { continuation ->
                    cluster.readOnOffAttribute(
                        object : ChipClusters.BooleanAttributeCallback {
                            override fun onSuccess(value: Boolean) {
                                continuation.complete(value)
                            }

                            override fun onError(error: Exception) {
                                continuation.completeExceptionally(error)
                            }
                        },
                    )
                }
            }
            Log.i(TAG, "Read node $nodeId endpoint ${capability.endpointId} OnOff=$value")
            updateDevice(deviceId) {
                copy(isOn = value, availability = DeviceAvailability.ONLINE)
            }
            value
        } catch (error: Throwable) {
            updateDevice(deviceId) { copy(availability = DeviceAvailability.OFFLINE) }
            throw error
        }
    }

    override fun observeOnOff(deviceId: String): Flow<OnOffState> = flow {
        val capability = requireOnOffCapability(deviceId)
        emit(OnOffState.Loading)
        emit(OnOffState.Available(readOnOff(deviceId)))
        subscribeRaw(
            deviceId = deviceId,
            capability = capability,
            attributeIds = setOf(MatterCapabilityRegistry.ON_OFF_ATTRIBUTE_ID),
            minIntervalSeconds = ON_OFF_MIN_INTERVAL_SECONDS,
            maxIntervalSeconds = ON_OFF_MAX_INTERVAL_SECONDS,
        ).collect { event ->
            when (event) {
                is MatterSubscriptionEvent.AttributeChanged -> {
                    val value = MatterAttributeValueDecoder.boolean(event.value)
                    updateDevice(deviceId) {
                        copy(isOn = value, availability = DeviceAvailability.ONLINE)
                    }
                    emit(OnOffState.Available(value))
                }
                is MatterSubscriptionEvent.Resubscribing -> {
                    updateDevice(deviceId) { copy(availability = DeviceAvailability.CONNECTING) }
                    emit(OnOffState.Loading)
                }
                is MatterSubscriptionEvent.Established,
                is MatterSubscriptionEvent.EventReceived,
                -> Unit
            }
        }
    }.catch { error ->
        if (error is CancellationException) throw error
        updateDeviceIfPresent(deviceId) { copy(availability = DeviceAvailability.OFFLINE) }
        Log.w(TAG, "Matter OnOff observation stopped for node $deviceId", error)
        emit(OnOffState.Unavailable)
    }

    override fun observeCapabilities(deviceId: String): Flow<CapabilitySubscriptionEvent> {
        val capabilities = requireDevice(deviceId).capabilities
            ?: error("Device capabilities have not been discovered")
        return capabilitySubscriptionManager.observe(deviceId, capabilities)
    }

    override suspend fun setLevel(deviceId: String, capability: LevelCapability, level: Int) {
        requireCapability(deviceId, capability)
        require(level in capability.minimum..capability.maximum) { "Level is outside the supported range" }
        check(capability.supportsMoveToLevel) { "Device does not accept MoveToLevel" }
        val nodeId = requireNodeId(deviceId)
        withConnectedDevice(nodeId) { devicePointer ->
            val cluster = ChipClusters.LevelControlCluster(devicePointer, capability.endpointId)
            awaitCallback<Unit> { result ->
                cluster.moveToLevel(
                    defaultClusterCallback(result),
                    level,
                    null,
                    0,
                    0,
                )
            }
        }
    }

    override suspend fun readLevel(deviceId: String, capability: LevelCapability): Int? {
        requireCapability(deviceId, capability)
        val nodeId = requireNodeId(deviceId)
        return withConnectedDevice(nodeId) { devicePointer ->
            val cluster = ChipClusters.LevelControlCluster(devicePointer, capability.endpointId)
            awaitCallback<Int?> { result ->
                cluster.readCurrentLevelAttribute(
                    object : ChipClusters.LevelControlCluster.CurrentLevelAttributeCallback {
                        override fun onSuccess(value: Int?) {
                            result.complete(value)
                        }

                        override fun onError(error: Exception) {
                            result.completeExceptionally(error)
                        }
                    },
                )
            }
        }
    }

    override suspend fun readTemperatureCelsius(deviceId: String, capability: SensorCapability): Double? {
        requireCapability(deviceId, capability)
        require(capability.kind == SensorKind.TEMPERATURE) { "Capability is not a temperature sensor" }
        val nodeId = requireNodeId(deviceId)
        val rawValue = withConnectedDevice(nodeId) { devicePointer ->
            val cluster = ChipClusters.TemperatureMeasurementCluster(devicePointer, capability.endpointId)
            awaitCallback<Int?> { result ->
                cluster.readMeasuredValueAttribute(
                    object : ChipClusters.TemperatureMeasurementCluster.MeasuredValueAttributeCallback {
                        override fun onSuccess(value: Int?) {
                            result.complete(value)
                        }

                        override fun onError(error: Exception) {
                            result.completeExceptionally(error)
                        }
                    },
                )
            }
        }
        return MatterValueConverter.temperatureCelsius(rawValue)
    }

    override suspend fun setHueSaturation(
        deviceId: String,
        capability: ColorCapability,
        hue: Int,
        saturation: Int,
    ) {
        requireCapability(deviceId, capability)
        check(capability.supportsHueSaturation) { "Device does not expose Hue/Saturation color control" }
        require(hue in 0..254 && saturation in 0..254) { "Hue and saturation must be between 0 and 254" }
        val nodeId = requireNodeId(deviceId)
        withConnectedDevice(nodeId) { pointer ->
            val cluster = ChipClusters.ColorControlCluster(pointer, capability.endpointId)
            awaitCallback<Unit> { result ->
                cluster.moveToHueAndSaturation(defaultClusterCallback(result), hue, saturation, 0, 0, 0)
            }
        }
    }

    override suspend fun setColorTemperature(deviceId: String, capability: ColorCapability, mireds: Int) {
        requireCapability(deviceId, capability)
        check(capability.supportsColorTemperature) { "Device does not expose color temperature control" }
        require(mireds in 1..65279) { "Color temperature is outside the Matter mired range" }
        val nodeId = requireNodeId(deviceId)
        withConnectedDevice(nodeId) { pointer ->
            val cluster = ChipClusters.ColorControlCluster(pointer, capability.endpointId)
            awaitCallback<Unit> { result ->
                cluster.moveToColorTemperature(defaultClusterCallback(result), mireds, 0, 0, 0)
            }
        }
    }

    override suspend fun readColor(deviceId: String, capability: ColorCapability): ColorState {
        requireCapability(deviceId, capability)
        val nodeId = requireNodeId(deviceId)
        return withConnectedDevice(nodeId) { pointer ->
            val cluster = ChipClusters.ColorControlCluster(pointer, capability.endpointId)
            ColorState(
                hue = if (capability.supportsHueSaturation) readInteger { cluster.readCurrentHueAttribute(it) } else null,
                saturation = if (capability.supportsHueSaturation) readInteger { cluster.readCurrentSaturationAttribute(it) } else null,
                colorTemperatureMireds =
                    if (capability.supportsColorTemperature) readInteger { cluster.readColorTemperatureMiredsAttribute(it) } else null,
            )
        }
    }

    override suspend fun setLocked(
        deviceId: String,
        capability: DoorLockCapability,
        locked: Boolean,
        pin: ByteArray?,
    ) {
        requireCapability(deviceId, capability)
        check(if (locked) capability.supportsLock else capability.supportsUnlock) {
            "Device does not accept the requested lock command"
        }
        val nodeId = requireNodeId(deviceId)
        withConnectedDevice(nodeId) { pointer ->
            val cluster = ChipClusters.DoorLockCluster(pointer, capability.endpointId)
            val pinCode = Optional.ofNullable(pin?.copyOf())
            awaitCallback<Unit> { result ->
                if (locked) {
                    cluster.lockDoor(defaultClusterCallback(result), pinCode, TIMED_INTERACTION_TIMEOUT_MILLIS)
                } else {
                    cluster.unlockDoor(defaultClusterCallback(result), pinCode, TIMED_INTERACTION_TIMEOUT_MILLIS)
                }
            }
        }
    }

    override suspend fun readLockState(deviceId: String, capability: DoorLockCapability): LockState {
        requireCapability(deviceId, capability)
        val nodeId = requireNodeId(deviceId)
        val rawState = withConnectedDevice(nodeId) { pointer ->
            val cluster = ChipClusters.DoorLockCluster(pointer, capability.endpointId)
            awaitCallback<Int?> { result ->
                cluster.readLockStateAttribute(
                    object : ChipClusters.DoorLockCluster.LockStateAttributeCallback {
                        override fun onSuccess(value: Int?) {
                            result.complete(value)
                        }

                        override fun onError(error: Exception) {
                            result.completeExceptionally(error)
                        }
                    },
                )
            }
        }
        return MatterValueConverter.lockState(rawState)
    }

    override suspend fun readThermostat(deviceId: String, capability: ThermostatCapability): ThermostatState {
        requireCapability(deviceId, capability)
        val nodeId = requireNodeId(deviceId)
        return withConnectedDevice(nodeId) { pointer ->
            val cluster = ChipClusters.ThermostatCluster(pointer, capability.endpointId)
            ThermostatState(
                localTemperatureCelsius = if (capability.hasLocalTemperature) {
                    MatterValueConverter.temperatureCelsius(
                        awaitCallback<Int?> { result ->
                            cluster.readLocalTemperatureAttribute(
                                object : ChipClusters.ThermostatCluster.LocalTemperatureAttributeCallback {
                                    override fun onSuccess(value: Int?) {
                                        result.complete(value)
                                    }

                                    override fun onError(error: Exception) {
                                        result.completeExceptionally(error)
                                    }
                                },
                            )
                        },
                    )
                } else null,
                occupiedCoolingSetpointCelsius = if (capability.hasOccupiedCoolingSetpoint) {
                    MatterValueConverter.temperatureCelsius(readInteger { cluster.readOccupiedCoolingSetpointAttribute(it) })
                } else null,
                occupiedHeatingSetpointCelsius = if (capability.hasOccupiedHeatingSetpoint) {
                    MatterValueConverter.temperatureCelsius(readInteger { cluster.readOccupiedHeatingSetpointAttribute(it) })
                } else null,
            )
        }
    }

    override suspend fun setCoolingSetpoint(deviceId: String, capability: ThermostatCapability, celsius: Double) {
        requireCapability(deviceId, capability)
        check(capability.hasOccupiedCoolingSetpoint) { "Device has no occupied cooling setpoint" }
        writeThermostatSetpoint(deviceId, capability, celsius, cooling = true)
    }

    override suspend fun setHeatingSetpoint(deviceId: String, capability: ThermostatCapability, celsius: Double) {
        requireCapability(deviceId, capability)
        check(capability.hasOccupiedHeatingSetpoint) { "Device has no occupied heating setpoint" }
        writeThermostatSetpoint(deviceId, capability, celsius, cooling = false)
    }

    override suspend fun setFanPercent(deviceId: String, capability: FanCapability, percent: Int) {
        require(percent in 0..100) { "Fan percent must be between 0 and 100" }
        writeTypedAttribute(deviceId, capability, FAN_PERCENT_SETTING_ATTRIBUTE, TlvWriter().putUnsigned(AnonymousTag, percent).getEncoded())
    }

    override suspend fun setWindowCoveringPosition(
        deviceId: String,
        capability: WindowCoveringCapability,
        percent: Double,
    ) {
        check(capability.supportsLiftPosition) { "Window covering does not expose lift position" }
        require(percent in 0.0..100.0) { "Window covering position must be between 0 and 100" }
        val hundredths = (percent * 100).toInt()
        val tlv = TlvWriter().startStructure(AnonymousTag)
            .putUnsigned(ContextSpecificTag(0), hundredths)
            .endStructure().getEncoded()
        invokeTypedCommand(deviceId, capability, WINDOW_GO_TO_LIFT_PERCENT_COMMAND, tlv)
    }

    override suspend fun openWindowCovering(deviceId: String, capability: WindowCoveringCapability) =
        invokeTypedCommand(deviceId, capability, WINDOW_OPEN_COMMAND, emptyCommand())

    override suspend fun closeWindowCovering(deviceId: String, capability: WindowCoveringCapability) =
        invokeTypedCommand(deviceId, capability, WINDOW_CLOSE_COMMAND, emptyCommand())

    override suspend fun stopWindowCovering(deviceId: String, capability: WindowCoveringCapability) =
        invokeTypedCommand(deviceId, capability, WINDOW_STOP_COMMAND, emptyCommand())

    override suspend fun controlMedia(
        deviceId: String,
        capability: MediaPlaybackCapability,
        action: MediaPlaybackAction,
    ) {
        val commandId = when (action) {
            MediaPlaybackAction.PLAY -> 0L
            MediaPlaybackAction.PAUSE -> 1L
            MediaPlaybackAction.STOP -> 2L
            MediaPlaybackAction.PREVIOUS -> 4L
            MediaPlaybackAction.NEXT -> 5L
        }
        invokeTypedCommand(deviceId, capability, commandId, emptyCommand())
    }

    override suspend fun readRawAttribute(
        deviceId: String,
        capability: MatterCapability,
        attributeId: Long,
    ): RawAttributeValue {
        requireCapability(deviceId, capability)
        MatterRawPathValidator.requireAttribute(capability, attributeId)
        val nodeId = requireNodeId(deviceId)
        return withConnectedDevice(nodeId) { pointer -> readRawAttribute(pointer, capability, attributeId) }
    }

    override suspend fun writeRawAttribute(
        deviceId: String,
        capability: MatterCapability,
        attributeId: Long,
        tlv: ByteArray,
        timedRequestTimeoutMillis: Int,
    ): RawWriteResult {
        requireCapability(deviceId, capability)
        MatterRawPathValidator.requireWritableAttribute(capability, attributeId)
        MatterRawPathValidator.requireTimedRequestTimeout(timedRequestTimeoutMillis)
        val nodeId = requireNodeId(deviceId)
        return withConnectedDevice(nodeId) { pointer ->
            rawInteraction.writeAttribute(
                pointer,
                capability,
                attributeId,
                tlv,
                timedRequestTimeoutMillis,
            )
        }
    }

    override suspend fun invokeRawCommand(
        deviceId: String,
        capability: MatterCapability,
        commandId: Long,
        tlv: ByteArray,
        timedRequestTimeoutMillis: Int,
    ): RawInvokeResult {
        requireCapability(deviceId, capability)
        MatterRawPathValidator.requireCommand(capability, commandId)
        MatterRawPathValidator.requireTimedRequestTimeout(timedRequestTimeoutMillis)
        val nodeId = requireNodeId(deviceId)
        return withConnectedDevice(nodeId) { pointer ->
            rawInteraction.invokeCommand(
                pointer,
                capability,
                commandId,
                tlv,
                timedRequestTimeoutMillis,
            )
        }
    }

    override fun subscribeRaw(
        deviceId: String,
        capability: MatterCapability,
        attributeIds: Set<Long>,
        eventIds: Set<Long>,
        minIntervalSeconds: Int,
        maxIntervalSeconds: Int,
    ): Flow<MatterSubscriptionEvent> = callbackFlow {
        check(!closed.get()) { "Matter SDK is closed" }
        requireCapability(deviceId, capability)
        MatterRawPathValidator.requireSubscription(
            capability,
            attributeIds,
            eventIds,
            minIntervalSeconds,
            maxIntervalSeconds,
        )

        val nodeId = requireNodeId(deviceId)
        val devicePointer = connectedDevicePointer(nodeId)
        val subscriptionId = AtomicLong(NO_SUBSCRIPTION_ID)
        val flowClosed = AtomicBoolean(false)
        val cleanupDone = AtomicBoolean(false)
        val attributePaths =
            attributeIds.map { attributeId ->
                ChipAttributePath.newInstance(
                    capability.endpointId,
                    capability.cluster.id,
                    attributeId,
                )
            }
        val eventPaths =
            eventIds.map { eventId ->
                ChipEventPath.newInstance(
                    capability.endpointId,
                    capability.cluster.id,
                    eventId,
                )
            }

        fun shutdownSubscription(id: Long) {
            if (id != NO_SUBSCRIPTION_ID) {
                runCatching {
                    runtime.controller.shutdownSubscriptions(
                        runtime.controller.fabricIndex,
                        nodeId,
                        id,
                    )
                }.onFailure { error ->
                    Log.w(TAG, "Unable to close Matter subscription $id", error)
                }
            }
        }

        lateinit var closeSubscription: () -> Unit
        val cleanup = {
            if (cleanupDone.compareAndSet(false, true)) {
                flowClosed.set(true)
                shutdownSubscription(subscriptionId.getAndSet(NO_SUBSCRIPTION_ID))
                runtime.controller.releaseConnectedDevicePointer(devicePointer)
                activeSubscriptionClosers.remove(closeSubscription)
            }
        }
        closeSubscription = {
            channel.close(IllegalStateException("Matter SDK is closed"))
            cleanup()
        }
        activeSubscriptionClosers.add(closeSubscription)
        if (closed.get()) {
            closeSubscription()
            error("Matter SDK is closed")
        }

        try {
            runtime.controller.subscribeToPath(
                object : SubscriptionEstablishedCallback {
                    override fun onSubscriptionEstablished(id: Long) {
                        subscriptionId.set(id)
                        if (flowClosed.get()) {
                            shutdownSubscription(subscriptionId.getAndSet(NO_SUBSCRIPTION_ID))
                        } else {
                            trySend(MatterSubscriptionEvent.Established(id))
                        }
                    }
                },
                object : ResubscriptionAttemptCallback {
                    override fun onResubscriptionAttempt(
                        terminationCause: Long,
                        nextResubscribeIntervalMillis: Long,
                    ) {
                        trySend(
                            MatterSubscriptionEvent.Resubscribing(
                                terminationCause,
                                nextResubscribeIntervalMillis,
                            ),
                        )
                    }
                },
                object : ReportCallback {
                    override fun onReport(nodeState: NodeState) {
                        val clusterState =
                            nodeState.getEndpointState(capability.endpointId)
                                ?.getClusterState(capability.cluster.id)
                                ?: return
                        attributeIds.forEach { attributeId ->
                            clusterState.getAttributeState(attributeId)?.let { state ->
                                trySend(
                                    MatterSubscriptionEvent.AttributeChanged(
                                        RawAttributeValue(
                                            capability.endpointId,
                                            capability.cluster.id,
                                            attributeId,
                                            state.tlv,
                                            state.json?.toString(),
                                        ),
                                    ),
                                )
                            }
                        }
                        eventIds.forEach { eventId ->
                            clusterState.getEventState(eventId)?.forEach { state ->
                                trySend(
                                    MatterSubscriptionEvent.EventReceived(
                                        RawEventValue(
                                            capability.endpointId,
                                            capability.cluster.id,
                                            eventId,
                                            state.eventNumber,
                                            state.priorityLevel,
                                            state.timestampType,
                                            state.timestampValue,
                                            state.tlv,
                                            state.json?.toString(),
                                        ),
                                    ),
                                )
                            }
                        }
                    }

                    override fun onError(
                        attributePath: ChipAttributePath?,
                        eventPath: ChipEventPath?,
                        error: Exception,
                    ) {
                        close(error)
                    }

                    override fun onDone() = Unit
                },
                devicePointer,
                attributePaths,
                eventPaths,
                minIntervalSeconds,
                maxIntervalSeconds,
                true,
                true,
                INTERACTION_TIMEOUT_MILLIS.toInt(),
            )
        } catch (error: Throwable) {
            cleanup()
            throw error
        }

        awaitClose { cleanup() }
    }

    override suspend fun removeDevice(deviceId: String) {
        val nodeId = requireNodeId(deviceId)
        deviceStateObservers.remove(deviceId)?.cancelAndJoin()
        try {
            deviceUnpairer.unpair(nodeId) { callback ->
                runtime.controller.unpairDeviceCallback(nodeId, callback)
            }
        } catch (error: Throwable) {
            startDeviceStateObservation(deviceId)
            throw error
        }
        nodeIdStore.removeCommissioned(nodeId)
        mutableDevices.value = mutableDevices.value.filterNot { it.id == deviceId }
        mutableCapabilityStates.value = mutableCapabilityStates.value - deviceId
        Log.i(TAG, "Removed Matter node $nodeId from its fabric and local device list")
    }

    override fun close() {
        if (closed.compareAndSet(false, true)) {
            sdkScope.cancel()
            deviceStateObservers.clear()
            activeSubscriptionClosers.toList().forEach { closeSubscription -> closeSubscription() }
            runtime.close()
        }
    }

    private fun parsePayload(setupCode: SetupCode): OnboardingPayload =
        when (setupCode.format) {
            SetupCode.Format.QR -> payloadParser.parseQrCode(setupCode.value)
            SetupCode.Format.MANUAL -> payloadParser.parseManualPairingCode(setupCode.value)
        }

    private fun requireDevice(deviceId: String): MatterDevice =
        mutableDevices.value.firstOrNull { it.id == deviceId } ?: error("Unknown device")

    private fun requireNodeId(deviceId: String): Long {
        requireDevice(deviceId)
        return deviceId.toLongOrNull() ?: error("Invalid Matter node ID")
    }

    private suspend fun invokeOnOff(
        nodeId: Long,
        endpointId: Int,
        command: (ChipClusters.OnOffCluster, ChipClusters.DefaultClusterCallback) -> Unit,
    ) {
        withOnOffCluster(nodeId, endpointId) { cluster ->
            awaitCallback<Unit> { continuation ->
                command(
                    cluster,
                    object : ChipClusters.DefaultClusterCallback {
                        override fun onSuccess() {
                            continuation.complete(Unit)
                        }

                        override fun onError(error: Exception) {
                            continuation.completeExceptionally(error)
                        }
                    },
                )
            }
        }
        Log.i(TAG, "Invoked OnOff command on node $nodeId endpoint $endpointId")
    }

    private suspend fun writeTypedAttribute(
        deviceId: String,
        capability: MatterCapability,
        attributeId: Long,
        tlv: ByteArray,
    ) {
        requireCapability(deviceId, capability)
        MatterRawPathValidator.requireAttribute(capability, attributeId)
        val nodeId = requireNodeId(deviceId)
        val result = withConnectedDevice(nodeId) { pointer ->
            rawInteraction.writeAttribute(pointer, capability, attributeId, tlv, 0)
        }
        check(result.statusCode == 0) { "Matter attribute write failed (${result.statusCode})" }
    }

    private suspend fun invokeTypedCommand(
        deviceId: String,
        capability: MatterCapability,
        commandId: Long,
        tlv: ByteArray,
    ) {
        requireCapability(deviceId, capability)
        MatterRawPathValidator.requireCommand(capability, commandId)
        val nodeId = requireNodeId(deviceId)
        val result = withConnectedDevice(nodeId) { pointer ->
            rawInteraction.invokeCommand(pointer, capability, commandId, tlv, 0)
        }
        check(result.statusCode == 0L) { "Matter command failed (${result.statusCode})" }
    }

    private fun emptyCommand(): ByteArray = TlvWriter().startStructure(AnonymousTag).endStructure().getEncoded()

    private fun defaultClusterCallback(result: CompletableDeferred<Unit>) =
        object : ChipClusters.DefaultClusterCallback {
            override fun onSuccess() {
                result.complete(Unit)
            }

            override fun onError(error: Exception) {
                result.completeExceptionally(error)
            }
        }

    private suspend fun readInteger(
        read: (ChipClusters.IntegerAttributeCallback) -> Unit,
    ): Int = awaitCallback { result ->
        read(
            object : ChipClusters.IntegerAttributeCallback {
                override fun onSuccess(value: Int) {
                    result.complete(value)
                }

                override fun onError(error: Exception) {
                    result.completeExceptionally(error)
                }
            },
        )
    }

    private suspend fun writeThermostatSetpoint(
        deviceId: String,
        capability: ThermostatCapability,
        celsius: Double,
        cooling: Boolean,
    ) {
        val rawValue = MatterValueConverter.temperatureHundredths(celsius)
        val nodeId = requireNodeId(deviceId)
        withConnectedDevice(nodeId) { pointer ->
            val cluster = ChipClusters.ThermostatCluster(pointer, capability.endpointId)
            awaitCallback<Unit> { result ->
                if (cooling) {
                    cluster.writeOccupiedCoolingSetpointAttribute(defaultClusterCallback(result), rawValue)
                } else {
                    cluster.writeOccupiedHeatingSetpointAttribute(defaultClusterCallback(result), rawValue)
                }
            }
        }
    }

    private suspend fun readRawAttribute(
        devicePointer: Long,
        capability: MatterCapability,
        attributeId: Long,
    ): RawAttributeValue {
        val result = CompletableDeferred<RawAttributeValue>()
        val path = ChipAttributePath.newInstance(capability.endpointId, capability.cluster.id, attributeId)
        runtime.controller.readAttributePath(
            object : ReportCallback {
                override fun onReport(nodeState: NodeState) {
                    val state = nodeState.getEndpointState(capability.endpointId)
                        ?.getClusterState(capability.cluster.id)
                        ?.getAttributeState(attributeId)
                        ?: return
                    result.complete(
                        RawAttributeValue(
                            endpointId = capability.endpointId,
                            clusterId = capability.cluster.id,
                            attributeId = attributeId,
                            tlv = state.tlv.copyOf(),
                            json = state.json?.toString(),
                        ),
                    )
                }

                override fun onError(
                    attributePath: ChipAttributePath?,
                    eventPath: ChipEventPath?,
                    error: Exception,
                ) {
                    result.completeExceptionally(error)
                }

                override fun onDone() {
                    if (!result.isCompleted) {
                        result.completeExceptionally(IllegalStateException("Matter attribute read returned no value"))
                    }
                }
            },
            devicePointer,
            listOf(path),
            INTERACTION_TIMEOUT_MILLIS.toInt(),
        )
        return result.await()
    }

    private suspend fun <T> withOnOffCluster(
        nodeId: Long,
        endpointId: Int,
        interaction: suspend (ChipClusters.OnOffCluster) -> T,
    ): T = withConnectedDevice(nodeId) { devicePointer ->
        interaction(ChipClusters.OnOffCluster(devicePointer, endpointId))
    }

    private suspend fun <T> withConnectedDevice(
        nodeId: Long,
        timeoutMillis: Long = INTERACTION_TIMEOUT_MILLIS,
        interaction: suspend (Long) -> T,
    ): T = withTimeout(timeoutMillis) {
        check(!closed.get()) { "Matter SDK is closed" }
        val devicePointer = connectedDevicePointer(nodeId)
        try {
            interaction(devicePointer)
        } finally {
            runtime.controller.releaseConnectedDevicePointer(devicePointer)
        }
    }

    private suspend fun connectedDevicePointer(nodeId: Long): Long =
        suspendCancellableCoroutine { continuation ->
            runtime.controller.getConnectedDevicePointer(
                nodeId,
                object : GetConnectedDeviceCallback {
                    override fun onDeviceConnected(devicePointer: Long) {
                        if (continuation.isActive) {
                            continuation.invokeOnCancellation {
                                runtime.controller.releaseConnectedDevicePointer(devicePointer)
                            }
                            continuation.resumeWith(Result.success(devicePointer))
                        } else {
                            runtime.controller.releaseConnectedDevicePointer(devicePointer)
                        }
                    }

                    override fun onConnectionFailure(nodeId: Long, error: Exception) {
                        if (continuation.isActive) continuation.resumeWith(Result.failure(error))
                    }
                },
            )
        }

    private suspend fun <T> awaitCallback(
        register: (CompletableDeferred<T>) -> Unit,
    ): T {
        val result = CompletableDeferred<T>()
        register(result)
        return result.await()
    }

    private fun restoredDevice(nodeId: Long) =
        MatterDevice(
            id = nodeId.toString(),
            name = DEFAULT_DEVICE_NAME,
            room = defaultRoom,
            type = DeviceType.UNKNOWN,
            connectionMode = ConnectionMode.LOCAL,
            availability = DeviceAvailability.CONNECTING,
            isOn = false,
        )

    private fun onOffEndpoint(deviceId: String): Int? =
        requireDevice(deviceId).capabilities?.let(MatterCapabilityInterpreter::onOffEndpoint)

    private fun requireOnOffEndpoint(deviceId: String): Int =
        requireOnOffCapability(deviceId).endpointId

    private fun requireOnOffCapability(deviceId: String): OnOffCapability {
        val capabilities = requireDevice(deviceId).capabilities
            ?: error("Device capabilities have not been discovered")
        return MatterCapabilityInterpreter.onOffCapability(capabilities)
            ?: error("Device does not expose a discovered On/Off capability")
    }

    private fun startDeviceStateObservation(deviceId: String) {
        if (closed.get() || requireDevice(deviceId).capabilities == null) return
        lateinit var observer: Job
        observer = sdkScope.launch {
            observeCapabilities(deviceId).collect { event ->
                when (event) {
                    is CapabilitySubscriptionEvent.Updated -> {
                        mutableCapabilityStates.update { states ->
                            states + (deviceId to (states[deviceId].orEmpty() + (event.state.key to event.state)))
                        }
                        updateDeviceIfPresent(deviceId) {
                            copy(
                                isOn = (event.state as? MatterCapabilityState.OnOff)?.isOn ?: isOn,
                                availability = DeviceAvailability.ONLINE,
                            )
                        }
                        mutableDevices.value.firstOrNull { it.id == deviceId }?.let(nodeIdStore::saveDevice)
                    }
                    is CapabilitySubscriptionEvent.Resubscribing -> {
                        updateDeviceIfPresent(deviceId) { copy(availability = DeviceAvailability.CONNECTING) }
                    }
                    is CapabilitySubscriptionEvent.Unavailable -> {
                        Log.w(TAG, "Capability ${event.key} unavailable for node $deviceId: ${event.message}")
                    }
                }
            }
        }
        deviceStateObservers.put(deviceId, observer)?.cancel()
        observer.invokeOnCompletion { deviceStateObservers.remove(deviceId, observer) }
    }

    private fun requireCapability(deviceId: String, capability: MatterCapability) {
        val discovered = requireDevice(deviceId).capabilities
            ?: error("Device capabilities have not been discovered")
        check(
            discovered.endpoints
                .firstOrNull { it.endpointId == capability.endpointId }
                ?.capabilities
                ?.contains(capability) == true,
        ) {
            "Capability does not belong to this device"
        }
    }

    private inline fun updateDevice(
        deviceId: String,
        transform: MatterDevice.() -> MatterDevice,
    ) {
        mutableDevices.value =
            mutableDevices.value.map { device ->
                if (device.id == deviceId) device.transform() else device
            }
    }

    private inline fun updateDeviceIfPresent(
        deviceId: String,
        transform: MatterDevice.() -> MatterDevice,
    ) {
        mutableDevices.value =
            mutableDevices.value.map { device ->
                if (device.id == deviceId) device.transform() else device
            }
    }

    private sealed interface CommissioningResult {
        data class Success(val nodeId: Long) : CommissioningResult
        data class Failure(val message: String) : CommissioningResult
    }

    private class CommissioningListener(
        private val result: CompletableDeferred<CommissioningResult>,
    ) : ChipDeviceController.CompletionListener {
        override fun onCommissioningComplete(nodeId: Long, errorCode: Long) {
            if (errorCode == 0L) {
                result.complete(CommissioningResult.Success(nodeId))
            } else {
                result.complete(CommissioningResult.Failure("Commissioning failed ($errorCode)"))
            }
        }

        override fun onPairingComplete(code: Long) {
            if (code != 0L) result.complete(CommissioningResult.Failure("Pairing failed ($code)"))
        }

        override fun onError(error: Throwable?) {
            result.complete(
                CommissioningResult.Failure(error?.message ?: "Matter commissioning failed"),
            )
        }

        override fun onConnectDeviceComplete() = Unit
        override fun onStatusUpdate(status: Int) = Unit
        override fun onPairingDeleted(code: Long) = Unit
        override fun onReadCommissioningInfo(vendorId: Int, productId: Int, wifiEndpointId: Int, threadEndpointId: Int) = Unit
        override fun onCommissioningStatusUpdate(nodeId: Long, stage: String, errorCode: Long) = Unit
        override fun onCommissioningStageStart(nodeId: Long, stage: String) = Unit
        override fun onNotifyChipConnectionClosed() = Unit
        override fun onCloseBleComplete() = Unit
        override fun onOpCSRGenerationComplete(csr: ByteArray) = Unit
        override fun onICDRegistrationInfoRequired() = Unit
        override fun onICDRegistrationComplete(errorCode: Long, icdDeviceInfo: ICDDeviceInfo) = Unit
    }

    private companion object {
        const val DEFAULT_DEVICE_NAME = "Matter device"
        const val TAG = "MatterAppSdk"
        const val INTERACTION_TIMEOUT_MILLIS = 30_000L
        const val CAPABILITY_DISCOVERY_TIMEOUT_MILLIS = 120_000L
        const val COMMISSIONING_TIMEOUT_MILLIS = 180_000L
        const val TIMED_INTERACTION_TIMEOUT_MILLIS = 10_000
        const val NO_SUBSCRIPTION_ID = -1L
        const val ON_OFF_MIN_INTERVAL_SECONDS = 1
        const val ON_OFF_MAX_INTERVAL_SECONDS = 60
        const val ATTESTATION_DECISION_TIMEOUT_SECONDS = 30
        const val MAX_COMMISSIONING_WINDOW_SECONDS = 900
        const val ENHANCED_WINDOW_ITERATIONS = 1000L
        const val MAX_DISCRIMINATOR = 4095
        const val FAN_PERCENT_SETTING_ATTRIBUTE = 2L
        const val WINDOW_OPEN_COMMAND = 0L
        const val WINDOW_CLOSE_COMMAND = 1L
        const val WINDOW_STOP_COMMAND = 2L
        const val WINDOW_GO_TO_LIFT_PERCENT_COMMAND = 5L
    }
}
