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
    val Background      = Color(0xFF0A0A0A)
    val Surface         = Color(0xFF111111)
    val SurfaceElevated = Color(0xFF1A1A1A)
    val BorderSubtle    = Color(0xFF222222)
    val AccentAmber     = Color(0xFFF5A623)
    val AccentDanger    = Color(0xFFE8445A)
    val AccentSuccess   = Color(0xFF2ECC71)
    val TextPrimary     = Color(0xFFF0F0F0)
    val TextSecondary   = Color(0xFF666666)
    val TextMuted       = Color(0xFF333333)
    val BadgeBlockedBg  = Color(0xFF2A1F00)
    val BadgeLockedBg   = Color(0xFF2A0A0A)
    val HardcoreBg      = Color(0xFF0D0505)
    val HardcoreCardBg  = Color(0xFF1A0A0A)
}

private val BastionDarkColors = darkColorScheme(
    primary             = BastionColors.AccentAmber,
    onPrimary           = Color.Black,
    primaryContainer    = BastionColors.SurfaceElevated,
    onPrimaryContainer  = BastionColors.TextPrimary,
    secondary           = BastionColors.AccentSuccess,
    onSecondary         = Color.Black,
    secondaryContainer  = Color(0xFF0A2010),
    onSecondaryContainer= BastionColors.AccentSuccess,
    tertiary            = BastionColors.AccentDanger,
    onTertiary          = Color.White,
    tertiaryContainer   = BastionColors.HardcoreCardBg,
    onTertiaryContainer = BastionColors.AccentDanger,
    background          = BastionColors.Background,
    onBackground        = BastionColors.TextPrimary,
    surface             = BastionColors.Surface,
    onSurface           = BastionColors.TextPrimary,
    surfaceVariant      = BastionColors.SurfaceElevated,
    onSurfaceVariant    = BastionColors.TextSecondary,
    outline             = BastionColors.BorderSubtle,
    error               = BastionColors.AccentDanger,
    onError             = Color.White
)

private val BastionTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Bold,
        fontSize = 56.sp,
        lineHeight = 64.sp
    ),
    displayMedium = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Bold,
        fontSize = 32.sp,
        lineHeight = 40.sp
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp,
        lineHeight = 32.sp
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
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
        lineHeight = 20.sp
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 11.sp,
        lineHeight = 16.sp
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp
    )
)

private val BastionShapes = Shapes(
    small  = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large  = RoundedCornerShape(16.dp)
)

@Composable
fun BastionTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = BastionDarkColors,
        typography  = BastionTypography,
        shapes      = BastionShapes,
        content     = content
    )
}
