package com.rise.musique
import android.content.Context
import android.net.Uri
import org.json.*
import java.io.File

/** Favoritos e playlists próprias, guardados por uri do arquivo. */
object Library {
  private fun file(c: Context) = File(c.filesDir, "library.json")
  @Synchronized fun load(c: Context): JSONObject = JSONObject(AtomicJson.read(file(c)) ?: "{}")
  @Synchronized private fun save(c: Context, o: JSONObject) = AtomicJson.write(file(c), o.toString())

  fun favorites(c: Context): Set<String> = load(c).optJSONArray("fav")?.let { a -> (0 until a.length()).map(a::getString).toSet() } ?: emptySet()
  @Synchronized fun toggleFavorite(c: Context, uri: String) { val o = load(c); val s = favorites(c).toMutableSet()
    if (!s.add(uri)) s.remove(uri); o.put("fav", JSONArray(s.toList())); save(c, o) }

  fun playlists(c: Context): Map<String, List<String>> = load(c).optJSONObject("pl")?.let { pl ->
    pl.keys().asSequence().associateWith { k -> pl.getJSONArray(k).let { a -> (0 until a.length()).map(a::getString) } } } ?: emptyMap()
  @Synchronized fun addToPlaylist(c: Context, name: String, uri: String) {
    require(name.isNotBlank() && name !in setOf("Tudo", "Favoritos")) { "Escolha outro nome para a playlist" }
    val o = load(c); val pl = o.optJSONObject("pl") ?: JSONObject()
    val a = pl.optJSONArray(name) ?: JSONArray(); if ((0 until a.length()).none { a.getString(it) == uri }) a.put(uri)
    pl.put(name, a); o.put("pl", pl); save(c, o) }
  @Synchronized fun removeFromPlaylist(c: Context, name: String, uri: String) { val o = load(c); val pl = o.optJSONObject("pl") ?: return
    val a = pl.optJSONArray(name) ?: return; pl.put(name, JSONArray((0 until a.length()).map(a::getString).filter { it != uri })); save(c, o) }
  @Synchronized fun deletePlaylist(c: Context, name: String) { val o = load(c); o.optJSONObject("pl")?.remove(name); save(c, o) }
  @Synchronized fun renamePlaylist(c: Context, old: String, name: String) {
    val o = load(c); val pl = o.optJSONObject("pl") ?: error("Playlist não encontrada")
    PlaylistEdits.rename(pl, old, name); save(c, o)
  }
  @Synchronized fun moveTrack(c: Context, name: String, from: Int, to: Int) {
    val o = load(c); val pl = o.optJSONObject("pl") ?: return
    val tracks = playlists(c)[name] ?: return
    pl.put(name, JSONArray(PlaylistEdits.move(tracks, from, to))); save(c, o)
  }
  @Synchronized fun removeTrack(c: Context, uri: String) {
    val o = load(c); o.put("fav", JSONArray(favorites(c).filterNot { it == uri }))
    val pl = JSONObject(); playlists(c).forEach { (name, tracks) -> pl.put(name, JSONArray(tracks.filterNot { it == uri })) }
    o.put("pl", pl); save(c, o)
  }

  /** Backup: histórico + biblioteca + preferências num JSON só. */
  @Synchronized fun export(c: Context, to: Uri) {
    val prefs = c.getSharedPreferences("rise", 0).all.filterKeys { it != "tree" }.filterValues { it is String || it is Boolean || it is Int }
    val o = JSONObject().put("app", "RiseMusique").put("v", 2).put("package", c.packageName).put("includesAudioFiles", false)
      .put("history", HistoryStore.json(c))
      .put("library", load(c)).put("prefs", JSONObject(prefs))
    c.contentResolver.openOutputStream(to, "wt")!!.use { it.write(o.toString(2).toByteArray()) }
  }
  @Synchronized fun import(c: Context, from: Uri): Int {
    val bytes = c.contentResolver.openInputStream(from)!!.use { input ->
      val out = java.io.ByteArrayOutputStream(); val buffer = ByteArray(8192)
      while (true) { val n = input.read(buffer); if (n < 0) break
        require(out.size() + n <= 10 * 1024 * 1024) { "Backup muito grande" }; out.write(buffer, 0, n) }
      out.toByteArray()
    }
    val o = JSONObject(bytes.decodeToString())
    require(o.optString("app") == "RiseMusique") { "Esse arquivo não é um backup do Rise Musique" }
    require(o.optInt("v") in 1..2) { "Versão de backup incompatível" }
    val incoming = o.getJSONArray("history")
    // Validate before writing anything. Old backups remain supported.
    for (i in 0 until incoming.length()) { incoming.getJSONObject(i).apply { getString("t"); getString("m"); getString("u") } }
    val mine = load(c)
    o.optJSONObject("library")?.let { lib ->
      val fav = (favorites(c) + (lib.optJSONArray("fav")?.let { a -> (0 until a.length()).map(a::getString) } ?: emptyList())).toList()
      mine.put("fav", JSONArray(fav))
      val pl = mine.optJSONObject("pl") ?: JSONObject(); lib.optJSONObject("pl")?.let { inPl -> inPl.keys().forEach { k ->
        val old = pl.optJSONArray(k) ?: JSONArray(); val fresh = inPl.getJSONArray(k)
        require(k.isNotBlank() && k !in setOf("Tudo", "Favoritos")) { "Nome de playlist inválido no backup" }
        pl.put(k, BackupMerge.tracks(old, fresh))
      } }
      mine.put("pl", pl)
    }
    val added = HistoryStore.merge(c, incoming)
    save(c, mine)
    o.optJSONObject("prefs")?.let { pr -> val e = c.getSharedPreferences("rise", 0).edit()
      pr.keys().forEach { k -> if (k in setOf("mode", "format", "quality_Musica", "quality_Video")) {
        val value = pr.optString(k)
        val valid = when (k) { "mode" -> value in setOf("Musica", "Video"); "format" -> value in setOf("MP3", "M4A", "Opus", "FLAC")
          "quality_Musica" -> value in setOf("128k", "192k", "320k"); else -> value in setOf("480p", "720p", "1080p", "Máx") }
        if (valid) e.putString(k, value)
      } }; e.apply() }
    return added
  }
}

