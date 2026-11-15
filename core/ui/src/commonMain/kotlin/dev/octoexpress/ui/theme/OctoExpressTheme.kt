package dev.octoexpress.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.materialkolor.PaletteStyle
import com.materialkolor.dynamiccolor.ColorSpec
import com.materialkolor.rememberDynamicColorScheme

data class SeedPreset(
    val name: String,
    val colorHex: Long
)

val OctoSeedPresets = listOf(
    SeedPreset("Octo Violet", 0xFF6E40C9),
    SeedPreset("Terminal Emerald", 0xFF0E9F6E),
    SeedPreset("Commit Cobalt", 0xFF2563EB),
    SeedPreset("Diff Amber", 0xFFD97706),
    SeedPreset("Rose Quartz", 0xFFE11D48)
)

@Composable
expect fun platformDynamicColorScheme(darkTheme: Boolean): ColorScheme?

val OctoExpressShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(36.dp)
)

val OctoExpressTypography = Typography(
    displaySmall = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 32.sp,
        lineHeight = 38.sp,
        letterSpacing = (-0.5).sp
    ),
    headlineMedium = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 26.sp,
        lineHeight = 32.sp,
        letterSpacing = (-0.25).sp
    ),
    headlineSmall = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 28.sp
    ),
    titleLarge = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 19.sp,
        lineHeight = 25.sp
    ),
    titleMedium = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 22.sp
    ),
    bodyLarge = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 22.sp
    ),
    bodyMedium = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp
    ),
    labelLarge = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 13.sp,
        lineHeight = 18.sp
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 14.sp
    )
)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun OctoExpressTheme(
    seedColorHex: Long = OctoSeedPresets.first().colorHex,
    useDynamicColor: Boolean = true,
    darkThemeOverride: Boolean? = null,
    content: @Composable () -> Unit
) {
    val darkTheme = darkThemeOverride ?: isSystemInDarkTheme()
    val dynamicScheme = if (useDynamicColor) platformDynamicColorScheme(darkTheme) else null

    val colorScheme = dynamicScheme ?: rememberDynamicColorScheme(
        seedColor = Color(seedColorHex),
        isDark = darkTheme,
        style = PaletteStyle.Expressive,
        specVersion = ColorSpec.SpecVersion.SPEC_2025
    )

    MaterialExpressiveTheme(
        colorScheme = colorScheme,
        motionScheme = MotionScheme.expressive(),
        shapes = OctoExpressShapes,
        typography = OctoExpressTypography,
        content = content
    )
}
