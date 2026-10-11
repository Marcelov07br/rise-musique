package com.rise.musique.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource

/** Backdrop the floating glass blurs (main content); null in isolated previews → tinted fallback. */
val LocalHaze = staticCompositionLocalOf<HazeState?> { null }

fun Modifier.glassSource(state: HazeState?) = if (state != null) hazeSource(state) else this

/**
 * Liquid glass: real backdrop blur of what's behind (Haze; RenderEffect on API 31+, tinted scrim below),
 * light tint + grain like iOS, then the "lens" edges: specular rim brighter on top, soft inner shadow at the
 * bottom and a faint refraction band hugging the border.
 */
@Composable fun Modifier.liquidGlass(shape: Shape, dark: Boolean): Modifier {
  val haze = LocalHaze.current
  val tint = if (dark) Color(0xFF1C1C20).copy(alpha = .42f) else Color.White.copy(alpha = .5f)
  val base = clip(shape)
  val blurred = if (haze != null) base.hazeEffect(haze, HazeStyle(backgroundColor = if (dark) Color.Black else Color.White, tints = listOf(HazeTint(tint)),
    blurRadius = 26.dp, noiseFactor = .09f, fallbackTint = HazeTint((if (dark) Color(0xFF1C1C20) else Color.White).copy(alpha = .9f))))
    else base.drawWithContent { drawRect(if (dark) Color(0xFF1C1C20).copy(alpha = .9f) else Color.White.copy(alpha = .9f)); drawContent() }
  return blurred.drawWithContent {
    drawContent()
    val o = shape.createOutline(size, layoutDirection, this)
    // Banda de refração junto à borda (lente) + brilho especular em cima.
    drawOutline(o, Brush.verticalGradient(0f to Color.White.copy(if (dark) .10f else .35f), .35f to Color.Transparent, .8f to Color.Transparent,
      1f to Color.Black.copy(if (dark) .22f else .08f)))
    drawOutline(o, Brush.radialGradient(listOf(Color.White.copy(if (dark) .09f else .2f), Color.Transparent), center = Offset(size.width * .3f, 0f), radius = size.width * .45f))
    drawOutline(o, Brush.verticalGradient(listOf(Color.White.copy(if (dark) .38f else .9f), Color.White.copy(.06f), Color.White.copy(if (dark) .16f else .4f))),
      style = Stroke(1.dp.toPx()))
  }
}
