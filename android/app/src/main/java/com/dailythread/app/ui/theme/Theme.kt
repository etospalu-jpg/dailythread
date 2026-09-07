package com.dailythread.app.ui.theme

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Scheme = lightColorScheme(
    primary = Color(0xFF2563EB),
    secondary = Color(0xFF102A56),
    background = Color(0xFFF6F8FC),
    surface = Color.White,
    onBackground = Color(0xFF14213A)
)

@Composable
fun DailyThreadTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Scheme, typography = Typography(), content = content)
}
