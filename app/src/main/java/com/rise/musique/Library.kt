package com.rise.musique
import android.content.Context
import android.net.Uri
import com.rise.musique.ui.HistoryItem
import org.json.*
import java.io.File

/**
 * The local library (schema 2, see [LibraryData]): likes, playlists, followed artists, saved albums and the tracks they point to.
 * One file (`library.json`), migrated on first read; everything else in the app reads it through here.
 */
object Library {
  private fun file(c: Context) = File(c.filesDir, "library.json")
  private var cache: LibraryData? = null
  /** Tests: forget the in-memory copy. */
  @Synchronized internal fun resetCache() { cache = null }
  private fun now() = System.currentTimeMillis()

  @Synchronized fun data(c: Context): LibraryData = cache ?: run {
    val raw = JSONObject(AtomicJson.read(file(c)) ?: "{}")
    val needs = LibraryMigrations.version(raw) < LibrarySchema.CURRENT
    val d = LibraryMigrations.migrate(raw, if (needs) legacyInputs(c) else LegacyInputs())
    cache = d
    if (needs) { File(c.filesDir, "library.v1.json").takeIf { !it.exists() }?.writeText(raw.toString()); write(c, d) }
    d
  }
  /** What ≤0.22 kept outside library.json (left in place as a fallback; no longer read after the migration). */
  private fun legacyInputs(c: Context) = LegacyInputs(HistoryStore.load(c),
    c.getSharedPreferences("follow", 0).getStringSet("artists", emptySet())!!.toSet(), SpotifyStore.loadLegacy(c),
    runCatching { JSONObject(c.getSharedPreferences("rise", 0).getString("artistPhotos", "{}")!!).let { o -> o.keys().asSequence().associateWith(o::optString) } }.getOrDefault(emptyMap()))
  /** Notified after every write (the ViewModel mirrors the library into its state). */
  @Volatile var listener: ((LibraryData) -> Unit)? = null
  private fun write(c: Context, d: LibraryData) { AtomicJson.write(file(c), LibraryCodec.encode(d).toString()); cache = d; listener?.invoke(d) }
  @Synchronized private fun update(c: Context, f: (LibraryData) -> LibraryData): LibraryData = f(data(c)).also { write(c, it) }

  // ---- Views used by the UI (keys are track keys, see LibTrack) ----
  fun favorites(c: Context): Set<String> = data(c).likedKeys
  fun playlists(c: Context): Map<String, List<String>> = data(c).playlists.associate { it.name to it.tracks }
  fun isLiked(c: Context, mediaId: String) = keyFor(mediaId) in favorites(c)

  /** Player media id / search url → library key. */
  fun keyFor(mediaId: String): String = when {
    mediaId.startsWith("stream:") -> LibraryMigrations.normalizeKey(mediaId)
    mediaId.startsWith("spotify:") -> "rise-spotify://track/" + mediaId.substringAfterLast(':')
    else -> mediaId }

  // ---- Tracks ----
  fun remember(c: Context, t: LibTrack) { update(c) { LibraryOps.upsertTrack(it, t, now()) } }
  /** Downloaded or imported file; [videoId] when it came from YouTube. */
  fun rememberFile(c: Context, h: HistoryItem, videoId: String = "") = remember(c, LibraryMigrations.trackOf(h, now(), videoId))
  fun streamTrack(r: com.rise.musique.ui.SearchResult): LibTrack? { val id = Engine.videoId(r.url) ?: return null
    return LibTrack("rise-stream://$id", r.title, r.channel, r.album, r.duration * 1000L, r.thumb, id, LibSource.YOUTUBE, mapOf("youtube" to id)) }
  fun trackFor(h: HistoryItem): LibTrack = LibraryMigrations.streamId(h.uri)?.let { id ->
    LibTrack(h.uri, h.title, h.artist, h.album, h.duration, h.art, id, LibSource.YOUTUBE, mapOf("youtube" to id)) } ?: LibraryMigrations.trackOf(h, now())

  // ---- Likes ----
  @Synchronized fun toggleFavorite(c: Context, key: String, meta: LibTrack? = null) { val k = keyFor(key)
    update(c) { d -> LibraryOps.toggleLike(if (meta != null || k !in d.tracks) LibraryOps.upsertTrack(d, meta?.copy(key = k) ?: LibraryMigrations.stubTrack(k, now()), now()) else d, k, now()) } }

  // ---- Playlists ----
  @Synchronized fun createPlaylist(c: Context, name: String) { update(c) { LibraryOps.createPlaylist(it, name, now()) } }
  @Synchronized fun addToPlaylist(c: Context, name: String, key: String, meta: LibTrack? = null) { update(c) { d ->
    val withTrack = if (meta != null || key !in d.tracks) LibraryOps.upsertTrack(d, meta ?: LibraryMigrations.stubTrack(key, now()), now()) else d
    LibraryOps.add(if (withTrack.playlist(name) == null) LibraryOps.createPlaylist(withTrack, name, now()) else withTrack, name, listOf(key), now()) } }
  @Synchronized fun removeFromPlaylist(c: Context, name: String, key: String) { update(c) { LibraryOps.remove(it, name, key, now()) } }
  @Synchronized fun deletePlaylist(c: Context, name: String) { update(c) { LibraryOps.delete(it, name, now()) } }
  @Synchronized fun renamePlaylist(c: Context, old: String, name: String) { update(c) { LibraryOps.rename(it, old, name, now()) } }
  @Synchronized fun moveTrack(c: Context, name: String, from: Int, to: Int) { update(c) { LibraryOps.move(it, name, from, to, now()) } }
  @Synchronized fun setCover(c: Context, name: String, cover: String) { update(c) { LibraryOps.setCover(it, name, cover, now()) } }
  @Synchronized fun movePlaylist(c: Context, from: Int, to: Int) { update(c) { LibraryOps.reorderPlaylists(it, from, to, now()) } }
  @Synchronized fun removeTrack(c: Context, key: String) { update(c) { LibraryOps.forget(it, key, now()) } }

  // ---- Artists / albums ----
  fun followed(c: Context): Set<String> = data(c).artists.map { it.name }.toSet()
  @Synchronized fun toggleArtist(c: Context, name: String, art: String = ""): Boolean {
    var on = false; update(c) { d -> LibraryOps.toggleArtist(d, LibArtist(name, art = art), now()).also { on = it.second }.first }; return on }
  @Synchronized fun toggleAlbum(c: Context, a: LibAlbum): Boolean {
    var on = false; update(c) { d -> LibraryOps.toggleAlbum(d, a, now()).also { on = it.second }.first }; return on }

  // ---- Spotify imports (written into the same model) ----
  @Synchronized fun saveSpotify(c: Context, sp: SpotifyPlaylist) { update(c) { d -> val t = now()
    var nd = d; val keys = sp.tracks.map { tr -> LibraryMigrations.spotifyTrack(tr, t).also { nd = LibraryOps.upsertTrack(nd, it, t) }.key }
    val existing = nd.playlists.firstOrNull { it.id == "sp-${sp.id}" }
    val pl = LibraryMigrations.spotifyPlaylist(sp, keys, t, nd.playlists.filter { it !== existing }.map { it.name }.toSet())
    nd.copy(playlists = if (existing != null) nd.playlists.map { if (it === existing) pl.copy(name = existing.name, createdAt = existing.createdAt) else it } else listOf(pl) + nd.playlists, updatedAt = t) } }
  @Synchronized fun removeSpotify(c: Context, id: String) { update(c) { d -> d.copy(playlists = d.playlists.filterNot { it.id == "sp-$id" }, updatedAt = now()) } }
  /** Saved Spotify playlists, rebuilt from the library for the existing Spotify screen. */
  fun spotifyPlaylists(c: Context): List<SpotifyPlaylist> = data(c).let { d -> d.playlists.filter { it.source == LibSource.SPOTIFY }.map { p ->
    SpotifyPlaylist(p.id.removePrefix("sp-"), p.externalIds["spotifyType"] ?: "playlist", p.name, p.description, p.cover,
      p.tracks.mapNotNull { k -> d.tracks[k]?.let { t -> SpotifyTrack(t.externalIds["spotify"] ?: ("spotify:track:" + k.substringAfterLast('/')), t.title, t.artist, t.durationMs) } }) } }

  // ---- Library file (versioned JSON, SAF) ----
  @Synchronized fun exportLibrary(c: Context, to: Uri) {
    val text = LibraryFile.encode(data(c), PlayStats.json(c))
    c.contentResolver.openOutputStream(to, "wt")!!.use { it.write(text.toByteArray()) }
  }
  @Synchronized fun importLibrary(c: Context, from: Uri): MergeReport {
    val parsed = LibraryFile.decode(readCapped(c, from).decodeToString())
    val (merged, report) = LibraryMerge.merge(data(c), parsed.library, now())
    write(c, merged); PlayStats.merge(c, parsed.history)
    return report
  }
  private fun readCapped(c: Context, from: Uri): ByteArray = c.contentResolver.openInputStream(from)!!.use { input ->
    val out = java.io.ByteArrayOutputStream(); val buffer = ByteArray(8192)
    while (true) { val n = input.read(buffer); if (n < 0) break
      require(out.size() + n <= 20 * 1024 * 1024) { "Arquivo muito grande" }; out.write(buffer, 0, n) }
    out.toByteArray() }

  /** Backup: histórico + biblioteca + preferências num JSON só. */
  @Synchronized fun export(c: Context, to: Uri) {
    val prefs = c.getSharedPreferences("rise", 0).all.filterKeys { it != "tree" }.filterValues { it is String || it is Boolean || it is Int }
    val o = JSONObject().put("app", "RiseMusique").put("v", 3).put("package", c.packageName).put("includesAudioFiles", false)
      .put("history", HistoryStore.json(c))
      .put("library", LibraryCodec.encode(data(c))).put("prefs", JSONObject(prefs)).put("stats", PlayStats.json(c))
    c.contentResolver.openOutputStream(to, "wt")!!.use { it.write(o.toString(2).toByteArray()) }
  }
  @Synchronized fun import(c: Context, from: Uri): Int {
    val o = JSONObject(readCapped(c, from).decodeToString())
    if (o.optString("format") == LibraryFile.FORMAT) return importLibrary(c, from).total
    require(o.optString("app") == "RiseMusique") { "Esse arquivo não é um backup do Rise Musique" }
    require(o.optInt("v") in 1..3) { "Versão de backup incompatível" }
    val incoming = o.getJSONArray("history")
    // Validate before writing anything. Old backups remain supported.
    for (i in 0 until incoming.length()) { incoming.getJSONObject(i).apply { getString("t"); getString("m"); getString("u") } }
    val lib = o.optJSONObject("library")?.let { LibraryMigrations.migrate(it, LegacyInputs(List(incoming.length()) { TrackJson.decode(incoming.getJSONObject(it)) })) }
    val added = HistoryStore.merge(c, incoming)
    o.optJSONArray("stats")?.let { PlayStats.merge(c, it) }
    if (lib != null) write(c, LibraryMerge.merge(data(c), lib, now()).first)
    o.optJSONObject("prefs")?.let { pr -> val e = c.getSharedPreferences("rise", 0).edit()
      pr.keys().forEach { k -> if (k in setOf("mode", "format", "quality_Musica", "quality_Video")) {
        val value = pr.optString(k)
        val valid = when (k) { "mode" -> value in setOf("Musica", "Video"); "format" -> value in setOf("MP3", "M4A", "Opus", "FLAC")
          "quality_Musica" -> value in setOf("128k", "192k", "320k"); else -> value in setOf("480p", "720p", "1080p", "Máx") }
        if (valid) e.putString(k, value)
      }
      if (k == "crossfade") pr.optInt(k, -1).takeIf { it in CrossfadeRules.OPTIONS }?.let { e.putInt(k, it) }
      }; e.apply() }
    return added
  }
}
