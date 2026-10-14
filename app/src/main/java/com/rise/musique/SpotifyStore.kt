package com.rise.musique

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Spotify playlists saved by the user. Since 0.23 they live in the unified [Library]; the old
 * `spotify_playlists.json` is only read once by the migration ([loadLegacy]).
 */
object SpotifyStore {
  private fun file(c: Context) = File(c.filesDir, "spotify_playlists.json")

  @Synchronized
  fun loadLegacy(c: Context): List<SpotifyPlaylist> = runCatching {
    val f = file(c)
    if (!f.exists()) return emptyList()
    val text = f.readText()
    if (text.isBlank()) return emptyList()
    val root = JSONArray(text)
    val list = mutableListOf<SpotifyPlaylist>()
    for (i in 0 until root.length()) {
      val o = root.optJSONObject(i) ?: continue
      val id = o.optString("id")
      if (id.isBlank()) continue
      val type = o.optString("type", "playlist")
      val title = o.optString("title")
      val subtitle = o.optString("subtitle")
      val art = o.optString("art")
      val tracksArray = o.optJSONArray("tracks") ?: JSONArray()
      val tracks = mutableListOf<SpotifyTrack>()
      for (j in 0 until tracksArray.length()) {
        val t = tracksArray.optJSONObject(j) ?: continue
        tracks.add(
          SpotifyTrack(
            uri = t.optString("uri"),
            title = t.optString("title"),
            artist = t.optString("artist"),
            durationMs = t.optLong("durationMs", 0L),
            previewUrl = t.optString("previewUrl").ifBlank { null },
            isExplicit = t.optBoolean("isExplicit", false)
          )
        )
      }
      list.add(SpotifyPlaylist(id, type, title, subtitle, art, tracks))
    }
    list
  }.getOrDefault(emptyList())

  fun load(c: Context): List<SpotifyPlaylist> = Library.spotifyPlaylists(c)
  fun savePlaylist(c: Context, pl: SpotifyPlaylist) = Library.saveSpotify(c, pl)
  fun removePlaylist(c: Context, id: String) = Library.removeSpotify(c, id)
  fun isSaved(c: Context, id: String): Boolean = load(c).any { it.id == id }
}
