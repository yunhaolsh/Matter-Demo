package com.example.matterhome.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FlashlightOn
import androidx.compose.material.icons.filled.NoPhotography
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.matterhome.AppViewModel
import com.example.matterhome.camera.MatterQrCameraPreview

@Composable
fun AddDeviceScreen(viewModel: AppViewModel, onBack: () -> Unit, onCodeReady: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var showManualCode by remember { mutableStateOf(false) }
    var torchOn by remember { mutableStateOf(false) }
    var hasFlash by remember { mutableStateOf(false) }
    var scanHandled by rememberSaveable { mutableStateOf(false) }
    var scanMessage by remember { mutableStateOf<String?>(null) }
    var cameraGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    val permissionLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            cameraGranted = granted
            if (!granted) scanMessage = "Camera access is required to scan a Matter QR code"
        }

    androidx.compose.runtime.DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                cameraGranted =
                    ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                        PackageManager.PERMISSION_GRANTED
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(Unit) {
        if (!cameraGranted) permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    fun acceptScan(rawValue: String) {
        if (scanHandled) return
        if (!rawValue.startsWith("MT:", ignoreCase = true)) {
            scanMessage = "This is not a Matter setup QR code"
            return
        }
        if (viewModel.parseSetupCode(rawValue)) {
            scanHandled = true
            torchOn = false
            onCodeReady()
        } else {
            scanMessage = "This Matter setup QR code is invalid"
        }
    }

    Column(
        modifier =
            Modifier.fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.Close, contentDescription = "Close scanner")
            }
            Text(
                "Add a Matter device",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            IconButton(
                onClick = { torchOn = !torchOn },
                enabled = cameraGranted && hasFlash,
            ) {
                Icon(
                    Icons.Default.FlashlightOn,
                    contentDescription = if (torchOn) "Turn flashlight off" else "Turn flashlight on",
                    tint =
                        when {
                            torchOn -> Color(0xFF007F7B)
                            cameraGranted && hasFlash -> MaterialTheme.colorScheme.onBackground
                            else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                        },
                )
            }
        }
        Spacer(Modifier.height(20.dp))
        Text("Position the device QR code inside the frame")
        Spacer(Modifier.height(16.dp))
        ScannerFrame(
            cameraGranted = cameraGranted,
            torchOn = torchOn,
            onQrCode = ::acceptScan,
            onCameraReady = { hasFlash = it },
            onCameraError = {
                scanMessage = "Unable to start the camera"
                cameraGranted = false
            },
        )
        scanMessage?.let { message ->
            Spacer(Modifier.height(12.dp))
            Text(message, color = MaterialTheme.colorScheme.error)
        }
        Spacer(Modifier.height(20.dp))
        if (!cameraGranted) {
            Button(
                onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) {
                Text("Allow camera")
            }
            TextButton(
                onClick = {
                    context.startActivity(
                        Intent(
                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.fromParts("package", context.packageName, null),
                        ),
                    )
                },
            ) {
                Text("Open app settings")
            }
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
                scanHandled = true
                torchOn = false
                showManualCode = false
                onCodeReady()
            },
        )
    }
}

@Composable
private fun ScannerFrame(
    cameraGranted: Boolean,
    torchOn: Boolean,
    onQrCode: (String) -> Unit,
    onCameraReady: (Boolean) -> Unit,
    onCameraError: (Throwable) -> Unit,
) {
    val shape = RoundedCornerShape(8.dp)
    Box(
        modifier =
            Modifier.fillMaxWidth()
                .height(360.dp)
                .clip(shape)
                .background(Color(0xFF0D1211))
                .border(2.dp, Color(0xFF58D1C8), shape),
        contentAlignment = Alignment.Center,
    ) {
        if (cameraGranted) {
            MatterQrCameraPreview(
                torchEnabled = torchOn,
                onQrCode = onQrCode,
                onCameraReady = onCameraReady,
                onCameraError = onCameraError,
                modifier = Modifier.fillMaxSize(),
            )
            Icon(
                Icons.Default.QrCodeScanner,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.7f),
                modifier = Modifier.size(72.dp),
            )
        } else {
            Icon(
                Icons.Default.NoPhotography,
                contentDescription = null,
                tint = Color(0xFF788583),
                modifier = Modifier.size(64.dp),
            )
        }
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
            Button(
                onClick = {
                    if (viewModel.parseSetupCode(code)) onCodeReady()
                    else localError = "Check the code and try again"
                },
            ) {
                Text("Continue")
            }
        },
        dismissButton = { OutlinedButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
