package com.rise.musique.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import coil.request.ImageRequest

/**
 * Per-artwork soft tints (never system dynamic color): the cover's dominant hue, desaturated to low chroma and with
 * lightness clamped per theme, so tiles read as "a little colored" in both light and dark mode.
 */
object Tint {
  /** HSL of [c]: h 0..360, s 0..1, l 0..1. */
  fun hsl(c: Color): FloatArray { val o = FloatArray(3); androidx.core.graphics.ColorUtils.colorToHSL(c.toArgb(), o); return o }
  fun soft(base: Color, dark: Boolean): Color {
    val (h, s, _) = hsl(base).let { Triple(it[0], it[1], it[2]) }
    val sat = (s * .45f).coerceIn(.06f, .26f)
    val l = if (dark) .22f else .88f
    return Color(androidx.core.graphics.ColorUtils.HSLToColor(floatArrayOf(h, sat, l)))
  }
  /** Stronger, still muted, for text scrims over art (bottom of hero cards). */
  fun scrim(base: Color): Color { val (h, s) = hsl(base).let { it[0] to it[1] }
    return Color(androidx.core.graphics.ColorUtils.HSLToColor(floatArrayOf(h, (s * .55f).coerceIn(.08f, .38f), .2f))) }
  /** Deterministic hue for items without art (or before the palette loads) so rows still vary. */
  fun seeded(key: String): Color = Color(androidx.core.graphics.ColorUtils.HSLToColor(floatArrayOf((key.hashCode().toLong().and(0xffff) % 360).toFloat(), .35f, .45f)))
  /** Roughly two of three cards tinted, the rest neutral — variety without a rainbow. */
  fun tinted(key: String) = Math.floorMod(key.hashCode(), 3) != 0
}

private val DominantCache = java.util.Collections.synchronizedMap(object : LinkedHashMap<String, Color>(128, .75f, true) {
  override fun removeEldestEntry(e: MutableMap.MutableEntry<String, Color>?) = size > 200 })

/** Dominant cover color (cached across screens), seeded fallback while loading / without art. */
@Composable fun rememberDominant(url: String, key: String): Color {
  val ctx = LocalContext.current
  var c by remember(url, key) { mutableStateOf(DominantCache[url] ?: Tint.seeded(key)) }
  LaunchedEffect(url) {
    if (url.isBlank() || StaticPreview.on || DominantCache.containsKey(url)) return@LaunchedEffect
    runCatching {
      val res = coil.ImageLoader(ctx).execute(ImageRequest.Builder(ctx).data(url).size(64).allowHardware(false).build())
      val bmp = (res.drawable as? android.graphics.drawable.BitmapDrawable)?.bitmap ?: return@runCatching
      val pal = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) { androidx.palette.graphics.Palette.from(bmp).maximumColorCount(12).generate() }
      (pal.vibrantSwatch ?: pal.dominantSwatch ?: pal.mutedSwatch)?.let { s -> Color(s.rgb).also { DominantCache[url] = it; c = it } }
    }.onFailure { if (it is kotlinx.coroutines.CancellationException) throw it }
  }
  return c
}

/** Card background: soft tint for ~2/3 of keys, neutral surface for the rest. */
@Composable fun tileColor(url: String, key: String): Color {
  val dark = MaterialTheme.colorScheme.background.luminance() < .5f
  val dom = rememberDominant(url, key)
  return if (Tint.tinted(key)) Tint.soft(dom, dark) else MaterialTheme.colorScheme.surfaceVariant
}
private fun Color.luminance() = androidx.compose.ui.graphics.Color(this.value).let { .2126f * it.red + .7152f * it.green + .0722f * it.blue }
