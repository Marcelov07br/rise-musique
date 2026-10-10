package com.rise.musique.ui

import android.os.Build
import androidx.compose.animation.core.*
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import kotlin.math.cos
import kotlin.math.sin

/** Neutral fallback palette (no dynamic color anywhere). */
private val Neutral = listOf(Color(0xFF2A2A2E), Color(0xFF151517), Color(0xFF3A3A40), Color(0xFF0B0B0C))

/** Up to four colors taken from the cover with Palette, darkened enough for white text. */
@Composable fun rememberCoverPalette(url: String): List<Color> {
  val ctx = LocalContext.current
  var colors by remember(url) { mutableStateOf(Neutral) }
  LaunchedEffect(url) {
    if (url.isBlank() || StaticPreview.on) return@LaunchedEffect
    runCatching {
      val res = coil.ImageLoader(ctx).execute(ImageRequest.Builder(ctx).data(url).size(96).allowHardware(false).build())
      val bmp = (res.drawable as? android.graphics.drawable.BitmapDrawable)?.bitmap ?: return@runCatching
      val pal = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) { androidx.palette.graphics.Palette.from(bmp).maximumColorCount(16).generate() }
      val picks = listOfNotNull(pal.dominantSwatch, pal.vibrantSwatch, pal.darkVibrantSwatch, pal.mutedSwatch, pal.darkMutedSwatch, pal.lightMutedSwatch)
        .map { Color(it.rgb) }.distinct()
      if (picks.isNotEmpty()) colors = List(4) { i -> lerp(picks[i % picks.size], Color.Black, .35f + i * .08f) }
    }.onFailure { if (it is kotlinx.coroutines.CancellationException) throw it }
  }
  return colors
}

/**
 * Living artwork: four soft color blobs from the cover drift on slow, out-of-phase orbits and blend into a mesh-like
 * gradient. Pure Canvas drawing, so it works on every API level; frozen when [StaticPreview] is on.
 */
@Composable fun LivingArtwork(art: String, modifier: Modifier = Modifier, dim: Float = .25f) {
  val colors = rememberCoverPalette(art)
  val animated = colors.map { animateColorAsState(it, tween(1200), label = "mesh").value }
  val t = if (StaticPreview.on) 0.3f else rememberInfiniteTransition(label = "mesh")
    .animateFloat(0f, 1f, infiniteRepeatable(tween(36_000, easing = LinearEasing)), label = "t").value
  Canvas(modifier.fillMaxSize()) {
    drawRect(animated[3])
    val w = size.width; val h = size.height; val r = maxOf(w, h) * .75f
    val orbits = listOf(Triple(.25f, .2f, 0f), Triple(.8f, .3f, 1.7f), Triple(.3f, .8f, 3.4f), Triple(.75f, .85f, 5.1f))
    orbits.forEachIndexed { i, (x, y, phase) ->
      val a = (t * 2 * Math.PI + phase).toFloat()
      val c = Offset(w * (x + .18f * cos(a * (1 + i % 2))), h * (y + .12f * sin(a)))
      drawCircle(Brush.radialGradient(listOf(animated[i].copy(alpha = .9f), animated[i].copy(alpha = 0f)), c, r), r, c)
    }
    drawRect(Color.Black.copy(alpha = dim.coerceIn(0f, 1f)))
  }
}

/**
 * Liquid glass: translucent tint, optional frosted copy of what sits behind (blurred with RenderEffect on API 31+,
 * heavily dimmed instead on older versions) and a thin top highlight edge.
 */
@Composable fun GlassBox(modifier: Modifier, shape: Shape, backdrop: String? = null, dark: Boolean = true,
                         content: @Composable BoxScope.() -> Unit) {
  val tint = if (dark) Color.White.copy(alpha = .10f) else Color.White.copy(alpha = .55f)
  val edge = Brush.verticalGradient(listOf(Color.White.copy(alpha = if (dark) .35f else .9f), Color.White.copy(alpha = .04f)))
  Box(modifier.clip(shape)) {
    if (!backdrop.isNullOrBlank()) {
      if (Build.VERSION.SDK_INT >= 31) AsyncImage(backdrop, null, Modifier.matchParentSize().blur(40.dp).graphicsLayer { alpha = .55f }, contentScale = ContentScale.Crop)
      else AsyncImage(ImageRequest.Builder(LocalContext.current).data(backdrop).size(12).build(), null,
        Modifier.matchParentSize().graphicsLayer { alpha = .35f }, contentScale = ContentScale.Crop)
    }
    Box(Modifier.matchParentSize().background(tint).border(1.dp, edge, shape))
    content()
  }
}

/** Haptics wrapper: light tick for toggles/drags, stronger for play/pause and skips. */
class Haptics(private val h: androidx.compose.ui.hapticfeedback.HapticFeedback) {
  fun tick() = h.performHapticFeedback(HapticFeedbackType.TextHandleMove)
  fun press() = h.performHapticFeedback(HapticFeedbackType.LongPress)
}
@Composable fun rememberHaptics(): Haptics { val h = LocalHapticFeedback.current; return remember(h) { Haptics(h) } }

/** Thin Apple-style progress slider: 4 dp track that thickens while dragging. Values clamped to 0..1. */
@Composable fun ThinSlider(value: Float, onChange: (Float) -> Unit, onDone: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
  val haptics = rememberHaptics()
  var dragging by remember { mutableStateOf(false) }
  val thick by animateFloatAsState(if (dragging) 1f else 0f, spring(dampingRatio = .6f), label = "thick")
  val v = value.coerceIn(0f, 1f)
  val change by rememberUpdatedState(onChange); val done by rememberUpdatedState(onDone)
  Box(modifier.fillMaxWidth().height(28.dp).then(if (!enabled) Modifier else Modifier.pointerInputSlider(
    onStart = { dragging = true; haptics.tick(); change(it) }, onMove = { change(it) }, onEnd = { dragging = false; haptics.tick(); done() })),
    contentAlignment = androidx.compose.ui.Alignment.CenterStart) {
    val hgt: Dp = 4.dp + 4.dp * thick.coerceIn(0f, 1f)
    Box(Modifier.fillMaxWidth().height(hgt).clip(RoundedCorner).background(Color.White.copy(alpha = .22f)))
    Box(Modifier.fillMaxWidth(v).height(hgt).clip(RoundedCorner).background(Color.White.copy(alpha = .92f)))
  }
}
private val RoundedCorner = androidx.compose.foundation.shape.RoundedCornerShape(50)

private fun Modifier.pointerInputSlider(onStart: (Float) -> Unit, onMove: (Float) -> Unit, onEnd: () -> Unit) =
  this.pointerInput(Unit) {
    detectHorizontalDragGestures(
      onDragStart = { o -> onStart((o.x / size.width).coerceIn(0f, 1f)) },
      onDragEnd = onEnd, onDragCancel = onEnd) { change, _ -> onMove((change.position.x / size.width).coerceIn(0f, 1f)) }
  }.pointerInput(Unit) {
    detectTapGestures { o -> onStart((o.x / size.width).coerceIn(0f, 1f)); onEnd() }
  }
