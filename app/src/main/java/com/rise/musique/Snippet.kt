package com.rise.musique

import android.content.Context
import android.graphics.*
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.effect.BitmapOverlay
import androidx.media3.effect.OverlayEffect
import androidx.media3.transformer.*
import com.google.common.collect.ImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Share snippets: a 9:16 card (cover + title + lyrics of the chosen range) and, for LOCAL files only, a ~15 s MP4
 * with that card, the lyric lines animating in sync and the audio clip of the range (Media3 Transformer).
 * Streams get the card image only: clipping YouTube audio into a new file would mean redistributing it.
 */
@androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])
object Snippet {
  const val LENGTH_MS = 15_000L
  /** Lyric lines that fall in [start, start+len), with the line active at [start] first if none starts there. Visible for tests. */
  fun linesIn(lines: List<Lyrics.Line>?, start: Long, len: Long = LENGTH_MS): List<Lyrics.Line> {
    if (lines.isNullOrEmpty()) return emptyList()
    val inside = lines.filter { it.ms in start until start + len && it.text.isNotBlank() }
    val before = lines.lastOrNull { it.ms <= start && it.text.isNotBlank() }
    return (listOfNotNull(before?.takeIf { inside.firstOrNull()?.ms != start }) + inside).take(6)
  }
  /** Clamp the snippet start so the window fits in the track. */
  fun clampStart(start: Long, dur: Long, len: Long = LENGTH_MS) = start.coerceIn(0, (dur - len).coerceAtLeast(0))

  /** The 9:16 card. [active] = index of the highlighted lyric line (-1 = all equal). */
  fun card(title: String, artist: String, cover: Bitmap?, lines: List<String>, active: Int = -1, w: Int = 1080, h: Int = 1920): Bitmap {
    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888); val c = Canvas(bmp)
    val base = cover?.let { dominant(it) } ?: Color.rgb(60, 40, 70)
    val hsl = FloatArray(3); androidx.core.graphics.ColorUtils.colorToHSL(base, hsl)
    val top = androidx.core.graphics.ColorUtils.HSLToColor(floatArrayOf(hsl[0], (hsl[1] * .6f).coerceAtMost(.5f), .32f))
    val bottom = androidx.core.graphics.ColorUtils.HSLToColor(floatArrayOf(hsl[0], (hsl[1] * .5f).coerceAtMost(.4f), .1f))
    c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), Paint().apply { shader = LinearGradient(0f, 0f, 0f, h.toFloat(), top, bottom, Shader.TileMode.CLAMP) })
    val m = w * .1f; val side = w - 2 * m; val coverTop = h * .1f
    val rect = RectF(m, coverTop, m + side, coverTop + side)
    val p = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    c.drawRoundRect(RectF(rect).apply { offset(0f, 18f) }, 48f, 48f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(90, 0, 0, 0); maskFilter = BlurMaskFilter(40f, BlurMaskFilter.Blur.NORMAL) })
    c.save(); c.clipPath(Path().apply { addRoundRect(rect, 48f, 48f, Path.Direction.CW) })
    if (cover != null) c.drawBitmap(cover, Rect(0, 0, cover.width, cover.height).squareCenter(), rect, p)
    else c.drawRect(rect, Paint().apply { color = Color.argb(60, 255, 255, 255) })
    c.restore()
    var y = rect.bottom + w * .1f
    val tp = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; typeface = Typeface.DEFAULT_BOLD; textSize = w * .058f }
    c.drawText(ellipsize(title, tp, side), m, y, tp); y += w * .055f
    val ap = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(185, 255, 255, 255); textSize = w * .042f }
    c.drawText(ellipsize(artist, ap, side), m, y, ap); y += w * .09f
    val lp = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textSize = w * .05f }
    lines.forEachIndexed { i, l ->
      if (y > h - w * .16f) return@forEachIndexed
      lp.color = if (active < 0 || i == active) Color.WHITE else Color.argb(110, 255, 255, 255)
      wrap(l, lp, side).take(2).forEach { part -> c.drawText(part, m, y, lp); y += lp.textSize * 1.25f }
      y += lp.textSize * .35f
    }
    val bp = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(200, 255, 255, 255); typeface = Typeface.DEFAULT_BOLD; textSize = w * .04f; letterSpacing = .02f }
    c.drawText("rise", m, h - w * .08f, bp)
    return bmp
  }

  private fun Rect.squareCenter(): Rect { val s = minOf(width(), height()); val l = left + (width() - s) / 2; val t = top + (height() - s) / 2; return Rect(l, t, l + s, t + s) }
  private fun dominant(b: Bitmap) = runCatching { Bitmap.createScaledBitmap(b, 1, 1, true).getPixel(0, 0) }.getOrDefault(Color.DKGRAY)
  private fun ellipsize(t: String, p: Paint, w: Float): String { if (p.measureText(t) <= w) return t
    var s = t; while (s.isNotEmpty() && p.measureText("$s…") > w) s = s.dropLast(1); return "$s…" }
  private fun wrap(t: String, p: Paint, w: Float): List<String> { val out = mutableListOf(""); t.split(" ").forEach { word ->
    val n = (out.last() + " " + word).trim(); if (p.measureText(n) > w && out.last().isNotEmpty()) out += word else out[out.size - 1] = n }; return out }

  fun shareDir(ctx: Context) = File(ctx.cacheDir, "share").apply { mkdirs() }

  suspend fun saveCard(ctx: Context, card: Bitmap): File = withContext(Dispatchers.IO) {
    File(shareDir(ctx), "rise-trecho.png").also { f -> f.outputStream().use { card.compress(Bitmap.CompressFormat.PNG, 95, it) } } }

  /** ~15 s MP4: background card + per-line highlighted overlay frames + the audio range. Must run with a Looper (Main). */
  suspend fun video(ctx: Context, audio: Uri, startMs: Long, title: String, artist: String, cover: Bitmap?, lines: List<Lyrics.Line>): File {
    val texts = lines.map { it.text }
    val frames = if (texts.isEmpty()) listOf(card(title, artist, cover, emptyList(), w = 720, h = 1280))
      else texts.indices.map { card(title, artist, cover, texts, it, 720, 1280) }
    val bg = withContext(Dispatchers.IO) { File(shareDir(ctx), "rise-bg.png").also { f -> f.outputStream().use { frames[0].compress(Bitmap.CompressFormat.PNG, 95, it) } } }
    val overlay = object : BitmapOverlay() {
      override fun getBitmap(presentationTimeUs: Long): Bitmap {
        val t = startMs + presentationTimeUs / 1000
        val i = lines.indexOfLast { it.ms <= t }.coerceAtLeast(0)
        return frames[i.coerceAtMost(frames.lastIndex)]
      }
    }
    val image = EditedMediaItem.Builder(MediaItem.Builder().setUri(Uri.fromFile(bg)).setImageDurationMs(LENGTH_MS).build())
      .setFrameRate(30).setEffects(Effects(emptyList(), listOf(OverlayEffect(ImmutableList.of<androidx.media3.effect.TextureOverlay>(overlay))))).build()
    val clip = EditedMediaItem.Builder(MediaItem.Builder().setUri(audio)
      .setClippingConfiguration(MediaItem.ClippingConfiguration.Builder().setStartPositionMs(startMs).setEndPositionMs(startMs + LENGTH_MS).build()).build())
      .setRemoveVideo(true).build()
    val composition = Composition.Builder(EditedMediaItemSequence(image), EditedMediaItemSequence(clip)).build()
    val out = File(shareDir(ctx), "rise-trecho.mp4").apply { delete() }
    return withContext(Dispatchers.Main) { suspendCancellableCoroutine { cont ->
      val tr = Transformer.Builder(ctx).setVideoMimeType(MimeTypes.VIDEO_H264).setAudioMimeType(MimeTypes.AUDIO_AAC)
        .addListener(object : Transformer.Listener {
          override fun onCompleted(composition: Composition, exportResult: ExportResult) { if (cont.isActive) cont.resume(out) }
          override fun onError(composition: Composition, exportResult: ExportResult, exportException: ExportException) { if (cont.isActive) cont.resumeWithException(exportException) }
        }).build()
      cont.invokeOnCancellation { android.os.Handler(android.os.Looper.getMainLooper()).post { runCatching { tr.cancel() } } }
      tr.start(composition, out.path)
    } }
  }
}
