package com.dailythread.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val DtBackground = Color(0xFFF6F8FC)
val DtSurface = Color(0xFFFFFFFF)
val DtSurface2 = Color(0xFFF0F5FF)
val DtPrimary = Color(0xFF2563EB)
val DtPrimaryDeep = Color(0xFF102A56)
val DtPrimarySoft = Color(0xFFEAF1FF)
val DtText = Color(0xFF14213A)
val DtMuted = Color(0xFF73809A)
val DtLine = Color(0xFFE5EAF2)
val DtDanger = Color(0xFFB4233B)
val DtSuccess = Color(0xFF15803D)
val DtWarning = Color(0xFF95620F)

private val Scheme = lightColorScheme(
    primary = DtPrimary,
    onPrimary = Color.White,
    primaryContainer = DtPrimarySoft,
    onPrimaryContainer = DtPrimaryDeep,
    secondary = DtPrimaryDeep,
    onSecondary = Color.White,
    background = DtBackground,
    onBackground = DtText,
    surface = DtSurface,
    onSurface = DtText,
    surfaceVariant = DtSurface2,
    onSurfaceVariant = DtMuted,
    outline = DtLine,
    error = DtDanger
)

private val DtTypography = Typography(
    displaySmall = TextStyle(
        fontSize = 42.sp,
        lineHeight = 44.sp,
        fontWeight = FontWeight.ExtraBold,
        letterSpacing = (-1.2f).sp,
        color = DtText
    ),
    headlineLarge = TextStyle(
        fontSize = 32.sp,
        lineHeight = 35.sp,
        fontWeight = FontWeight.ExtraBold,
        letterSpacing = (-0.9f).sp,
        color = DtText
    ),
    headlineMedium = TextStyle(
        fontSize = 28.sp,
        lineHeight = 31.sp,
        fontWeight = FontWeight.ExtraBold,
        letterSpacing = (-0.7f).sp,
        color = DtText
    ),
    headlineSmall = TextStyle(
        fontSize = 23.sp,
        lineHeight = 27.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = (-0.4f).sp,
        color = DtText
    ),
    titleLarge = TextStyle(
        fontSize = 18.sp,
        lineHeight = 23.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = (-0.25f).sp,
        color = DtText
    ),
    titleMedium = TextStyle(
        fontSize = 15.sp,
        lineHeight = 20.sp,
        fontWeight = FontWeight.Bold,
        color = DtText
    ),
    bodyLarge = TextStyle(fontSize = 15.sp, lineHeight = 23.sp, color = DtText),
    bodyMedium = TextStyle(fontSize = 13.sp, lineHeight = 20.sp, color = DtText),
    bodySmall = TextStyle(fontSize = 11.sp, lineHeight = 17.sp, color = DtMuted),
    labelLarge = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Bold),
    labelMedium = TextStyle(fontSize = 11.sp, lineHeight = 15.sp, fontWeight = FontWeight.Bold),
    labelSmall = TextStyle(fontSize = 10.sp, lineHeight = 14.sp, fontWeight = FontWeight.SemiBold)
)

private val DtShapes = androidx.compose.material3.Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun DailyThreadTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = Scheme,
        typography = DtTypography,
        shapes = DtShapes,
        content = content
    )
}
