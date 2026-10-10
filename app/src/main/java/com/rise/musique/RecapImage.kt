package com.rise.musique

import android.content.Context
import android.graphics.*
import androidx.core.content.FileProvider
import java.io.File

/** Renders the retrospective as a 1080×1920 (9:16) story image in the share cache and returns a content URI. */
object RecapImage {
  fun render(r: Recap): Bitmap {
    val w = 1080; val h = 1920
    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888); val c = Canvas(bmp)
    c.drawColor(Color.rgb(10, 10, 12))
    // Soft neutral glow blobs (same idea as the living artwork, frozen).
    fun blob(x: Float, y: Float, r0: Float, col: Int) = c.drawCircle(x, y, r0, Paint(Paint.ANTI_ALIAS_FLAG).apply {
      shader = RadialGradient(x, y, r0, col, Color.TRANSPARENT, Shader.TileMode.CLAMP) })
    blob(200f, 300f, 900f, Color.argb(150, 90, 90, 100)); blob(950f, 1200f, 1000f, Color.argb(120, 70, 70, 80)); blob(300f, 1800f, 800f, Color.argb(110, 120, 120, 130))
    val white = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD) }
    val quiet = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(170, 255, 255, 255); typeface = Typeface.SANS_SERIF }
    var y = 230f
    fun text(t: String, p: Paint, size: Float, gap: Float) { p.textSize = size; c.drawText(ellipsize(t, p, w - 160f), 80f, y, p); y += gap }
    text("rise", white, 64f, 150f)
    text(r.title, white, 110f, 80f)
    text(r.period, quiet, 44f, 200f)
    text("${r.minutes}", white, 220f, 70f)
    text("minutos ouvindo · ${r.plays} reproduções", quiet, 44f, 170f)
    r.topArtist?.let { text("ARTISTA DO PERÍODO", quiet, 36f, 75f); text(it.name, white, 76f, 150f) }
    if (r.topTracks.isNotEmpty()) { text("MAIS TOCADAS", quiet, 36f, 85f)
      r.topTracks.forEachIndexed { i, t -> text("${i + 1}  ${t.name}", white, 58f, 64f); if (t.sub.isNotBlank()) text("    ${t.sub} · ${t.plays}×", quiet, 38f, 70f) } }
    quiet.textSize = 34f; c.drawText("Rise Musique", 80f, h - 100f, quiet)
    return bmp
  }
  private fun ellipsize(t: String, p: Paint, max: Float): String { if (p.measureText(t) <= max) return t
    var s = t; while (s.isNotEmpty() && p.measureText("$s…") > max) s = s.dropLast(1); return "$s…" }

  fun share(c: Context, r: Recap): android.net.Uri {
    val dir = File(c.cacheDir, "share").apply { mkdirs() }
    val f = File(dir, "rise-retrospectiva.png")
    f.outputStream().use { render(r).compress(Bitmap.CompressFormat.PNG, 100, it) }
    return FileProvider.getUriForFile(c, "${c.packageName}.files", f)
  }
}
