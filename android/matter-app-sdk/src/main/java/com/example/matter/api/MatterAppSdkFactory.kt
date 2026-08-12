package com.example.matter.api

import android.content.Context

object MatterAppSdkFactory {
    @Volatile
    private var instance: MatterAppSdk? = null

    fun create(context: Context): MatterAppSdk =
        instance ?: synchronized(this) {
            instance ?: RealMatterAppSdk(context.applicationContext).also { instance = it }
        }

    fun shutdown() {
        synchronized(this) {
            instance?.close()
            instance = null
        }
    }
}
