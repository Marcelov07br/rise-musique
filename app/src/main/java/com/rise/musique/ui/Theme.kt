package com.rise.musique.ui
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
private val Dark = darkColorScheme(primary = Color(0xFFE6E1D8), onPrimary = Color(0xFF111111),
  background = Color.Black, surface = Color(0xFF0D0D0D), surfaceVariant = Color(0xFF181818),
  onSurface = Color(0xFFEDEDED), onSurfaceVariant = Color(0xFF8E8E8E), outline = Color(0xFF262626))
private val Light = lightColorScheme(primary = Color(0xFF1A1A1A), onPrimary = Color(0xFFFAFAFA),
  background = Color(0xFFF7F6F3), surface = Color.White, surfaceVariant = Color(0xFFEDECE8),
  onSurface = Color(0xFF151515), onSurfaceVariant = Color(0xFF6E6E6E), outline = Color(0xFFDFDED9))
@Composable fun RiseTheme(dark: Boolean, content: @Composable () -> Unit) =
  MaterialTheme(colorScheme = if (dark) Dark else Light, content = content)
