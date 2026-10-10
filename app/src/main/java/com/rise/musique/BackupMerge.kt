package com.rise.musique

import org.json.JSONArray
import org.json.JSONObject

object BackupMerge {
  /** Preserve all records; pairs avoid ambiguous concatenated IDs. */
  fun history(current: JSONArray, incoming: JSONArray): Pair<JSONArray, Int> {
    val result = JSONArray(current.toString())
    val seen = (0 until result.length()).map { result.getJSONObject(it).let { o -> o.getString("u") to o.getString("t") } }.toMutableSet()
    var added = 0
    for (i in 0 until incoming.length()) {
      val item = incoming.getJSONObject(i)
      item.getString("m")
      if (seen.add(item.getString("u") to item.getString("t"))) { result.put(JSONObject(item.toString())); added++ }
    }
    return result to added
  }
  fun tracks(current: JSONArray, incoming: JSONArray): JSONArray = JSONArray(
    ((0 until current.length()).map(current::getString) + (0 until incoming.length()).map(incoming::getString)).distinct())
}
