package com.rise.musique

import com.rise.musique.ui.SearchResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

enum class SearchKind(val label: String) { Songs("Músicas"), Videos("Vídeos"), Albums("Álbuns"), Artists("Artistas"), Playlists("Playlists") }
enum class DurationFilter(val label: String, val sp: Int) { Any("Qualquer", 0), Short("Curta", 1), Medium("Média", 3), Long("Longa", 2);
  fun accepts(sec: Int) = when (this) { Any -> true; Short -> sec in 1 until 240; Medium -> sec in 240..1200; Long -> sec > 1200 } }
enum class DateFilter(val label: String, val sp: Int) { Any("Qualquer data", 0), Today("Hoje", 2), Week("Semana", 3), Month("Mês", 4), Year("Ano", 5) }

data class SearchQuery(val text: String, val kind: SearchKind = SearchKind.Songs, val duration: DurationFilter = DurationFilter.Any, val date: DateFilter = DateFilter.Any)
data class SearchPage(val items: List<SearchResult>, val next: String?)

/**
 * YouTube Music-aware search. Songs/videos/albums/artists/playlists come from YouTube Music's own search (innertube WEB_REMIX,
 * the same request music.youtube.com makes); date/duration filters on videos go through yt-dlp's YouTube results URL with the
 * filter (sp) the site uses. Pages are cached for 10 minutes; [next] continues the list (infinite scroll).
 */
object MusicSearch {
  private val PARAMS = mapOf(SearchKind.Songs to "EgWKAQIIAWoKEAkQBRAKEAMQBA==", SearchKind.Videos to "EgWKAQIQAWoKEAkQChAFEAMQBA==",
    SearchKind.Albums to "EgWKAQIYAWoKEAkQChAFEAMQBA==", SearchKind.Artists to "EgWKAQIgAWoKEAkQChAFEAMQBA==", SearchKind.Playlists to "EgeKAQQoAEABagoQAxAEEAkQChAF")
  private const val API = "https://music.youtube.com/youtubei/v1/search?prettyPrint=false"
  private val cache = object : LinkedHashMap<String, Pair<Long, SearchPage>>(32, .75f, true) {
    override fun removeEldestEntry(e: MutableMap.MutableEntry<String, Pair<Long, SearchPage>>?) = size > 40 }
  private const val TTL = 10 * 60_000L

  suspend fun search(q: SearchQuery, next: String? = null): SearchPage = withContext(Dispatchers.IO) {
    val key = "$q|$next"
    synchronized(cache) { cache[key]?.takeIf { System.currentTimeMillis() - it.first < TTL }?.second }?.let { return@withContext it }
    val page = if (q.kind == SearchKind.Videos && q.date != DateFilter.Any) ytResults(q, next?.toIntOrNull() ?: 0)
      else music(q, next)
    val filtered = page.copy(items = rank(q, page.items.filter { it.kind !in setOf("song", "video") || q.duration.accepts(it.duration) || it.duration == 0 }))
    synchronized(cache) { cache[key] = System.currentTimeMillis() to filtered }; filtered
  }

  private fun music(q: SearchQuery, next: String?): SearchPage {
    val body = JSONObject().put("context", JSONObject().put("client", JSONObject().put("clientName", "WEB_REMIX")
      .put("clientVersion", "1.20250101.01.00").put("hl", "pt").put("gl", "BR")))
    val url = if (next != null) "$API&ctoken=$next&continuation=$next&type=next" else { body.put("query", q.text).put("params", PARAMS[q.kind]); API }
    return parseMusic(post(url, body), q.kind)
  }

  private fun post(url: String, body: JSONObject): JSONObject {
    val c = URL(url).openConnection() as HttpURLConnection
    c.requestMethod = "POST"; c.doOutput = true; c.connectTimeout = 10_000; c.readTimeout = 15_000
    c.setRequestProperty("Content-Type", "application/json"); c.setRequestProperty("User-Agent", Engine.DEFAULT_UA)
    c.setRequestProperty("Origin", "https://music.youtube.com")
    c.outputStream.use { it.write(body.toString().toByteArray()) }
    return try { JSONObject(c.inputStream.bufferedReader().readText()) } finally { c.disconnect() }
  }

  /** www.youtube.com/results with the site's filter: field 1 = upload date, 2 = type (1 video), 3 = duration. */
  fun spFor(q: SearchQuery): String {
    val bytes = buildList { if (q.date.sp > 0) { add(0x08); add(q.date.sp) }; add(0x10); add(1); if (q.duration.sp > 0) { add(0x18); add(q.duration.sp) } }
    val msg = byteArrayOf(0x12, bytes.size.toByte()) + bytes.map { it.toByte() }.toByteArray()
    return java.util.Base64.getEncoder().encodeToString(msg)
  }

  private suspend fun ytResults(q: SearchQuery, offset: Int): SearchPage {
    val url = "https://www.youtube.com/results?search_query=${URLEncoder.encode(q.text, "UTF-8")}&sp=${URLEncoder.encode(spFor(q), "UTF-8")}"
    val req = com.yausername.youtubedl_android.YoutubeDLRequest(url).apply { addOption("-J"); addOption("--flat-playlist")
      addOption("--playlist-items", "${offset + 1}:${offset + 20}") }
    val items = Engine.parseEntries(kotlinx.coroutines.runInterruptible { com.yausername.youtubedl_android.YoutubeDL.getInstance().execute(req).out })
      .map { it.copy(thumb = hiRes(it.thumb, Engine.videoId(it.url))) }
    return SearchPage(items, if (items.size >= 20) "${offset + 20}" else null)
  }

  // ---- parsing (pure, unit-tested) ----

  private fun walk(o: Any?, key: String, out: MutableList<Any> = mutableListOf()): MutableList<Any> {
    when (o) { is JSONObject -> o.keys().forEach { k -> val v = o.get(k); if (k == key) out += v; walk(v, key, out) }
      is JSONArray -> for (i in 0 until o.length()) walk(o.get(i), key, out) }
    return out
  }
  private fun runs(o: JSONObject?) = o?.optJSONArray("runs")?.let { a -> (0 until a.length()).joinToString("") { a.getJSONObject(it).optString("text") } }.orEmpty()

  fun parseMusic(root: JSONObject, kind: SearchKind): SearchPage {
    val items = walk(root, "musicResponsiveListItemRenderer").mapNotNull { it as? JSONObject }.mapNotNull { r -> runCatching { item(r, kind) }.getOrNull() }
    val next = walk(root, "nextContinuationData").firstOrNull()?.let { (it as JSONObject).optString("continuation") }?.takeIf { it.isNotBlank() }
    return SearchPage(items.distinctBy { it.url }, next)
  }

  private fun item(r: JSONObject, kind: SearchKind): SearchResult? {
    val cols = r.optJSONArray("flexColumns") ?: return null
    val col = { i: Int -> runs(cols.optJSONObject(i)?.optJSONObject("musicResponsiveListItemFlexColumnRenderer")?.optJSONObject("text")) }
    val title = col(0).ifBlank { return null }
    val parts = col(1).split(" • ").map(String::trim).filter(String::isNotBlank)
    val thumbs = walk(r.optJSONObject("thumbnail"), "thumbnails").firstOrNull() as? JSONArray
    val rawThumb = thumbs?.let { it.optJSONObject(it.length() - 1)?.optString("url") }.orEmpty()
    val browse = r.optJSONObject("navigationEndpoint")?.optJSONObject("browseEndpoint")?.optString("browseId").orEmpty()
    val videoId = r.optJSONObject("playlistItemData")?.optString("videoId")?.takeIf { it.isNotBlank() }
      ?: (walk(r.optJSONObject("overlay"), "videoId").firstOrNull() as? String)
    fun dur(s: String?) = s?.takeIf { Regex("^\\d+(:\\d\\d)+$").matches(it) }?.split(":")?.fold(0) { a, p -> a * 60 + p.toInt() } ?: 0
    return when (kind) {
      SearchKind.Songs, SearchKind.Videos -> { val id = videoId ?: return null
        val d = dur(parts.lastOrNull())
        val artist = parts.firstOrNull().orEmpty()
        val album = if (kind == SearchKind.Songs && parts.size >= 3) parts[parts.size - 2] else ""
        SearchResult("https://music.youtube.com/watch?v=$id", title, artist, d, hiRes(rawThumb, id), if (kind == SearchKind.Songs) "song" else "video", album,
          parts.dropLast(if (d > 0) 1 else 0).joinToString(" • ")) }
      SearchKind.Albums -> { if (browse.isBlank()) return null
        SearchResult("https://music.youtube.com/browse/$browse", title, parts.getOrNull(1).orEmpty(), 0, hiRes(rawThumb, null), "album", title, parts.joinToString(" • ")) }
      SearchKind.Artists -> SearchResult("https://music.youtube.com/channel/$browse", title, title, 0, hiRes(rawThumb, null), "artist", "", parts.drop(1).joinToString(" • "))
      SearchKind.Playlists -> { if (browse.isBlank()) return null
        SearchResult("https://music.youtube.com/playlist?list=${browse.removePrefix("VL")}", title, parts.firstOrNull().orEmpty(), 0, hiRes(rawThumb, null), "playlist", "", parts.joinToString(" • ")) }
    }
  }

  /** Largest thumbnail the CDN serves: googleusercontent "=w60-h60" → 544px; ytimg → hqdefault without crop params. */
  fun hiRes(url: String, videoId: String?): String = when {
    url.contains("googleusercontent.com") || url.contains("ggpht.com") -> url.replace(Regex("=w\\d+-h\\d+[^&]*$"), "=w544-h544-l90-rj").replace(Regex("=s\\d+[^&]*$"), "=s544")
    url.contains("i.ytimg.com") && videoId != null -> "https://i.ytimg.com/vi/$videoId/hqdefault.jpg"
    url.isBlank() && videoId != null -> "https://i.ytimg.com/vi/$videoId/hqdefault.jpg"
    else -> url
  }

  private val penalized = listOf("live", "ao vivo", "cover", "karaok", "reaction", "react", "8d", "slowed", "sped up", "nightcore", "remix", "instrumental", "lyrics video", "tutorial")
  /** Songs first by "the real recording": topic/official-audio up, live/cover/remix/slowed down unless asked for. Stable otherwise. */
  fun rank(q: SearchQuery, items: List<SearchResult>): List<SearchResult> {
    if (q.kind != SearchKind.Songs && q.kind != SearchKind.Videos) return items
    val ql = q.text.lowercase()
    fun score(r: SearchResult): Int { val t = r.title.lowercase(); val ch = r.channel.lowercase(); var s = 0
      if (ch.endsWith(" - topic") || r.kind == "song") s += 3
      if ("official audio" in t || "áudio oficial" in t || "audio oficial" in t) s += 2
      if ("official video" in t || "clipe oficial" in t || "official music video" in t) s += 1
      if (ql.split(" ").filter { it.length > 2 }.all { it in t || it in ch }) s += 1
      s -= penalized.count { it in t && it !in ql } * 3
      return s }
    return items.withIndex().sortedWith(compareByDescending<IndexedValue<SearchResult>> { score(it.value) }.thenBy { it.index }).map { it.value }
  }
}
