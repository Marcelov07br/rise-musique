package com.rise.musique

import com.rise.musique.ui.HistoryItem
import org.json.JSONObject

object TrackJson {
  fun encode(h: HistoryItem) = JSONObject().put("t", h.title).put("m", h.meta).put("u", h.uri)
    .put("a", h.art).put("r", h.artist).put("duration", h.duration).put("album", h.album)
    .put("imported", h.imported).put("video", h.video)
  fun decode(o: JSONObject) = HistoryItem(o.getString("t"), o.getString("m"), o.optString("u"), o.optString("a"), o.optString("r"),
    o.optLong("duration").coerceAtLeast(0), o.optString("album"), o.optBoolean("imported"),
    o.optBoolean("video", o.getString("m").substringBefore(" ·").uppercase() in setOf("MP4", "MKV", "WEBM")))
}
