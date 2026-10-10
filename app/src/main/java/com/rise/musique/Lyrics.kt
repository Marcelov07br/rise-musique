package com.rise.musique
import kotlinx.coroutines.*
import org.json.*
import java.net.*

/** Letras sincronizadas da LRCLIB (base aberta, sem chave). */
object Lyrics {
  data class Line(val ms: Long, val text: String)

  /** Limpa títulos de YouTube: "Artista - Música (Clipe Oficial) [4K]" vira artista e música. */
  fun guess(title: String, artist: String): Pair<String, String> {
    val clean = title.replace(Regex("[(\\[][^)\\]]*[)\\]]"), "").replace(Regex("(?i)\\b(official|video|audio|lyrics?|clipe|oficial|hd|4k)\\b"), "")
      .replace(Regex("\\s+"), " ").trim(' ', '-', '|')
    val parts = clean.split(Regex("\\s[-–|]\\s"), 2)
    return if (parts.size == 2) parts[0].trim() to parts[1].trim()
           else artist.removeSuffix(" - Topic").removeSuffix("VEVO").trim() to clean
  }

  suspend fun fetch(title: String, artist: String, durationSec: Long): List<Line>? = withContext(Dispatchers.IO) {
    val (a, t) = guess(title, artist)
    fun get(u: String): String? = runCatching { (URL(u).openConnection() as HttpURLConnection).run {
      setRequestProperty("User-Agent", "RiseMusique/0.6 (https://github.com)"); connectTimeout = 8000; readTimeout = 8000
      if (responseCode == 200) inputStream.bufferedReader().readText() else null } }.getOrNull()
    val enc = { s: String -> URLEncoder.encode(s, "UTF-8") }
    val direct = if (a.isNotBlank()) get("https://lrclib.net/api/get?artist_name=${enc(a)}&track_name=${enc(t)}" +
      (if (durationSec > 0) "&duration=$durationSec" else ""))?.let { JSONObject(it).optString("syncedLyrics") } else null
    val synced = direct?.takeIf { it.isNotBlank() && it != "null" } ?: get("https://lrclib.net/api/search?q=${enc("$a $t".trim())}")
      ?.let { JSONArray(it) }?.let { arr -> (0 until arr.length()).map { arr.getJSONObject(it) }
        .firstOrNull { it.optString("syncedLyrics").let { s -> s.isNotBlank() && s != "null" } }?.optString("syncedLyrics") }
    synced?.let(::parse)?.takeIf { it.isNotEmpty() }
  }

  fun parse(lrc: String): List<Line> = lrc.lines().flatMap { l ->
    val tags = Regex("\\[(\\d+):(\\d+)(?:[.:](\\d+))?]").findAll(l).toList()
    val text = l.replace(Regex("\\[[^]]*]"), "").trim()
    tags.map { m -> val (mm, ss, frac) = m.destructured
      Line(mm.toLong() * 60_000 + ss.toLong() * 1000 + (frac.padEnd(3, '0').take(3).toLongOrNull() ?: 0), text) }
  }.sortedBy { it.ms }
}
