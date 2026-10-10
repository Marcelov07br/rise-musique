package com.rise.musique

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.Calendar

/** One counted play: key (uri), title, artist, ms actually listened, epoch ms when it started. */
data class Play(val key: String, val title: String, val artist: String, val listenedMs: Long, val at: Long) {
  fun json() = JSONObject().put("u", key).put("t", title).put("r", artist).put("ms", listenedMs).put("at", at)
  companion object { fun of(o: JSONObject) = Play(o.getString("u"), o.optString("t"), o.optString("r"), o.optLong("ms").coerceAtLeast(0), o.getLong("at")) }
}

data class Ranked(val name: String, val sub: String, val plays: Int, val ms: Long)
data class Month(val label: String, val plays: Int, val ms: Long)
data class StatsSummary(val totalPlays: Int = 0, val totalMs: Long = 0, val topTracks: List<Ranked> = emptyList(),
                        val topArtists: List<Ranked> = emptyList(), val months: List<Month> = emptyList())

/** Accumulates listening time for the item currently playing; a play counts after 30 s or half the track. */
class PlaySession(val key: String, val title: String, val artist: String, val start: Long) {
  var listenedMs = 0L; private set
  var counted = false; private set
  fun add(deltaMs: Long, durationMs: Long): Boolean {
    listenedMs += deltaMs.coerceIn(0, 5_000)
    if (!counted && listenedMs >= threshold(durationMs)) { counted = true; return true }
    return false
  }
  fun play() = Play(key, title, artist, listenedMs, start)
  companion object { fun threshold(durationMs: Long) = if (durationMs > 0) minOf(30_000L, durationMs / 2) else 30_000L }
}

object PlayStats {
  private const val MAX = 20_000
  private val months = listOf("jan", "fev", "mar", "abr", "mai", "jun", "jul", "ago", "set", "out", "nov", "dez")
  private fun file(c: Context) = File(c.filesDir, "stats.json")

  @Synchronized fun json(c: Context): JSONArray = runCatching { JSONArray(AtomicJson.read(file(c)) ?: "[]") }.getOrDefault(JSONArray())
  fun load(c: Context): List<Play> = parse(json(c))
  fun parse(a: JSONArray): List<Play> = (0 until a.length()).mapNotNull { runCatching { Play.of(a.getJSONObject(it)) }.getOrNull() }

  /** Inserts or updates (same key + start) a play. */
  @Synchronized fun record(c: Context, p: Play) {
    val list = load(c).filterNot { it.key == p.key && it.at == p.at } + p
    AtomicJson.write(file(c), JSONArray(list.takeLast(MAX).map(Play::json)).toString())
  }
  /** Backup import: union by key + start time. Returns how many were added. */
  @Synchronized fun merge(c: Context, incoming: JSONArray): Int {
    val (merged, added) = mergeLists(load(c), parse(incoming))
    AtomicJson.write(file(c), JSONArray(merged.map(Play::json)).toString()); return added
  }
  fun mergeLists(mine: List<Play>, other: List<Play>): Pair<List<Play>, Int> {
    val seen = mine.map { it.key to it.at }.toMutableSet()
    val add = other.filter { seen.add(it.key to it.at) }
    return (mine + add).sortedBy { it.at }.takeLast(MAX) to add.size
  }

  fun summarize(plays: List<Play>, now: Long = System.currentTimeMillis(), top: Int = 5, monthCount: Int = 6): StatsSummary {
    val tracks = plays.groupBy { it.key }.map { (_, ps) -> val last = ps.maxBy { it.at }
      Ranked(last.title.ifBlank { "Sem título" }, last.artist, ps.size, ps.sumOf { it.listenedMs }) }
      .sortedWith(compareByDescending<Ranked> { it.plays }.thenByDescending { it.ms }).take(top)
    val artists = plays.filter { it.artist.isNotBlank() }.groupBy { it.artist.trim().lowercase() }.map { (_, ps) ->
      Ranked(ps.first().artist.trim(), "", ps.size, ps.sumOf { it.listenedMs }) }
      .sortedWith(compareByDescending<Ranked> { it.plays }.thenByDescending { it.ms }).take(top)
    val cal = Calendar.getInstance().apply { timeInMillis = now }
    val keyOf = { t: Long -> Calendar.getInstance().apply { timeInMillis = t }.let { it.get(Calendar.YEAR) * 12 + it.get(Calendar.MONTH) } }
    val byMonth = plays.groupBy { keyOf(it.at) }
    val nowKey = cal.get(Calendar.YEAR) * 12 + cal.get(Calendar.MONTH)
    val ms = (monthCount - 1 downTo 0).map { back -> val k = nowKey - back; val ps = byMonth[k].orEmpty()
      Month("${months[k % 12]}/${(k / 12) % 100}", ps.size, ps.sumOf { it.listenedMs }) }
    return StatsSummary(plays.size, plays.sumOf { it.listenedMs }, tracks, artists, ms)
  }

  fun duration(ms: Long): String { val m = ms / 60_000; val h = m / 60
    return if (h > 0) "${h} h ${m % 60} min" else "$m min" }
}
