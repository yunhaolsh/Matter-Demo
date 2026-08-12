package com.example.matterhome.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Ink = Color(0xFF202624)
val Muted = Color(0xFF5E6966)
val Canvas = Color(0xFFF7F9F8)
val Surface = Color(0xFFFFFFFF)
val Teal = Color(0xFF007F7B)
val TealContainer = Color(0xFFD3F3F0)
val Green = Color(0xFF176B49)
val Coral = Color(0xFFC64B43)

private val MatterColors = lightColorScheme(
    primary = Teal,
    onPrimary = Color.White,
    primaryContainer = TealContainer,
    onPrimaryContainer = Color(0xFF003735),
    secondary = Green,
    error = Coral,
    background = Canvas,
    onBackground = Ink,
    surface = Surface,
    onSurface = Ink,
    surfaceVariant = Color(0xFFE7ECEA),
    onSurfaceVariant = Muted,
    outline = Color(0xFFB7C1BE),
)

@Composable
fun MatterHomeTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = MatterColors,
        typography = Typography(),
        content = content,
    )
}
