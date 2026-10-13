package com.rise.musique

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Persists Spotify playlists saved by the user in the Library.
 */
object SpotifyStore {
  private fun file(c: Context) = File(c.filesDir, "spotify_playlists.json")

  @Synchronized
  fun load(c: Context): List<SpotifyPlaylist> = runCatching {
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

  @Synchronized
  private fun save(c: Context, list: List<SpotifyPlaylist>) {
    runCatching {
      val root = JSONArray()
      for (pl in list) {
        val o = JSONObject()
        o.put("id", pl.id)
        o.put("type", pl.type)
        o.put("title", pl.title)
        o.put("subtitle", pl.subtitle)
        o.put("art", pl.art)
        val tracksArray = JSONArray()
        for (t in pl.tracks) {
          val to = JSONObject()
          to.put("uri", t.uri)
          to.put("title", t.title)
          to.put("artist", t.artist)
          to.put("durationMs", t.durationMs)
          if (!t.previewUrl.isNullOrBlank()) to.put("previewUrl", t.previewUrl)
          to.put("isExplicit", t.isExplicit)
          tracksArray.put(to)
        }
        o.put("tracks", tracksArray)
        root.put(o)
      }
      val f = file(c)
      val tmp = File(c.filesDir, "spotify_playlists.json.tmp")
      tmp.writeText(root.toString())
      if (tmp.renameTo(f)) {
        // successfully saved
      } else {
        f.writeText(root.toString())
        tmp.delete()
      }
    }
  }

  @Synchronized
  fun savePlaylist(c: Context, pl: SpotifyPlaylist) {
    val current = load(c).filter { it.id != pl.id }.toMutableList()
    current.add(0, pl) // Add to top of saved playlists
    save(c, current)
  }

  @Synchronized
  fun removePlaylist(c: Context, id: String) {
    val current = load(c).filter { it.id != id }
    save(c, current)
  }

  fun isSaved(c: Context, id: String): Boolean = load(c).any { it.id == id }
}
