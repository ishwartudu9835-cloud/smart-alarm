package com.ishwar.smartalarm.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.ishwar.smartalarm.data.Prefs
import com.ishwar.smartalarm.data.TaskStatus

// App icon se liye gaye rang
val Navy = Color(0xFF071A4A)
val NavyDeep = Color(0xFF040F2E)
val Blue = Color(0xFF2F7BFF)
val BlueLight = Color(0xFF8FB8FF)
val Cyan = Color(0xFF4FC3F7)
val Orange = Color(0xFFFF9F1C)

val StatusGreen = Color(0xFF2EB872)
val StatusOrange = Color(0xFFFF9F1C)
val StatusRed = Color(0xFFE5484D)
val StatusBlue = Color(0xFF3D8BFF)

private val DarkColors = darkColorScheme(
    primary = BlueLight,
    onPrimary = NavyDeep,
    primaryContainer = Color(0xFF16357F),
    onPrimaryContainer = Color(0xFFDCE6FF),
    secondary = Cyan,
    onSecondary = NavyDeep,
    secondaryContainer = Color(0xFF123A5C),
    onSecondaryContainer = Color(0xFFCDEBFF),
    tertiary = Orange,
    background = NavyDeep,
    onBackground = Color(0xFFE6ECFF),
    surface = Color(0xFF0A1838),
    onSurface = Color(0xFFE6ECFF),
    surfaceVariant = Color(0xFF15264F),
    onSurfaceVariant = Color(0xFFB9C6E8),
    surfaceContainer = Color(0xFF0E1E45),
    surfaceContainerHigh = Color(0xFF13244F),
    surfaceContainerHighest = Color(0xFF182B5A),
    surfaceContainerLow = Color(0xFF0B1A3D),
    outline = Color(0xFF5A6A94)
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF1F5EEB),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDCE6FF),
    onPrimaryContainer = Navy,
    secondary = Color(0xFF0B87C4),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD3EEFF),
    onSecondaryContainer = Color(0xFF00344F),
    tertiary = Color(0xFFE07E00),
    background = Color(0xFFF5F7FD),
    onBackground = Color(0xFF0D1730),
    surface = Color(0xFFF5F7FD),
    onSurface = Color(0xFF0D1730),
    surfaceVariant = Color(0xFFE2E8F7),
    onSurfaceVariant = Color(0xFF45506B),
    surfaceContainer = Color(0xFFECF0FA),
    surfaceContainerHigh = Color(0xFFE6EBF8),
    surfaceContainerHighest = Color(0xFFDFE5F5),
    surfaceContainerLow = Color(0xFFF1F4FC),
    outline = Color(0xFF7A859F)
)

@Composable
fun isDark(): Boolean = when (Prefs.theme) {
    Prefs.THEME_LIGHT -> false
    Prefs.THEME_DARK -> true
    else -> isSystemInDarkTheme()
}

@Composable
fun SmartTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isDark()) DarkColors else LightColors, content = content)
}

fun statusColor(s: TaskStatus): Color = when (s) {
    TaskStatus.COMPLETED -> StatusGreen
    TaskStatus.PENDING -> StatusOrange
    TaskStatus.MISSED -> StatusRed
    TaskStatus.UPCOMING -> StatusBlue
}
