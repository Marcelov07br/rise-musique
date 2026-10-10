package com.rise.musique

import android.content.Context
import android.graphics.*
import com.rise.musique.ui.HistoryItem
import java.io.File
import kotlin.math.*

/** An automatic station: name, why it exists, the local seeds it starts from, and its cover palette. */
data class Station(val id: String, val name: String, val subtitle: String, val kind: String, val seeds: List<HistoryItem>, val hue: Float) {
  /** Local tracks mixed into the stream queue (the station's own artist / mood); none for Descobertas. */
  val mixLocal get() = kind != "discover"
}

object Stations {
  /** From listening stats (top artists), mood buckets of the library, plus Descobertas. Visible for tests. */
  fun build(history: List<HistoryItem>, stats: StatsSummary?, info: Map<String, TrackInfo>): List<Station> {
    val local = history.filter { !it.video }
    if (local.isEmpty()) return emptyList()
    val out = mutableListOf<Station>()
    val topArtists = (stats?.topArtists?.map { it.name } ?: emptyList()).ifEmpty {
      local.filter { it.artist.isNotBlank() }.groupingBy { it.artist }.eachCount().entries.sortedByDescending { it.value }.map { it.key } }
    topArtists.filter { it.isNotBlank() }.take(3).forEach { a ->
      val seeds = local.filter { it.artist.equals(a, true) }.take(5)
      if (seeds.isNotEmpty()) out += Station("ar:$a", "Estação de $a", "Baseada no que você mais ouve", "artist", seeds, hueOf(a)) }
    Moods.ALL.forEach { m ->
      val seeds = local.filter { info[it.uri]?.let(Moods::of) == m }
      if (seeds.size >= 2) out += Station("mood:$m", "Mix $m", when (m) { "Calmo" -> "Para desacelerar"; "Foco" -> "Para concentrar"; "Animado" -> "Para levantar"; else -> "Para dançar" },
        "mood", seeds.shuffled(java.util.Random(m.hashCode().toLong())).take(5), hueOf(m)) }
    val top = stats?.topTracks?.mapNotNull { r -> local.firstOrNull { it.title == r.name } }.orEmpty().ifEmpty { local.take(8) }
    out += Station("discover", "Descobertas", "Músicas novas parecidas com as suas", "discover", top.take(5), 280f)
    return out
  }
  fun hueOf(s: String) = (Math.floorMod(s.lowercase().hashCode(), 360)).toFloat()

  /** Generated cover: two-tone gradient, soft abstract shapes and the station name. Cached per id+size. */
  fun cover(ctx: Context?, st: Station, px: Int = 512): Bitmap {
    val f = ctx?.let { File(it.cacheDir, "stations/${st.id.hashCode()}_$px.png") }
    f?.takeIf { it.exists() }?.let { BitmapFactory.decodeFile(it.path)?.let { b -> return b } }
    val b = render(st, px)
    f?.let { runCatching { it.parentFile?.mkdirs(); it.outputStream().use { o -> b.compress(Bitmap.CompressFormat.PNG, 92, o) } } }
    return b
  }

  fun render(st: Station, px: Int): Bitmap {
    val bmp = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888); val c = Canvas(bmp); val s = px.toFloat()
    fun hsl(h: Float, sat: Float, l: Float) = androidx.core.graphics.ColorUtils.HSLToColor(floatArrayOf((h % 360 + 360) % 360, sat, l))
    val h = st.hue
    c.drawRect(0f, 0f, s, s, Paint().apply { shader = LinearGradient(0f, 0f, s, s, hsl(h, .55f, .52f), hsl(h + 38, .6f, .34f), Shader.TileMode.CLAMP) })
    val p = Paint(Paint.ANTI_ALIAS_FLAG)
    when (st.kind) {
      "artist" -> { // concentric arcs (a "station" signal)
        p.style = Paint.Style.STROKE; p.strokeWidth = s * .035f
        for (i in 1..5) { p.color = Color.argb(70 - i * 9, 255, 255, 255); c.drawCircle(s * .78f, s * .22f, s * .16f * i, p) } }
      "mood" -> { // soft overlapping blobs
        p.style = Paint.Style.FILL
        listOf(Triple(.25f, .3f, .32f), Triple(.75f, .45f, .26f), Triple(.45f, .75f, .22f)).forEachIndexed { i, (x, y, r) ->
          p.shader = RadialGradient(s * x, s * y, s * r, hsl(h + 25 * (i + 1), .7f, .7f), Color.TRANSPARENT, Shader.TileMode.CLAMP)
          c.drawCircle(s * x, s * y, s * r, p) }; p.shader = null }
      else -> { // four-point star
        p.style = Paint.Style.STROKE; p.strokeWidth = s * .012f; p.color = Color.argb(150, 255, 255, 255)
        val path = Path(); val cx = s * .5f; val cy = s * .42f; val r = s * .3f; val k = r * .22f
        path.moveTo(cx, cy - r); path.quadTo(cx + k, cy - k, cx + r, cy); path.quadTo(cx + k, cy + k, cx, cy + r)
        path.quadTo(cx - k, cy + k, cx - r, cy); path.quadTo(cx - k, cy - k, cx, cy - r); c.drawPath(path, p) }
    }
    // name, bottom-left, bold, wrapped to two lines
    val tp = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD); textSize = s * .1f }
    val words = st.name.split(" "); val lines = mutableListOf(""); val maxW = s * .84f
    words.forEach { w -> val t = (lines.last() + " " + w).trim(); if (tp.measureText(t) > maxW && lines.last().isNotEmpty()) lines += w else lines[lines.size - 1] = t }
    val shown = lines.take(2); var y = s * .9f - (shown.size - 1) * tp.textSize * 1.1f
    shown.forEach { l -> c.drawText(l, s * .08f, y, tp); y += tp.textSize * 1.1f }
    val sp = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(200, 255, 255, 255); textSize = s * .045f; typeface = Typeface.DEFAULT_BOLD; letterSpacing = .12f }
    c.drawText("RISE", s * .08f, s * .12f, sp)
    return bmp
  }
}

object StationQueue {
  /** Round-robin over each seed's radio so one seed doesn't dominate; dedupes by url. */
  fun interleave(lists: List<List<com.rise.musique.ui.SearchResult>>): List<com.rise.musique.ui.SearchResult> {
    val out = LinkedHashMap<String, com.rise.musique.ui.SearchResult>(); val max = lists.maxOfOrNull { it.size } ?: 0
    for (i in 0 until max) lists.forEach { l -> l.getOrNull(i)?.let { out.putIfAbsent(it.url, it) } }
    return out.values.toList()
  }
  /** One local track after every [every] streams (local first so the station opens with something you know). */
  fun mix(streams: List<com.rise.musique.ui.SearchResult>, locals: List<HistoryItem>, every: Int = 4): List<Any> {
    val out = mutableListOf<Any>(); val l = locals.iterator()
    if (l.hasNext()) out += l.next()
    streams.forEachIndexed { i, s -> out += s; if ((i + 1) % every == 0 && l.hasNext()) out += l.next() }
    l.forEachRemaining { out += it }
    return out
  }
}
