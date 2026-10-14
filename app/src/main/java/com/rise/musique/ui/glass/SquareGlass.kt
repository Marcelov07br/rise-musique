/*
 * Rise Musique 0.28.0 — portado de Lelonio/Square (GPL-3.0),
 * app/src/main/java/dev/lelonio/square/ui/glass/GlassEffect.kt (Modifier.liquidGlass, GlassEffectConfig,
 * glassSaturation, glassResolutionScale, LENS_MAX_DP/LENS_MAX_SHARE, fallback translúcido) e
 * ui/player/GlassSurface.kt (BarHighlightAlpha). https://github.com/Lelonio/Square
 * Cabeçalho original: "Convx Project (C) 2026 — Licensed under GPL-3.0 | See git history for contributors".
 *
 * Modificado para o Rise:
 *  - sem DataStore/ajustes do Square: a configuração é a padrão do Square (vibrancy 1.2, blur 2 dp, lente .4/.6 de 48 dp,
 *    aro .8 dp branco, filme .5) e não há per-componente/estilos BLUR/TRANSPARENT nem matiz do usuário;
 *  - o "filme" escolhe claro/escuro pelo parâmetro `dark` do Rise (sem LocalInkOverride/LocalLightTheme do Square);
 *    no escuro continua seguindo a luminância da capa (LocalBackdropLuminance), como no Square;
 *  - backdrop nulo (pré-visualizações/testes) ou API < 31 → caminho translúcido do próprio Square (filme + aro);
 *    API 31–32 → desfoque sem lente (a lente exige RuntimeShader, API 33+);
 *  - removidos loopBucket/heldWhileMoving/onDrawTint (o Rise não usa); `frozen` mantido.
 */
package com.rise.musique.ui.glass

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.rise.musique.ui.glass.backdrop.Backdrop
import com.rise.musique.ui.glass.backdrop.BackdropEffectScope
import com.rise.musique.ui.glass.backdrop.drawBackdrop
import com.rise.musique.ui.glass.backdrop.effects.blur
import com.rise.musique.ui.glass.backdrop.effects.colorControls
import com.rise.musique.ui.glass.backdrop.effects.lens
import com.rise.musique.ui.glass.backdrop.highlight.Highlight
import com.rise.musique.ui.glass.backdrop.highlight.HighlightStyle
import com.rise.musique.ui.glass.backdrop.isRenderEffectSupported
import com.rise.musique.ui.glass.backdrop.shadow.Shadow

/** Os valores padrão do Square (GlassEffectConfig). */
@Stable
data class GlassEffectConfig(
  val vibrancy: Float = 1.2f,
  val blurRadius: Float = 2f,
  val lensHeight: Float = 0.4f,
  val lensAmount: Float = 0.6f,
  val chromaticAberration: Boolean = false,
  val depthEffect: Boolean = false,
  val highlightOpacity: Float = EdgeHighlightAlpha,
  val surfaceOpacity: Float = 0.5f,
)

internal const val LENS_MAX_DP = 48f
private const val LENS_MAX_SHARE = 0.45f
private const val LENS_MIN_PANE_PX = 24f
internal const val MIN_GLASS_RESOLUTION_SCALE = 0.30f
internal const val FULL_QUALITY_BLUR_DP = 8f
private const val BRIGHT_FROM = 0.42f
private const val BRIGHT_TO = 0.72f
private val EdgeHighlightWidth = 0.8f.dp
internal const val EdgeHighlightAlpha = 0.55f
/** Aro da barra e do mini no Square (GlassSurface.BarHighlightAlpha). */
const val BarHighlightAlpha = 0.3f
private const val HighlightAngleFrozen = (25f + 65f) / 2f

fun glassResolutionScale(blurRadiusDp: Float): Float {
  val t = (blurRadiusDp / FULL_QUALITY_BLUR_DP).coerceIn(0f, 1f)
  return 1f - t * (1f - MIN_GLASS_RESOLUTION_SCALE)
}
fun glassSaturation(vibrancy: Float): Float = 1f + 0.5f * vibrancy.coerceIn(0f, 2f)
/** Square: sem RenderEffect (abaixo do Android 12) → filme translúcido + aro, nunca um bloco opaco. */
fun shouldUseTranslucentGlassFallback(renderEffectSupported: Boolean, hasBackdrop: Boolean): Boolean = !renderEffectSupported || !hasBackdrop

/** Filme do vidro (Square: claro #FAFAFA; escuro entre #4A4A4E e #23232A conforme o brilho da capa). */
fun glassFilm(dark: Boolean, backdropLuminance: Float): Color =
  if (!dark) Color(0xFFFAFAFA) else lerp(Color(0xFF4A4A4E), Color(0xFF23232A), ((backdropLuminance - BRIGHT_FROM) / (BRIGHT_TO - BRIGHT_FROM)).coerceIn(0f, 1f))

val LocalGlassEffectConfig = staticCompositionLocalOf { GlassEffectConfig() }
/** Brilho (0..1) do que está atrás do vidro; o Rise fornece a partir da capa tocando. */
val LocalBackdropLuminance = compositionLocalOf { 0f }
/** O conteúdo do app que o vidro amostra; null = sem captura (pré-visualização) → caminho translúcido. */
val LocalAppBackdrop = staticCompositionLocalOf<Backdrop?> { null }
val LocalGlassFrozen = staticCompositionLocalOf<() -> Boolean> { { false } }

@Composable
fun Modifier.squareLiquidGlass(
  dark: Boolean,
  shape: CornerBasedShape,
  config: GlassEffectConfig = LocalGlassEffectConfig.current,
  applyEdgeEffects: Boolean = true,
  blurRadiusDp: Float = config.blurRadius,
  highlightAlpha: Float = BarHighlightAlpha,
  backdropScale: Float = glassResolutionScale(blurRadiusDp),
  frozen: () -> Boolean = LocalGlassFrozen.current,
  ownBackdrop: Backdrop? = null,
  layerBlock: (GraphicsLayerScope.() -> Unit)? = null,
): Modifier {
  val backdrop = ownBackdrop ?: LocalAppBackdrop.current
  val density = LocalDensity.current
  val resolutionScale = backdropScale.coerceIn(0.05f, 1f)
  val blurPx = with(density) { blurRadiusDp.dp.toPx() } * resolutionScale
  val saturation = glassSaturation(config.vibrancy)
  val lensHeightPx = with(density) { (config.lensHeight * LENS_MAX_DP).dp.toPx() } * resolutionScale
  val lensAmountPx = with(density) { (config.lensAmount * LENS_MAX_DP).dp.toPx() } * resolutionScale
  val surfaceTintColor = glassFilm(dark, LocalBackdropLuminance.current)
  val rimAlpha = (highlightAlpha * (config.highlightOpacity / EdgeHighlightAlpha)).coerceIn(0f, 1f)

  if (backdrop == null || shouldUseTranslucentGlassFallback(isRenderEffectSupported(), true)) {
    return this.clip(shape)
      .background(surfaceTintColor.copy(alpha = config.surfaceOpacity.coerceIn(0f, 1f)))
      .border(EdgeHighlightWidth, Color.White.copy(alpha = rimAlpha), shape)
  }

  val shapeBlock: () -> Shape = remember(shape) { { shape } }
  val effectsBlock: BackdropEffectScope.() -> Unit = remember(saturation, blurPx, applyEdgeEffects, lensHeightPx, lensAmountPx, config.depthEffect, config.chromaticAberration) {
    {
      if (saturation != 1f) colorControls(saturation = saturation)
      if (blurPx > 0f) blur(blurPx)
      // Rise: tamanho ainda não medido no onAttach → sem lente neste quadro (o Square lia size direto e quebrava).
      if (applyEdgeEffects && size.isSpecified && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && (lensHeightPx > 0f || lensAmountPx > 0f) &&
        minOf(size.width, size.height) > LENS_MIN_PANE_PX) {
        val reach = minOf(size.width, size.height) * LENS_MAX_SHARE
        lens(refractionHeight = lensHeightPx.coerceAtMost(reach), refractionAmount = lensAmountPx.coerceAtMost(reach),
          depthEffect = config.depthEffect, chromaticAberration = config.chromaticAberration)
      }
    }
  }
  val highlightBlock: (() -> Highlight?)? = remember(applyEdgeEffects, rimAlpha) {
    if (applyEdgeEffects) ({ Highlight(width = EdgeHighlightWidth, style = HighlightStyle.Default(color = Color.White.copy(alpha = rimAlpha), angle = HighlightAngleFrozen)) }) else null
  }
  val shadowBlock: (() -> Shadow?)? = remember(applyEdgeEffects) { if (applyEdgeEffects) ({ Shadow.Default }) else null }
  val surfaceBlock: DrawScope.() -> Unit = remember(surfaceTintColor, config.surfaceOpacity) {
    { if (config.surfaceOpacity > 0f) drawRect(color = surfaceTintColor.copy(alpha = config.surfaceOpacity), size = size) }
  }
  return drawBackdrop(backdrop = backdrop, shape = shapeBlock, effects = effectsBlock, highlight = highlightBlock, shadow = shadowBlock,
    onDrawSurface = surfaceBlock, layerBlock = layerBlock, backdropScale = resolutionScale, frozen = frozen)
}

