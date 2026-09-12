package com.inkora.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.inkora.app.InkoraConfig

/** Centralized brand values. Changing the product name happens in one place. */
object InkoraBrand {
    val productName: String get() = InkoraConfig.productName
    val versionName: String get() = InkoraConfig.versionName
}

/** Semantic colors used by custom surfaces in addition to Material color roles. */
object InkoraColors {
    val primaryBlue = Color(0xFF315F63)
    val primaryDark = Color(0xFF24484B)
    val primarySoft = Color(0xFFE6EFEC)
    val appBackground = Color(0xFFF8F9F7)
    val paper = Color(0xFFFFFDF8)
    val toolbar = Color(0xFFF8F9FA)
    val border = Color(0xFFE2E6EA)
    val ink = Color(0xFF242B2B)
    val mutedInk = Color(0xFF5C6868)
    val success = Color(0xFF217A4B)
    val warmAccent = Color(0xFFC98936)
}

private val LightInkoraScheme = lightColorScheme(
    primary = InkoraColors.primaryBlue,
    onPrimary = Color.White,
    primaryContainer = InkoraColors.primarySoft,
    onPrimaryContainer = InkoraColors.primaryDark,
    secondary = Color(0xFF536779),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFDDEAF5),
    onSecondaryContainer = Color(0xFF1B2B3A),
    tertiary = InkoraColors.warmAccent,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFEFD7),
    onTertiaryContainer = Color(0xFF3A2813),
    background = InkoraColors.appBackground,
    onBackground = InkoraColors.ink,
    surface = Color.White,
    onSurface = InkoraColors.ink,
    surfaceVariant = Color(0xFFF0F3F0),
    onSurfaceVariant = InkoraColors.mutedInk,
    outline = Color(0xFF879693),
    outlineVariant = Color(0xFFDDE5E1),
    error = Color(0xFFB3261E),
    errorContainer = Color(0xFFF9DEDC),
    onErrorContainer = Color(0xFF410E0B)
)

private val DarkInkoraScheme = darkColorScheme(
    primary = Color(0xFF9BCBFA),
    onPrimary = Color(0xFF003256),
    primaryContainer = Color(0xFF0E4E7D),
    onPrimaryContainer = Color(0xFFD3E9FF),
    secondary = Color(0xFFB7C9D9),
    onSecondary = Color(0xFF21333F),
    secondaryContainer = Color(0xFF384C5A),
    onSecondaryContainer = Color(0xFFD8EAF8),
    tertiary = Color(0xFFFFC978),
    onTertiary = Color(0xFF452B00),
    tertiaryContainer = Color(0xFF634516),
    onTertiaryContainer = Color(0xFFFFDDAA),
    background = Color(0xFF111416),
    onBackground = Color(0xFFE7E9EB),
    surface = Color(0xFF171A1D),
    onSurface = Color(0xFFE7E9EB),
    surfaceVariant = Color(0xFF242A2F),
    onSurfaceVariant = Color(0xFFB8C1C8),
    outline = Color(0xFF475159),
    outlineVariant = Color(0xFF30373D),
    error = Color(0xFFFFB4AB),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6)
)

private val InkoraTypography = Typography(
    displaySmall = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 32.sp, lineHeight = 40.sp),
    headlineSmall = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 23.sp, lineHeight = 30.sp),
    titleLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 21.sp, lineHeight = 28.sp),
    titleMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 22.sp),
    titleSmall = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp),
    bodyLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 12.sp, lineHeight = 17.sp),
    labelLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp),
    labelMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp),
    labelSmall = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp)
)

@Composable
fun InkoraTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkInkoraScheme else LightInkoraScheme,
        typography = InkoraTypography,
        content = content
    )
}
