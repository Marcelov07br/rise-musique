package com.rise.musique.ui
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Explicitly set every role used by Material controls, including tonal surfaces.
private fun neutral(dark: Boolean): ColorScheme {
  val bg = if (dark) Color.Black else Color(0xFFFAFAFA)
  val fg = if (dark) Color(0xFFF5F5F5) else Color(0xFF151515)
  val surface = if (dark) Color(0xFF0D0D0F) else Color.White
  val raised = if (dark) Color(0xFF19191C) else Color(0xFFEFEFF1)
  val quiet = if (dark) Color(0xFFA0A0A6) else Color(0xFF616168)
  val edge = if (dark) Color(0xFF353539) else Color(0xFFD5D5DA)
  val scheme = if (dark) darkColorScheme() else lightColorScheme()
  return scheme.copy(primary = fg, onPrimary = bg, primaryContainer = raised, onPrimaryContainer = fg,
    inversePrimary = bg, secondary = fg, onSecondary = bg, secondaryContainer = raised, onSecondaryContainer = fg,
    tertiary = fg, onTertiary = bg, tertiaryContainer = raised, onTertiaryContainer = fg,
    background = bg, onBackground = fg, surface = surface, onSurface = fg, surfaceVariant = raised, onSurfaceVariant = quiet,
    surfaceTint = Color.Transparent, inverseSurface = fg, inverseOnSurface = bg,
    error = fg, onError = bg, errorContainer = raised, onErrorContainer = fg, outline = edge, outlineVariant = edge.copy(alpha = .5f),
    scrim = Color.Black, surfaceDim = surface, surfaceBright = raised, surfaceContainerLowest = bg,
    surfaceContainerLow = surface, surfaceContainer = surface, surfaceContainerHigh = raised, surfaceContainerHighest = raised)
}
private val Type = Typography(
  headlineLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 36.sp, lineHeight = 42.sp, letterSpacing = (-1).sp),
  titleLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 24.sp, lineHeight = 30.sp, letterSpacing = (-.5f).sp),
  titleMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, lineHeight = 23.sp),
  bodyLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 16.sp, lineHeight = 23.sp),
  bodyMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 14.sp, lineHeight = 20.sp),
  labelLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp))
@Composable fun RiseTheme(dark: Boolean, content: @Composable () -> Unit) =
  MaterialTheme(colorScheme = neutral(dark), typography = Type) {
    CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onBackground) { content() }
  }
