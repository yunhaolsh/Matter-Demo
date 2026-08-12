package com.example.matter.api

import android.annotation.SuppressLint
import android.content.Context
import chip.devicecontroller.ChipDeviceController
import chip.devicecontroller.CommissionParameters
import chip.devicecontroller.ICDDeviceInfo
import chip.devicecontroller.NetworkCredentials
import com.example.matter.commissioning.MatterBleTransport
import com.example.matter.controller.MatterControllerRuntime
import com.example.matter.storage.NodeIdStore
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withTimeout
import matter.onboardingpayload.OnboardingPayload
import matter.onboardingpayload.OnboardingPayloadParser

internal class RealMatterAppSdk(context: Context) : MatterAppSdk {
    private val applicationContext = context.applicationContext
    private val runtime = MatterControllerRuntime.create(applicationContext)
    private val nodeIdStore = NodeIdStore(applicationContext)
    private val setupCodeParser = MatterSetupCodeParser()
    private val payloadParser = OnboardingPayloadParser()
    private val commissioningMutex = Mutex()
    private val closed = AtomicBoolean(false)

    private val defaultRoom = MatterRoom("unassigned", "Unassigned")
    private val mutableHome = MutableStateFlow(MatterHome("local-home", "Matter Home"))
    override val home: StateFlow<MatterHome> = mutableHome.asStateFlow()
    private val mutableRooms = MutableStateFlow(listOf(defaultRoom))
    override val rooms: StateFlow<List<MatterRoom>> = mutableRooms.asStateFlow()
    private val mutableDevices = MutableStateFlow<List<MatterDevice>>(emptyList())
    override val devices: StateFlow<List<MatterDevice>> = mutableDevices.asStateFlow()

    override fun parseSetupCode(rawCode: String): SetupCode = setupCodeParser.parse(rawCode)

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
                    val device =
                        MatterDevice(
                            id = commissioningResult.nodeId.toString(),
                            name = "Matter device",
                            room = defaultRoom,
                            type = DeviceType.UNKNOWN,
                            connectionMode = ConnectionMode.LOCAL,
                            availability = DeviceAvailability.ONLINE,
                            isOn = false,
                        )
                    mutableDevices.value = mutableDevices.value + device
                    emit(CommissioningEvent.Completed(device))
                }
            }
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

    override suspend fun refresh(deviceId: String): MatterDevice = requireDevice(deviceId)

    override suspend fun setOnOff(deviceId: String, value: Boolean) {
        requireDevice(deviceId)
        error("Device control is enabled after capability discovery in the next milestone")
    }

    override suspend fun toggle(deviceId: String) {
        requireDevice(deviceId)
        error("Device control is enabled after capability discovery in the next milestone")
    }

    override suspend fun readOnOff(deviceId: String): Boolean {
        requireDevice(deviceId)
        error("Device control is enabled after capability discovery in the next milestone")
    }

    override fun observeOnOff(deviceId: String): Flow<OnOffState> = flow {
        requireDevice(deviceId)
        emit(OnOffState.Unavailable)
    }

    override suspend fun removeDevice(deviceId: String) {
        requireDevice(deviceId)
        error("Device removal is enabled after persistent device storage in the next milestone")
    }

    override fun close() {
        if (closed.compareAndSet(false, true)) {
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
        const val COMMISSIONING_TIMEOUT_MILLIS = 180_000L
    }
}
