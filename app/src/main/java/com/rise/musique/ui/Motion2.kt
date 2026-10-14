package com.rise.musique.ui

import android.content.Context
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.animation.core.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext

/**
 * Animated covers. Real "motion artwork" (Apple Music) is only served through the authenticated MusicKit API and
 * YouTube Music has no equivalent, so there is no legitimate public source: this is the generated fallback —
 * a slow Ken Burns drift (scale 1.0→1.08 with a gentle pan, 24 s loop) of the static cover.
 * Off with battery saver, with system animations disabled ("remover animações"), in static previews or when [enabled] is false.
 */
object CoverMotion {
  fun allowed(ctx: Context): Boolean {
    val scale = runCatching { Settings.Global.getFloat(ctx.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) }.getOrDefault(1f)
    val saver = (ctx.getSystemService(Context.POWER_SERVICE) as? PowerManager)?.isPowerSaveMode == true
    return scale > 0f && !saver && !StaticPreview.on && !ReduceMotion.user
  }
  /** Pure path of the drift for a phase 0..1 (visible for tests): scale, x, y (fractions of the size). */
  fun at(t: Float): Triple<Float, Float, Float> {
    val e = (1 - kotlin.math.cos(t * 2 * Math.PI).toFloat()) / 2 // 0→1→0, smooth at the loop point
    return Triple(1f + .08f * e, .025f * kotlin.math.sin(t * 2 * Math.PI).toFloat(), -.02f * e)
  }
}

@Composable fun Modifier.kenBurns(enabled: Boolean): Modifier {
  val ctx = LocalContext.current
  val on = enabled && remember { CoverMotion.allowed(ctx) }
  if (!on) return this
  val t by rememberInfiniteTransition(label = "kenburns").animateFloat(0f, 1f, infiniteRepeatable(tween(24_000, easing = LinearEasing)), label = "kb")
  return graphicsLayer { val (s, x, y) = CoverMotion.at(t); scaleX = s; scaleY = s; translationX = x * size.width; translationY = y * size.height }
}
