package com.rise.musique

import android.content.Context
import com.rise.musique.ui.SearchResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** A YouTube Music artist page (innertube browse of the artist channel). */
data class ArtistPage(val id: String, val name: String, val image: String, val about: String = "", val listeners: String = "",
  val top: List<SearchResult> = emptyList(), val albums: List<SearchResult> = emptyList(), val singles: List<SearchResult> = emptyList(),
  val videos: List<SearchResult> = emptyList(), val similar: List<SearchResult> = emptyList())

/** A streamed album: header + tracklist from YouTube Music. */
data class AlbumPage(val id: String, val title: String, val artist: String, val year: String, val art: String, val tracks: List<SearchResult>)

/**
 * Artist / album pages from YouTube Music's own innertube browse (WEB_REMIX — the same request music.youtube.com makes and
 * the same API search already uses). Responses are cached raw on disk for a day (pages load instantly offline-ish).
 */
object ArtistBrowse {
  private const val API = "https://music.youtube.com/youtubei/v1/browse?prettyPrint=false"
  private const val TTL = 24 * 3600_000L
  private val mem = HashMap<String, Any>()

  suspend fun artist(ctx: Context, name: String, channelId: String? = null): ArtistPage = withContext(Dispatchers.IO) {
    synchronized(mem) { mem["ar:$name"] as? ArtistPage }?.let { return@withContext it }
    val id = channelId ?: resolveArtist(name) ?: error("Artista não encontrado")
    val page = parseArtist(cached(ctx, "ar-$id") { browse(id) }, id).let { if (it.name.isBlank()) it.copy(name = name) else it }
    synchronized(mem) { mem["ar:$name"] = page }; page
  }
  suspend fun album(ctx: Context, browseId: String): AlbumPage = withContext(Dispatchers.IO) {
    synchronized(mem) { mem["al:$browseId"] as? AlbumPage }?.let { return@withContext it }
    parseAlbum(cached(ctx, "al-$browseId") { browse(browseId) }, browseId).also { p -> synchronized(mem) { mem["al:$browseId"] = p } }
  }

  /** Channel id of the artist whose name matches best (YouTube Music artist search). */
  private suspend fun resolveArtist(name: String): String? {
    val items = MusicSearch.search(SearchQuery(name, SearchKind.Artists)).items
    val hit = items.firstOrNull { it.title.equals(name, true) } ?: items.firstOrNull()
    return hit?.url?.substringAfter("/channel/", "")?.takeIf { it.isNotBlank() }
  }

  private fun cached(ctx: Context, key: String, load: () -> JSONObject): JSONObject {
    val f = File(ctx.cacheDir, "ytm/${key.replace(Regex("[^\\w-]"), "_")}.json")
    if (f.exists() && System.currentTimeMillis() - f.lastModified() < TTL) runCatching { return JSONObject(f.readText()) }
    val j = load(); runCatching { f.parentFile?.mkdirs(); f.writeText(j.toString()) }; return j
  }
  private fun browse(id: String): JSONObject {
    val body = JSONObject().put("context", JSONObject().put("client", JSONObject().put("clientName", "WEB_REMIX")
      .put("clientVersion", "1.20250101.01.00").put("hl", "pt").put("gl", "BR"))).put("browseId", id)
    val c = URL(API).openConnection() as HttpURLConnection
    c.requestMethod = "POST"; c.doOutput = true; c.connectTimeout = 10_000; c.readTimeout = 15_000
    c.setRequestProperty("Content-Type", "application/json"); c.setRequestProperty("User-Agent", Engine.DEFAULT_UA)
    c.setRequestProperty("Origin", "https://music.youtube.com")
    c.outputStream.use { it.write(body.toString().toByteArray()) }
    return try { JSONObject(c.inputStream.bufferedReader().readText()) } finally { c.disconnect() }
  }

  // ---- parsing (pure, unit-tested against saved responses) ----

  private fun walk(o: Any?, key: String, out: MutableList<Any> = mutableListOf()): MutableList<Any> {
    when (o) { is JSONObject -> o.keys().forEach { k -> val v = o.get(k); if (k == key) out += v; walk(v, key, out) }
      is JSONArray -> for (i in 0 until o.length()) walk(o.get(i), key, out) }
    return out
  }
  private fun runs(o: JSONObject?) = o?.optJSONArray("runs")?.let { a -> (0 until a.length()).joinToString("") { a.getJSONObject(it).optString("text") } }.orEmpty()
  private fun lastThumb(o: Any?): String = (walk(o, "thumbnails").firstOrNull() as? JSONArray)?.let { it.optJSONObject(it.length() - 1)?.optString("url") }.orEmpty()

  /** Bigger artwork from the CDN: square hero crop for the artist, 544px for covers. */
  fun heroImage(url: String) = if (url.contains("googleusercontent.com") || url.contains("ggpht.com"))
    url.replace(Regex("=w\\d+-h\\d+[^&]*$"), "=w1080-h1080-p-l90-rj").replace(Regex("=s\\d+[^&]*$"), "=s1080") else url

  fun parseArtist(root: JSONObject, id: String): ArtistPage {
    val header = root.optJSONObject("header")?.let { h -> h.keys().asSequence().firstOrNull()?.let { h.optJSONObject(it) } }
    val name = runs(header?.optJSONObject("title"))
    val image = heroImage(lastThumb(header?.optJSONObject("thumbnail")))
    var about = runs(header?.optJSONObject("description"))
    val listeners = runs(header?.optJSONObject("monthlyListenerCount"))
    var top = emptyList<SearchResult>(); var albums = emptyList<SearchResult>(); var singles = emptyList<SearchResult>()
    var videos = emptyList<SearchResult>(); var similar = emptyList<SearchResult>()
    for (shelf in walk(root, "musicShelfRenderer").filterIsInstance<JSONObject>()) {
      if (top.isEmpty()) top = (walk(shelf, "musicResponsiveListItemRenderer").filterIsInstance<JSONObject>()).mapNotNull { row(it, name, "") } }
    for (car in walk(root, "musicCarouselShelfRenderer").filterIsInstance<JSONObject>()) {
      val title = runs(car.optJSONObject("header")?.optJSONObject("musicCarouselShelfBasicHeaderRenderer")?.optJSONObject("title")).lowercase()
      val cards = walk(car, "musicTwoRowItemRenderer").filterIsInstance<JSONObject>().mapNotNull { card(it, name) }
      when {
        title.startsWith("álbu") || title.startsWith("album") -> albums = cards
        title.startsWith("single") -> singles = cards
        title.startsWith("víd") || title.startsWith("vid") -> videos = cards.filter { it.kind == "video" }
        title.contains("fãs") || title.contains("fans") || title.contains("parecid") || title.contains("similar") -> similar = cards.filter { it.kind == "artist" }
      }
    }
    walk(root, "musicDescriptionShelfRenderer").filterIsInstance<JSONObject>().firstOrNull()?.let { d -> runs(d.optJSONObject("description")).takeIf { it.length > about.length }?.let { about = it } }
    return ArtistPage(id, name, image, about, listeners, top, albums, singles, videos, similar)
  }

  private fun card(r: JSONObject, artist: String): SearchResult? {
    val title = runs(r.optJSONObject("title")).ifBlank { return null }
    val sub = runs(r.optJSONObject("subtitle"))
    val thumb = lastThumb(r.optJSONObject("thumbnailRenderer"))
    val nav = r.optJSONObject("navigationEndpoint")
    nav?.optJSONObject("watchEndpoint")?.optString("videoId")?.takeIf { it.isNotBlank() }?.let { v ->
      return SearchResult("https://music.youtube.com/watch?v=$v", title, artist, 0, MusicSearch.hiRes(thumb, v), "video", "", sub) }
    val b = nav?.optJSONObject("browseEndpoint") ?: return null
    val id = b.optString("browseId").ifBlank { return null }
    val type = b.optJSONObject("browseEndpointContextSupportedConfigs")?.optJSONObject("browseEndpointContextMusicConfig")?.optString("pageType").orEmpty()
    return when {
      type.endsWith("ARTIST") || id.startsWith("UC") -> SearchResult("https://music.youtube.com/channel/$id", title, title, 0, MusicSearch.hiRes(thumb, null), "artist", "", sub)
      type.endsWith("ALBUM") || id.startsWith("MPRE") -> SearchResult("https://music.youtube.com/browse/$id", title, artist, 0, MusicSearch.hiRes(thumb, null), "album", title, sub)
      else -> null
    }
  }

  private fun row(r: JSONObject, artist: String, album: String, art: String = ""): SearchResult? {
    val cols = r.optJSONArray("flexColumns") ?: return null
    val col = { i: Int -> cols.optJSONObject(i)?.optJSONObject("musicResponsiveListItemFlexColumnRenderer")?.optJSONObject("text") }
    val title = runs(col(0)).ifBlank { return null }
    val id = r.optJSONObject("playlistItemData")?.optString("videoId")?.takeIf { it.isNotBlank() }
      ?: (walk(col(0), "videoId").firstOrNull() as? String) ?: return null
    val by = runs(col(1)).ifBlank { artist }
    val alb = runs(col(3)).ifBlank { runs(col(2)).takeUnless { it.contains("vezes") || it.contains("plays") || it.contains("views") }.orEmpty() }.ifBlank { album }
    val durText = (walk(r.optJSONArray("fixedColumns"), "text").firstOrNull() as? JSONObject)?.let(::runs).orEmpty()
    val dur = durText.takeIf { Regex("^\\d+(:\\d\\d)+$").matches(it) }?.split(":")?.fold(0) { a, p -> a * 60 + p.toInt() } ?: 0
    val thumb = lastThumb(r.optJSONObject("thumbnail")).ifBlank { art }
    return SearchResult("https://music.youtube.com/watch?v=$id", title, by, dur, if (thumb.isNotBlank()) MusicSearch.hiRes(thumb, id) else MusicSearch.hiRes("", id), "song", alb)
  }

  fun parseAlbum(root: JSONObject, id: String): AlbumPage {
    val h = walk(root, "musicResponsiveHeaderRenderer").firstOrNull() as? JSONObject
      ?: (root.optJSONObject("header")?.let { x -> x.keys().asSequence().firstOrNull()?.let { x.optJSONObject(it) } })
    val title = runs(h?.optJSONObject("title"))
    val artist = runs(h?.optJSONObject("straplineTextOne")).ifBlank { runs(h?.optJSONObject("subtitle")).split(" • ").getOrNull(1).orEmpty() }
    val year = runs(h?.optJSONObject("subtitle")).split(" • ").map(String::trim).lastOrNull { Regex("^\\d{4}$").matches(it) }.orEmpty()
    val art = MusicSearch.hiRes(lastThumb(h?.optJSONObject("thumbnail")), null)
    val shelf = walk(root, "musicShelfRenderer").firstOrNull()
    val tracks = walk(shelf, "musicResponsiveListItemRenderer").filterIsInstance<JSONObject>().mapNotNull { row(it, artist, title, art) }
      .map { it.copy(thumb = art.ifBlank { it.thumb }, album = title) }
    return AlbumPage(id, title, artist, year, art, tracks)
  }
}
