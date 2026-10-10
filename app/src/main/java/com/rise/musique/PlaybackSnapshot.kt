package com.rise.musique

import com.rise.musique.ui.HistoryItem
import org.json.JSONArray
import org.json.JSONObject

data class PlaybackSnapshot(val tracks: List<HistoryItem>, val index: Int, val position: Long, val repeat: Int, val shuffle: Boolean) {
  fun json() = JSONObject().put("tracks", JSONArray(tracks.map(TrackJson::encode))).put("index", index)
    .put("position", position).put("repeat", repeat).put("shuffle", shuffle).toString()
  companion object {
    fun parse(text: String): PlaybackSnapshot {
      val o = JSONObject(text); val list = o.getJSONArray("tracks")
      require(list.length() in 1..5000) { "Fila inválida" }
      val tracks = List(list.length()) { TrackJson.decode(list.getJSONObject(it)) }
      require(tracks.none { it.uri.isBlank() || it.uri.startsWith("preview:") || it.uri.startsWith("http") }) { "Somente arquivos locais podem ser restaurados" }
      return PlaybackSnapshot(tracks, o.optInt("index").coerceIn(tracks.indices), o.optLong("position").coerceAtLeast(0),
        o.optInt("repeat").takeIf { it in 0..2 } ?: 0, o.optBoolean("shuffle"))
    }
  }
}
