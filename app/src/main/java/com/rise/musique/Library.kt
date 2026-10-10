package com.rise.musique
import android.content.Context
import android.net.Uri
import org.json.*
import java.io.File

/** Favoritos e playlists próprias, guardados por uri do arquivo. */
object Library {
  private fun file(c: Context) = File(c.filesDir, "library.json")
  @Synchronized fun load(c: Context): JSONObject = runCatching { JSONObject(file(c).readText()) }.getOrDefault(JSONObject())
  @Synchronized private fun save(c: Context, o: JSONObject) = file(c).writeText(o.toString())

  fun favorites(c: Context): Set<String> = load(c).optJSONArray("fav")?.let { a -> (0 until a.length()).map(a::getString).toSet() } ?: emptySet()
  fun toggleFavorite(c: Context, uri: String) { val o = load(c); val s = favorites(c).toMutableSet()
    if (!s.add(uri)) s.remove(uri); o.put("fav", JSONArray(s.toList())); save(c, o) }

  fun playlists(c: Context): Map<String, List<String>> = load(c).optJSONObject("pl")?.let { pl ->
    pl.keys().asSequence().associateWith { k -> pl.getJSONArray(k).let { a -> (0 until a.length()).map(a::getString) } } } ?: emptyMap()
  fun addToPlaylist(c: Context, name: String, uri: String) { val o = load(c); val pl = o.optJSONObject("pl") ?: JSONObject()
    val a = pl.optJSONArray(name) ?: JSONArray(); if ((0 until a.length()).none { a.getString(it) == uri }) a.put(uri)
    pl.put(name, a); o.put("pl", pl); save(c, o) }
  fun removeFromPlaylist(c: Context, name: String, uri: String) { val o = load(c); val pl = o.optJSONObject("pl") ?: return
    val a = pl.optJSONArray(name) ?: return; pl.put(name, JSONArray((0 until a.length()).map(a::getString).filter { it != uri })); save(c, o) }
  fun deletePlaylist(c: Context, name: String) { val o = load(c); o.optJSONObject("pl")?.remove(name); save(c, o) }

  /** Backup: histórico + biblioteca + preferências num JSON só. */
  fun export(c: Context, to: Uri) {
    val prefs = c.getSharedPreferences("rise", 0).all.filterValues { it is String || it is Boolean || it is Int }
    val o = JSONObject().put("app", "RiseMusique").put("v", 1)
      .put("history", runCatching { JSONArray(File(c.filesDir, "history.json").readText()) }.getOrDefault(JSONArray()))
      .put("library", load(c)).put("prefs", JSONObject(prefs))
    c.contentResolver.openOutputStream(to, "wt")!!.use { it.write(o.toString(2).toByteArray()) }
  }
  fun import(c: Context, from: Uri): Int {
    val o = JSONObject(c.contentResolver.openInputStream(from)!!.use { it.readBytes().decodeToString() })
    require(o.optString("app") == "RiseMusique") { "Esse arquivo não é um backup do Rise Musique" }
    val incoming = o.getJSONArray("history"); val current = runCatching { JSONArray(File(c.filesDir, "history.json").readText()) }.getOrDefault(JSONArray())
    val seen = (0 until current.length()).map { current.getJSONObject(it).optString("u") + current.getJSONObject(it).optString("t") }.toMutableSet()
    var added = 0
    for (i in 0 until incoming.length()) { val it = incoming.getJSONObject(i); if (seen.add(it.optString("u") + it.optString("t"))) { current.put(it); added++ } }
    File(c.filesDir, "history.json").writeText(current.toString())
    o.optJSONObject("library")?.let { lib -> val mine = load(c)
      val fav = (favorites(c) + (lib.optJSONArray("fav")?.let { a -> (0 until a.length()).map(a::getString) } ?: emptyList())).toList()
      mine.put("fav", JSONArray(fav))
      val pl = mine.optJSONObject("pl") ?: JSONObject(); lib.optJSONObject("pl")?.let { inPl -> inPl.keys().forEach { k -> if (!pl.has(k)) pl.put(k, inPl.get(k)) } }
      mine.put("pl", pl); save(c, mine) }
    o.optJSONObject("prefs")?.let { pr -> val e = c.getSharedPreferences("rise", 0).edit()
      pr.keys().forEach { k -> when (val v = pr.get(k)) { is String -> e.putString(k, v); is Boolean -> e.putBoolean(k, v); is Int -> e.putInt(k, v) } }; e.apply() }
    return added
  }
}
