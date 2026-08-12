package com.example.matter.controller

import android.content.Context
import chip.devicecontroller.ChipDeviceController
import chip.devicecontroller.ControllerParams
import chip.platform.AndroidBleManager
import chip.platform.AndroidChipPlatform
import chip.platform.AndroidNfcCommissioningManager
import chip.platform.ChipMdnsCallbackImpl
import chip.platform.DiagnosticDataProviderImpl
import chip.platform.NsdManagerServiceBrowser
import chip.platform.NsdManagerServiceResolver
import chip.platform.PreferencesConfigurationManager
import chip.platform.PreferencesKeyValueStoreManager
import java.io.Closeable
import java.util.concurrent.atomic.AtomicBoolean

internal class MatterControllerRuntime private constructor(
    @Suppress("unused") private val platform: AndroidChipPlatform,
    internal val bleManager: AndroidBleManager,
    internal val controller: ChipDeviceController,
) : Closeable {
    private val closed = AtomicBoolean(false)

    override fun close() {
        if (closed.compareAndSet(false, true)) {
            controller.shutdownCommissioning()
            controller.shutdownSubscriptions()
            controller.close()
        }
    }

    companion object {
        private const val DEMO_VENDOR_ID = 0xFFF4

        fun create(context: Context): MatterControllerRuntime {
            val applicationContext = context.applicationContext
            ChipDeviceController.loadJni()

            val resolverState = NsdManagerServiceResolver.NsdManagerResolverAvailState()
            val bleManager = AndroidBleManager(applicationContext)
            val platform =
                AndroidChipPlatform(
                    bleManager,
                    AndroidNfcCommissioningManager(),
                    PreferencesKeyValueStoreManager(applicationContext),
                    PreferencesConfigurationManager(applicationContext),
                    NsdManagerServiceResolver(applicationContext, resolverState),
                    NsdManagerServiceBrowser(applicationContext),
                    ChipMdnsCallbackImpl(),
                    DiagnosticDataProviderImpl(applicationContext),
                )
            val controller =
                ChipDeviceController(
                    ControllerParams.newBuilder()
                        .setControllerVendorId(DEMO_VENDOR_ID)
                        .setEnableServerInteractions(true)
                        .build(),
                )
            return MatterControllerRuntime(platform, bleManager, controller)
        }
    }
}
