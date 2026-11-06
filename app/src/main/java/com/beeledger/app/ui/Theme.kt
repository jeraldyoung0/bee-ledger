package com.beeledger.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val BeeScheme = lightColorScheme(
    primary = Color(0xFFF2A516),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFFF1D6),
    onPrimaryContainer = Color(0xFF4A3200),
    secondary = Color(0xFF6B8E23),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE8F0D8),
    onSecondaryContainer = Color(0xFF2E3B10),
    tertiary = Color(0xFF2E7D32),
    onTertiary = Color(0xFFFFFFFF),
    background = Color(0xFFFDFAF3),
    onBackground = Color(0xFF1F1B12),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1F1B12),
    surfaceVariant = Color(0xFFF3EDE0),
    onSurfaceVariant = Color(0xFF6B6357),
    outline = Color(0xFFD8D0BE),
    error = Color(0xFFD32F2F),
    onError = Color(0xFFFFFFFF)
)

@Composable
fun BeeTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = BeeScheme, content = content)
}
