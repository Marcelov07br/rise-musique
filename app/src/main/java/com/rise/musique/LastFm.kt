package com.rise.musique

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.MessageDigest

/** Last.fm rules: track longer than 30 s, played for half its length or 4 minutes, whichever comes first. */
object ScrobbleRules {
  fun threshold(durationMs: Long): Long? = if (durationMs <= 30_000) null else minOf(durationMs / 2, 240_000L)
  fun eligible(durationMs: Long, listenedMs: Long) = threshold(durationMs)?.let { listenedMs >= it } ?: false
}

data class Scrobble(val artist: String, val track: String, val album: String, val timestampSec: Long, val durationSec: Long) {
  fun json() = JSONObject().put("a", artist).put("t", track).put("al", album).put("ts", timestampSec).put("d", durationSec)
  companion object { fun of(o: JSONObject) = Scrobble(o.getString("a"), o.getString("t"), o.optString("al"), o.getLong("ts"), o.optLong("d")) }
}

/**
 * Minimal Last.fm client (https://www.last.fm/api): auth.getMobileSession with the user's login (the password is only
 * sent once to obtain a session key and never stored), track.updateNowPlaying and batched track.scrobble with an
 * offline queue. Keys come from BuildConfig (local secrets.properties).
 */
object LastFm {
  private const val API = "https://ws.audioscrobbler.com/2.0/"
  val configured get() = BuildConfig.LASTFM_API_KEY.isNotBlank() && BuildConfig.LASTFM_API_SECRET.isNotBlank()

  /** api_sig: md5 of params sorted by name, concatenated name+value, followed by the secret (format/callback excluded). */
  fun signature(params: Map<String, String>, secret: String): String {
    val base = params.filterKeys { it != "format" && it != "callback" }.toSortedMap().entries.joinToString("") { it.key + it.value } + secret
    return MessageDigest.getInstance("MD5").digest(base.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
  }
  fun scrobbleParams(batch: List<Scrobble>): Map<String, String> = buildMap {
    put("method", "track.scrobble")
    batch.forEachIndexed { i, s -> put("artist[$i]", s.artist); put("track[$i]", s.track); put("timestamp[$i]", s.timestampSec.toString())
      if (s.album.isNotBlank()) put("album[$i]", s.album); if (s.durationSec > 0) put("duration[$i]", s.durationSec.toString()) }
  }

  private fun prefs(c: Context) = c.getSharedPreferences("lastfm", 0)
  fun user(c: Context): String? = prefs(c).getString("user", null)?.takeIf { prefs(c).contains("sk") }
  fun enabled(c: Context) = configured && user(c) != null && prefs(c).getBoolean("on", true)
  fun setEnabled(c: Context, on: Boolean) = prefs(c).edit().putBoolean("on", on).apply()
  fun logout(c: Context) = prefs(c).edit().clear().apply()

  private fun call(params: Map<String, String>): JSONObject {
    val all = params + ("api_key" to BuildConfig.LASTFM_API_KEY)
    val signed = all + ("api_sig" to signature(all, BuildConfig.LASTFM_API_SECRET)) + ("format" to "json")
    val body = signed.entries.joinToString("&") { URLEncoder.encode(it.key, "UTF-8") + "=" + URLEncoder.encode(it.value, "UTF-8") }
    val c = URL(API).openConnection() as HttpURLConnection
    try {
      c.requestMethod = "POST"; c.doOutput = true; c.connectTimeout = 15_000; c.readTimeout = 15_000
      c.setRequestProperty("Content-Type", "application/x-www-form-urlencoded"); c.setRequestProperty("User-Agent", "RiseMusique/${BuildConfig.VERSION_NAME}")
      c.outputStream.use { it.write(body.toByteArray()) }
      val text = (if (c.responseCode in 200..299) c.inputStream else c.errorStream)?.use { it.readBytes().decodeToString() } ?: "{}"
      val o = JSONObject(text)
      if (o.has("error")) throw LastFmError(o.optInt("error"), o.optString("message"))
      return o
    } finally { c.disconnect() }
  }
  class LastFmError(val code: Int, msg: String) : Exception(msg)

  /** Blocking. Returns the user name on success. */
  fun login(c: Context, user: String, password: String): String {
    require(configured) { "Last.fm não configurado nesta versão" }
    val o = call(mapOf("method" to "auth.getMobileSession", "username" to user.trim(), "password" to password))
    val s = o.getJSONObject("session")
    prefs(c).edit().putString("sk", s.getString("key")).putString("user", s.getString("name")).putBoolean("on", true).apply()
    return s.getString("name")
  }

  fun nowPlaying(c: Context, artist: String, track: String, album: String, durationSec: Long) {
    if (!enabled(c) || artist.isBlank()) return
    runCatching { call(buildMap { put("method", "track.updateNowPlaying"); put("artist", artist); put("track", track)
      if (album.isNotBlank()) put("album", album); if (durationSec > 0) put("duration", durationSec.toString())
      put("sk", prefs(c).getString("sk", "")!!) }) }
  }

  private fun queueFile(c: Context) = File(c.filesDir, "scrobbles.json")
  @Synchronized fun enqueue(c: Context, s: Scrobble) {
    if (!enabled(c) || s.artist.isBlank()) return
    val q = queue(c) + s
    AtomicJson.write(queueFile(c), JSONArray(q.takeLast(2000).map(Scrobble::json)).toString())
  }
  @Synchronized fun queue(c: Context): List<Scrobble> = runCatching {
    val a = JSONArray(AtomicJson.read(queueFile(c)) ?: "[]"); (0 until a.length()).map { Scrobble.of(a.getJSONObject(it)) } }.getOrDefault(emptyList())
  /** Sends queued scrobbles in batches of 50; keeps them on network errors, drops them on invalid-session (9). */
  @Synchronized fun flush(c: Context) {
    if (!enabled(c)) return
    var q = queue(c)
    while (q.isNotEmpty()) {
      val batch = q.take(50)
      try { call(scrobbleParams(batch) + ("sk" to prefs(c).getString("sk", "")!!)) }
      catch (e: LastFmError) { if (e.code == 9) logout(c); return }
      catch (_: Exception) { return }
      q = q.drop(batch.size); AtomicJson.write(queueFile(c), JSONArray(q.map(Scrobble::json)).toString())
    }
  }
}
