package io.github.sebasgl1.mrwhite

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

val Ink = Color(0xFF101114)
val Gold = Color(0xFFF2C230)
val CivilColor = Color(0xFF4FD1A0)
val UndercoverColor = Color(0xFFFF6B57)
val WhiteColor = Color(0xFFF5F5F0)

private val Scheme = darkColorScheme(
    primary = Color(0xFFF5F5F0), onPrimary = Ink,
    secondaryContainer = Color(0xFF3A3420), onSecondaryContainer = Gold,
    background = Ink, onBackground = Color(0xFFEDEDE8),
    surface = Color(0xFF1B1D22), onSurface = Color(0xFFEDEDE8),
    surfaceVariant = Color(0xFF2A2D34), onSurfaceVariant = Color(0xFF9A9FAB),
    error = UndercoverColor, outline = Color(0xFF3E424B),
)

@Composable
fun MrWhiteTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Scheme) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background, content = content)
    }
}
