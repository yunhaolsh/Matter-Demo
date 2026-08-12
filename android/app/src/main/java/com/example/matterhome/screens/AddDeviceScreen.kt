package com.example.matterhome.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FlashlightOn
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.matterhome.AppViewModel

@Composable
fun AddDeviceScreen(viewModel: AppViewModel, onBack: () -> Unit, onCodeReady: () -> Unit) {
    var showManualCode by remember { mutableStateOf(false) }
    var torchOn by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).verticalScroll(rememberScrollState()).padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.Close, contentDescription = "Close scanner")
            }
            Text(
                "Add a Matter device",
                modifier = Modifier.weight(1f),
                color = MaterialTheme.colorScheme.onBackground,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            IconButton(onClick = { torchOn = !torchOn }) {
                Icon(
                    Icons.Default.FlashlightOn,
                    contentDescription = if (torchOn) "Turn flashlight off" else "Turn flashlight on",
                    tint = if (torchOn) Color(0xFF007F7B) else MaterialTheme.colorScheme.onBackground,
                )
            }
        }
        Spacer(Modifier.height(24.dp))
        Text("Position the device QR code inside the frame", color = MaterialTheme.colorScheme.onBackground)
        Spacer(Modifier.height(24.dp))
        Box(
            modifier = Modifier
                .widthIn(max = 360.dp)
                .fillMaxWidth()
                .aspectRatio(1f)
                .border(2.dp, Color(0xFF58D1C8), RoundedCornerShape(8.dp))
                .background(Color(0xFF0D1211), RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Default.QrCodeScanner,
                contentDescription = null,
                tint = Color(0xFF788583),
                modifier = Modifier.size(72.dp),
            )
        }
        Spacer(Modifier.height(24.dp))
        Button(
            onClick = {
                if (viewModel.parseSetupCode("MT:DEMO123")) onCodeReady()
            },
            modifier = Modifier.fillMaxWidth().height(52.dp),
        ) {
            Text("Simulate QR scan")
        }
        TextButton(onClick = { showManualCode = true }) {
            Text("Enter setup code manually")
        }
    }

    if (showManualCode) {
        ManualCodeDialog(
            viewModel = viewModel,
            onDismiss = { showManualCode = false },
            onCodeReady = {
                showManualCode = false
                onCodeReady()
            },
        )
    }
}

@Composable
private fun ManualCodeDialog(viewModel: AppViewModel, onDismiss: () -> Unit, onCodeReady: () -> Unit) {
    var code by remember { mutableStateOf("") }
    var localError by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Manual setup code") },
        text = {
            Column {
                Text("Find the 11-digit code beside the Matter QR code.")
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = code,
                    onValueChange = {
                        code = it
                        localError = null
                    },
                    label = { Text("Setup code") },
                    supportingText = { localError?.let { Text(it) } },
                    isError = localError != null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                )
            }
        },
        confirmButton = {
            Button(onClick = {
                if (viewModel.parseSetupCode(code)) onCodeReady()
                else localError = "Check the code and try again"
            }) { Text("Continue") }
        },
        dismissButton = { OutlinedButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
