package com.example.matter.api

import android.content.Context

object MatterAppSdkFactory {
    fun create(context: Context): MatterAppSdk = RealMatterAppSdk(context.applicationContext)
}
