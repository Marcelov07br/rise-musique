package com.rise.musique.ui

import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import com.rise.musique.ui.glass.BarHighlightAlpha
import com.rise.musique.ui.glass.backdrop.backdrops.LayerBackdrop
import com.rise.musique.ui.glass.backdrop.backdrops.layerBackdrop
import com.rise.musique.ui.glass.squareLiquidGlass

/**
 * 0.28: o vidro agora é o do Square (github.com/Lelonio/Square, GPL-3.0) — `ui/glass/SquareGlass.kt` sobre a cópia da
 * biblioteca backdrop (Kyant0, Apache-2.0) que o Square embute: captura do conteúdo numa camada, saturação, desfoque
 * (RenderEffect, API 31+), lente de refração (RuntimeShader/AGSL, API 33+), aro especular e sombra. O Haze de 0.20–0.27 saiu.
 * Sem RenderEffect (API < 31) ou sem captura (pré-visualização) → filme translúcido + aro (fallback do próprio Square).
 */
/** O conteúdo que o vidro amostra (a camada é gravada aqui). */
fun Modifier.glassSource(backdrop: com.rise.musique.ui.glass.backdrop.Backdrop?) =
  if (backdrop is LayerBackdrop) layerBackdrop(backdrop) else this

/** Medidas que sobraram fora do vidro (sombra suave do Square: `softShadow` 20 dp, spot .26 no mini). */
object GlassLook {
  val Shadow = 20.dp
  const val ShadowAlpha = .26f
  const val AmbientAlpha = .10f
}

@Composable fun Modifier.liquidGlass(shape: Shape, dark: Boolean, highlightAlpha: Float = BarHighlightAlpha): Modifier =
  squareLiquidGlass(dark, (shape as? CornerBasedShape) ?: RoundedCornerShape(50), highlightAlpha = highlightAlpha)

/** Filme do vidro sem captura (para quem desenha o próprio fundo). */
fun glassFallbackFilm(dark: Boolean): Color = com.rise.musique.ui.glass.glassFilm(dark, 0f).copy(alpha = .5f)
