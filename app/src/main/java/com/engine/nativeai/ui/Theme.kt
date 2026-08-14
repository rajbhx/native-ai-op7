package com.engine.nativeai.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** OxygenOS "NEVER SETTLE" design tokens (blueprint Phase 6). */
val OpRed = Color(0xFFEB0029)
val OpRedDark = Color(0xFF8F0019)
val OpBg = Color(0xFF121212)
val OpCard = Color(0xFF1E1E1E)
val OpText = Color(0xFFFFFFFF)
val OpTextSecondary = Color(0xFFA0A0A0)
val OpDivider = Color(0xFF2A2A2A)
val OpBorder = Color(0xFF3A3A3A)
val OpBlue = Color(0xFF008BFF)
val OpAmber = Color(0xFFF5A623)
val OpSuccess = Color(0xFF2ECC71)

private val OpColors = darkColorScheme(
    primary = OpRed,
    onPrimary = Color.White,
    secondary = OpBlue,
    onSecondary = Color.White,
    background = OpBg,
    onBackground = OpText,
    surface = OpCard,
    onSurface = OpText,
    surfaceVariant = OpCard,
    onSurfaceVariant = OpTextSecondary,
    outline = OpDivider,
)

@Composable
fun OxygenOSTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = OpColors, content = content)
}
