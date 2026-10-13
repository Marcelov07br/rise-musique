package com.rise.musique

import android.content.Context
import com.rise.musique.ui.HistoryItem
import com.rise.musique.ui.SearchResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap

data class SpotifyTrack(
  val uri: String,
  val title: String,
  val artist: String,
  val durationMs: Long,
  val previewUrl: String? = null,
  val isExplicit: Boolean = false
) {
  val durationSec: Int get() = (durationMs / 1000).toInt()
}

data class SpotifyPlaylist(
  val id: String,
  val type: String, // "playlist" or "album"
  val title: String,
  val subtitle: String,
  val art: String,
  val tracks: List<SpotifyTrack>
)

object SpotifyImport {
  const val SCHEME = "rise-spotify"

  private val SPOTIFY_REGEX = Regex(
    """(?:spotify:(playlist|album):([a-zA-Z0-9]+)|spotify\.com/(?:[a-zA-Z0-9_-]+/)*(playlist|album)/([a-zA-Z0-9]+))""",
    RegexOption.IGNORE_CASE
  )

  private val resolvedCache = ConcurrentHashMap<String, SearchResult>()
  private val trackMap = ConcurrentHashMap<String, SpotifyTrack>()

  fun registerTrack(track: SpotifyTrack) {
    trackMap[track.uri] = track
    val bare = track.uri.removePrefix("spotify:")
    if (bare != track.uri) trackMap[bare] = track
  }

  fun getTrack(uri: String): SpotifyTrack? =
    trackMap[uri] ?: trackMap["spotify:$uri"] ?: trackMap[uri.removePrefix("spotify:")]

  fun isSpotifyUrl(text: String): Boolean =
    text.contains("spotify.link/", ignoreCase = true) || SPOTIFY_REGEX.containsMatchIn(text.trim())

  fun parseSpotifyRef(text: String): Pair<String, String>? {
    val m = SPOTIFY_REGEX.find(text.trim()) ?: return null
    val type = m.groups[1]?.value ?: m.groups[3]?.value ?: return null
    val id = m.groups[2]?.value ?: m.groups[4]?.value ?: return null
    return type.lowercase() to id
  }

  /**
   * Fetches the Spotify playlist or album metadata and track list via the public embed page
   * without requiring any user authentication or Spotify Developer credentials.
   */
  suspend fun fetch(urlOrUri: String): Result<SpotifyPlaylist> = withContext(Dispatchers.IO) {
    runCatching {
      val foundUrl = Regex("https?://\\S+").find(urlOrUri)?.value
      var resolvedText = foundUrl ?: urlOrUri.trim()
      // If it's a short link (spotify.link / link.spotify.com), follow redirect
      if (resolvedText.contains("spotify.link", ignoreCase = true)) {
        resolvedText = followRedirect(resolvedText)
      }

      val (type, id) = parseSpotifyRef(resolvedText)
        ?: error("Link do Spotify não reconhecido. Use um link de playlist ou álbum.")

      val embedUrl = "https://open.spotify.com/embed/$type/$id"
      val html = fetchHtml(embedUrl)

      val scriptMatcher = Regex("""<script id="__NEXT_DATA__"[^>]*>(.*?)</script>""", RegexOption.DOT_MATCHES_ALL)
      val jsonMatch = scriptMatcher.find(html)
        ?: error("Não foi possível ler as informações da playlist do Spotify.")

      val json = JSONObject(jsonMatch.groupValues[1])
      val entity = json.optJSONObject("props")
        ?.optJSONObject("pageProps")
        ?.optJSONObject("state")
        ?.optJSONObject("data")
        ?.optJSONObject("entity")
        ?: error("A playlist parece estar vazia ou privada.")

      val title = entity.optString("name").ifBlank { "Playlist do Spotify" }
      val subtitle = entity.optString("subtitle")
      val art = entity.optJSONObject("visualIdentity")
        ?.optJSONArray("image")
        ?.optJSONObject(0)
        ?.optString("url")
        ?: entity.optJSONArray("images")?.optJSONObject(0)?.optString("url").orEmpty()

      val trackListArray = entity.optJSONArray("trackList")
      val tracks = mutableListOf<SpotifyTrack>()
      if (trackListArray != null) {
        for (i in 0 until trackListArray.length()) {
          val t = trackListArray.optJSONObject(i) ?: continue
          val tTitle = t.optString("title")
          if (tTitle.isBlank()) continue
          val tArtist = t.optString("subtitle")
          val tUri = t.optString("uri").ifBlank { "spotify:track:$i" }
          val tDuration = t.optLong("duration", 0L)
          val tExplicit = t.optBoolean("isExplicit", false)
          val tPreview = t.optJSONObject("audioPreview")?.optString("url")
          val track = SpotifyTrack(
            uri = tUri,
            title = tTitle,
            artist = tArtist,
            durationMs = tDuration,
            previewUrl = tPreview,
            isExplicit = tExplicit
          )
          registerTrack(track)
          tracks.add(track)
        }
      }

      require(tracks.isNotEmpty()) { "Nenhuma faixa encontrada nesta playlist ou álbum." }

      SpotifyPlaylist(
        id = id,
        type = type,
        title = title,
        subtitle = subtitle,
        art = art,
        tracks = tracks
      )
    }
  }

  private fun followRedirect(initialUrl: String): String {
    return runCatching {
      val c = URL(initialUrl).openConnection() as HttpURLConnection
      c.instanceFollowRedirects = false
      c.connectTimeout = 8_000
      c.readTimeout = 8_000
      c.setRequestProperty("User-Agent", Engine.DEFAULT_UA)
      c.connect()
      val loc = c.getHeaderField("Location")
      c.disconnect()
      loc?.takeIf { it.isNotBlank() } ?: initialUrl
    }.getOrDefault(initialUrl)
  }

  private fun fetchHtml(url: String): String {
    val conn = URL(url).openConnection() as HttpURLConnection
    conn.connectTimeout = 12_000
    conn.readTimeout = 15_000
    conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
    conn.setRequestProperty("Accept-Language", "pt-BR,pt;q=0.9,en-US;q=0.8,en;q=0.7")
    return try {
      conn.inputStream.bufferedReader().use { it.readText() }
    } finally {
      conn.disconnect()
    }
  }

  /**
   * Searches YouTube Music for the Spotify track to obtain a streamable/downloadable SearchResult.
   */
  suspend fun resolveToSearchResult(track: SpotifyTrack): SearchResult? {
    resolvedCache[track.uri]?.let { return it }
    val bareUri = track.uri.removePrefix("spotify:")
    resolvedCache[bareUri]?.let { return it }
    val q = "${track.title} ${track.artist}".trim()
    val page = MusicSearch.search(SearchQuery(q, SearchKind.Songs))
    val result = page.items.firstOrNull { it.playable }
      ?: MusicSearch.search(SearchQuery(q, SearchKind.Videos)).items.firstOrNull { it.playable }
      ?: run {
        val mainArtist = track.artist.split(",", ";", "&", "feat.", "ft.").firstOrNull()?.trim().orEmpty()
        if (mainArtist.isNotBlank() && mainArtist != track.artist) {
          MusicSearch.search(SearchQuery("${track.title} $mainArtist", SearchKind.Songs)).items.firstOrNull { it.playable }
        } else null
      }
    if (result != null) {
      resolvedCache[track.uri] = result
      if (bareUri != track.uri) resolvedCache[bareUri] = result
    }
    return result
  }

  fun getCachedSearchResult(trackUri: String): SearchResult? = resolvedCache[trackUri]

  /**
   * Checks if this Spotify track already matches an item in the user's local history.
   */
  fun findLocalMatch(track: SpotifyTrack, history: List<HistoryItem>): HistoryItem? {
    fun clean(s: String) = s.lowercase().replace(Regex("[^a-z0-9]"), "")
    val trackTitle = clean(track.title)
    val trackArtist = clean(track.artist)
    return history.firstOrNull { h ->
      val hTitle = clean(h.title)
      val hArtist = clean(h.artist)
      (hTitle == trackTitle || hTitle.contains(trackTitle) || trackTitle.contains(hTitle)) &&
        (trackArtist.isEmpty() || hArtist.contains(trackArtist) || trackArtist.contains(hArtist))
    }
  }
}
