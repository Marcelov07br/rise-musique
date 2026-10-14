package com.rise.musique.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.drawscope.translate
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
 * 0.27: parâmetros do vidro, afinados olhando capturas de referência (vidro claro e leitoso, aro de luz contínuo,
 * borda que "acende" por refração, sombra curta). Recriação própria: nenhum código de terceiros foi reaproveitado.
 */
object GlassLook {
  val Blur = 34.dp
  /** Véu: escuro mais neutro/translúcido que em 0.24 (.55) para o desfoque aparecer; claro = branco leitoso. */
  val TintDark = Color(0xFF3C3C42).copy(alpha = .48f)
  val TintLight = Color.White.copy(alpha = .58f)
  const val Noise = .06f
  /** Faixa de refração junto à borda (espessura) e aro. */
  val Edge = 7.dp
  val Rim = 1.2.dp
  val Shadow = 10.dp
  const val ShadowAlpha = .16f
}

/**
 * Liquid glass: real backdrop blur of what's behind (Haze; RenderEffect on API 31+, tinted scrim below), milky tint +
 * light grain, then the lens: a refraction band that brightens along the whole border (strongest at the top), a
 * specular glow that follows the phone tilt, a soft inner shade at the bottom and a continuous light rim.
 */
@Composable fun Modifier.liquidGlass(shape: Shape, dark: Boolean): Modifier {
  val haze = LocalHaze.current
  val tint = if (dark) GlassLook.TintDark else GlassLook.TintLight
  val base = clip(shape)
  val blurred = if (haze != null) base.hazeEffect(haze, HazeStyle(backgroundColor = if (dark) Color.Black else Color.White, tints = listOf(HazeTint(tint)),
    blurRadius = GlassLook.Blur, noiseFactor = GlassLook.Noise, fallbackTint = HazeTint((if (dark) Color(0xFF26262A) else Color.White).copy(alpha = .9f))))
    else base.drawWithContent { drawRect(if (dark) Color(0xFF26262A).copy(alpha = .9f) else Color.White.copy(alpha = .9f)); drawContent() }
  val tilt = LocalTilt.current
  // Tudo é criado em drawWithCache (só muda com o tamanho); por quadro só lemos a inclinação e transladamos o brilho.
  return blurred.drawWithCache {
    val o = shape.createOutline(size, layoutDirection, this)
    val shade = Brush.verticalGradient(0f to Color.White.copy(if (dark) .07f else .22f), .4f to Color.Transparent, .75f to Color.Transparent,
      1f to Color.Black.copy(if (dark) .16f else .05f))
    val r = size.width * .45f
    val glow = Brush.radialGradient(listOf(Color.White.copy(if (dark) .08f else .18f), Color.Transparent), center = Offset(size.width * .3f, 0f), radius = r)
    // Refração: traço largo e suave por dentro da borda (metade cai fora e é recortada), mais claro em cima e embaixo.
    val edgePx = GlassLook.Edge.toPx()
    val refr = Brush.verticalGradient(listOf(Color.White.copy(if (dark) .16f else .45f), Color.White.copy(if (dark) .03f else .1f),
      Color.White.copy(if (dark) .10f else .3f)))
    val rim = Brush.verticalGradient(listOf(Color.White.copy(if (dark) .55f else .95f), Color.White.copy(if (dark) .14f else .35f),
      Color.White.copy(if (dark) .30f else .7f)))
    val wide = Stroke(edgePx * 2f); val inner = Stroke(edgePx * .9f); val stroke = Stroke(GlassLook.Rim.toPx())
    onDrawWithContent {
      drawOutline(o, shade)
      drawOutline(o, refr, style = wide, alpha = .55f)
      drawOutline(o, refr, style = inner, alpha = .6f)
      val dx = (tilt?.x?.value ?: 0f) * size.width * .25f; val dy = (tilt?.y?.value ?: 0f) * size.height * .5f
      if (dx == 0f && dy == 0f) drawOutline(o, glow) else translate(dx, dy) { drawCircle(glow, r, Offset(size.width * .3f, 0f)) }
      drawOutline(o, rim, style = stroke)
      drawContent()
    }
  }
}
