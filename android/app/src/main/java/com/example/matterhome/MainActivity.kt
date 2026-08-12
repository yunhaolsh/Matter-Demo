package com.example.matterhome

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.matter.api.FakeMatterAppSdk
import com.example.matterhome.navigation.MatterHomeApp
import com.example.matterhome.theme.MatterHomeTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MatterHomeTheme {
                val appViewModel: AppViewModel = viewModel(factory = AppViewModel.factory(FakeMatterAppSdk()))
                MatterHomeApp(appViewModel)
            }
        }
    }
}
