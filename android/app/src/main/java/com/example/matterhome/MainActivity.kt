package com.example.matterhome

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import com.example.matter.api.MatterAppSdkFactory
import com.example.matter.api.DeviceAttestationPolicy
import com.example.matter.api.MatterSdkConfiguration
import com.example.matterhome.navigation.MatterHomeApp
import com.example.matterhome.theme.MatterHomeTheme

class MainActivity : ComponentActivity() {
    private val appViewModel: AppViewModel by viewModels {
        AppViewModel.factory(
            MatterAppSdkFactory.create(
                applicationContext,
                MatterSdkConfiguration(
                    attestationPolicy = if (BuildConfig.DEBUG) {
                        DeviceAttestationPolicy.AllowDevelopmentDevices
                    } else {
                        DeviceAttestationPolicy.Strict
                    },
                ),
            ),
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MatterHomeTheme {
                MatterHomeApp(appViewModel)
            }
        }
    }
}
