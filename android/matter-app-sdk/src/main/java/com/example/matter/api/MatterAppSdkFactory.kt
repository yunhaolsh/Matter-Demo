package com.example.matter.api

import android.content.Context

object MatterAppSdkFactory {
    @Volatile
    private var instance: MatterAppSdk? = null

    fun create(context: Context, configuration: MatterSdkConfiguration = MatterSdkConfiguration()): MatterAppSdk =
        instance ?: synchronized(this) {
            instance ?: RealMatterAppSdk(context.applicationContext, configuration).also { instance = it }
        }

    fun shutdown() {
        synchronized(this) {
            instance?.close()
            instance = null
        }
    }
}
