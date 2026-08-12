package com.example.matterhome.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.activity.compose.BackHandler
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.matter.api.CommissioningEvent
import com.example.matterhome.AppViewModel
import com.example.matterhome.theme.Green

private data class ProgressStage(val label: String, val eventType: Class<out CommissioningEvent>)

@Composable
fun CommissioningProgressScreen(
    viewModel: AppViewModel,
    onClose: () -> Unit,
    onRetry: () -> Unit,
    onCompleted: (String) -> Unit,
) {
    val state by viewModel.state.collectAsState()
    val event = state.commissioningEvent
    val stages = listOf(
        ProgressStage("Preparing", CommissioningEvent.Preparing::class.java),
        ProgressStage("Finding your device", CommissioningEvent.FindingDevice::class.java),
        ProgressStage("Connecting securely", CommissioningEvent.Connecting::class.java),
        ProgressStage("Joining Wi-Fi", CommissioningEvent.JoiningNetwork::class.java),
        ProgressStage("Adding to your home", CommissioningEvent.AddingToHome::class.java),
    )
    val activeIndex = stages.indexOfFirst { it.eventType.isInstance(event) }
    BackHandler(onBack = onClose)
    LaunchedEffect(event) {
        if (event is CommissioningEvent.Completed) onCompleted(event.device.id)
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            IconButton(onClick = onClose) { Icon(Icons.Default.Close, contentDescription = "Cancel setup") }
        }
        Spacer(Modifier.height(32.dp))
        Text("Adding your device", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
        Text("Keep your phone near the device until setup is complete.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(40.dp))
        if (event is CommissioningEvent.Failed) {
            Text(event.message, color = MaterialTheme.colorScheme.error)
            Spacer(Modifier.height(20.dp))
            Button(onClick = onRetry, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                Text("Back to device setup")
            }
        } else {
            stages.forEachIndexed { index, stage ->
                val complete = event is CommissioningEvent.Completed || index < activeIndex
                val current = index == activeIndex
                Row(
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    when {
                        complete -> Icon(Icons.Default.CheckCircle, contentDescription = "Completed", tint = Green)
                        current -> CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                        else -> Icon(Icons.Default.RadioButtonUnchecked, contentDescription = "Pending", tint = Color(0xFF9AA4A1))
                    }
                    Text(
                        stage.label,
                        modifier = Modifier.padding(start = 16.dp),
                        color = if (complete || current) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
