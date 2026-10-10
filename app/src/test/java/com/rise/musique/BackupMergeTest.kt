package com.rise.musique

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class BackupMergeTest {
  private fun item(uri: String, title: String) = JSONObject().put("u", uri).put("t", title).put("m", "MP3")
  @Test fun keepsMoreThanFiftyTracks() {
    val old = JSONArray(); repeat(75) { old.put(item("u$it", "Faixa $it")) }
    val (merged, count) = BackupMerge.history(old, JSONArray().put(item("u76", "Nova")))
    assertEquals(76, merged.length()); assertEquals(1, count); assertEquals("u0", merged.getJSONObject(0).getString("u"))
  }
  @Test fun repeatedImportDoesNotDuplicateTracks() {
    val incoming = JSONArray().put(item("u1", "Uma"))
    val once = BackupMerge.history(JSONArray(), incoming).first
    val twice = BackupMerge.history(once, incoming)
    assertEquals(1, twice.first.length()); assertEquals(0, twice.second)
  }
  @Test fun identitiesCannotCollideThroughConcatenation() {
    val (merged, count) = BackupMerge.history(JSONArray().put(item("ab", "c")), JSONArray().put(item("a", "bc")))
    assertEquals(2, merged.length()); assertEquals(1, count)
  }
  @Test fun combinesPlaylistTracksInOrder() {
    assertEquals("[\"a\",\"b\",\"c\"]", BackupMerge.tracks(JSONArray("[\"a\",\"b\"]"), JSONArray("[\"b\",\"c\"]")).toString())
  }
  @Test fun malformedIncomingHistoryDoesNotMutateCurrent() {
    val current = JSONArray().put(item("u1", "Uma"))
    try { BackupMerge.history(current, JSONArray().put(JSONObject().put("t", "Inválida"))); fail("Should reject malformed input") }
    catch (_: org.json.JSONException) { assertEquals(1, current.length()) }
  }
}
