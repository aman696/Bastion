package com.aman.bastion.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

object BastionColors {
    val Background = Color(0xFF0E1210)
    val Surface = Color(0xFF141A16)
    val SurfaceElevated = Color(0xFF1C241E)
    val BorderSubtle = Color(0xFF2E3931)
    val AccentAmber = Color(0xFFE4B14A)
    val AccentDanger = Color(0xFFD8644B)
    val AccentSuccess = Color(0xFF7CAF78)
    val TextPrimary = Color(0xFFF5F2E9)
    val TextSecondary = Color(0xFFBAC3B7)
    val TextMuted = Color(0xFF7E897F)
    val BadgeBlockedBg = Color(0xFF322814)
    val BadgeLockedBg = Color(0xFF371A15)
    val HardcoreBg = Color(0xFF140D0B)
    val HardcoreCardBg = Color(0xFF211311)
}

private val BastionDarkColors = darkColorScheme(
    primary = BastionColors.AccentAmber,
    onPrimary = Color(0xFF18130A),
    primaryContainer = Color(0xFF2E2617),
    onPrimaryContainer = BastionColors.TextPrimary,
    secondary = BastionColors.AccentSuccess,
    onSecondary = Color(0xFF0E170E),
    secondaryContainer = Color(0xFF172419),
    onSecondaryContainer = BastionColors.TextPrimary,
    tertiary = BastionColors.AccentDanger,
    onTertiary = Color.White,
    tertiaryContainer = BastionColors.HardcoreCardBg,
    onTertiaryContainer = BastionColors.TextPrimary,
    background = BastionColors.Background,
    onBackground = BastionColors.TextPrimary,
    surface = BastionColors.Surface,
    onSurface = BastionColors.TextPrimary,
    surfaceVariant = BastionColors.SurfaceElevated,
    onSurfaceVariant = BastionColors.TextSecondary,
    outline = BastionColors.BorderSubtle,
    error = BastionColors.AccentDanger,
    onError = Color.White,
    errorContainer = BastionColors.BadgeLockedBg,
    onErrorContainer = BastionColors.TextPrimary
)

private val BastionTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.Bold,
        fontSize = 52.sp,
        lineHeight = 58.sp,
        letterSpacing = (-1.2).sp
    ),
    displayMedium = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.Bold,
        fontSize = 34.sp,
        lineHeight = 40.sp,
        letterSpacing = (-0.4).sp
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.Bold,
        fontSize = 26.sp,
        lineHeight = 32.sp
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
        lineHeight = 24.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 21.sp
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 18.sp
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = 1.2.sp
    )
)

private val BastionShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(28.dp)
)

@Composable
fun BastionTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = BastionDarkColors,
        typography = BastionTypography,
        shapes = BastionShapes,
        content = content
    )
}
