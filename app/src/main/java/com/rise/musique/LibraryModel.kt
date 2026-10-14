package com.rise.musique

import org.json.JSONArray
import org.json.JSONObject

/**
 * Unified local library (0.23): the single source of truth for likes, playlists, followed artists and saved albums.
 * Stored in `filesDir/library.json` (schema [LibrarySchema.CURRENT]); play history stays in `stats.json` ([PlayStats]).
 * Pure Kotlin + org.json so migrations, merge and the export format are unit-tested on the JVM.
 */
enum class LibSource { LOCAL, YOUTUBE, SPOTIFY;
  val id get() = name.lowercase()
  companion object { fun of(s: String?) = entries.firstOrNull { it.id == s?.lowercase() } ?: LOCAL } }

/**
 * A track. [key] is how the rest of the app refers to it: the file path/content uri for downloaded/imported files,
 * `rise-stream://<videoId>` for YouTube streams, `rise-spotify://track/<id>` for Spotify items not resolved yet.
 * [videoId] lets any of them play from YouTube; [externalIds] keeps provenance ids (`youtube`, `spotify`, `ytmAlbum`…).
 */
data class LibTrack(val key: String, val title: String, val artist: String = "", val album: String = "", val durationMs: Long = 0,
                    val art: String = "", val videoId: String = "", val source: LibSource = LibSource.LOCAL,
                    val externalIds: Map<String, String> = emptyMap(), val addedAt: Long = 0, val updatedAt: Long = 0) {
  /** Identity for dedupe: same YouTube video = same track, whatever the key. */
  val identity get() = if (videoId.isNotBlank()) "yt:$videoId" else key
}

data class LibLike(val key: String, val at: Long)

/** [cover]: blank = automatic mosaic of the first four covers; otherwise a custom image uri. */
data class LibPlaylist(val id: String, val name: String, val tracks: List<String> = emptyList(), val cover: String = "",
                       val source: LibSource = LibSource.LOCAL, val externalIds: Map<String, String> = emptyMap(),
                       val description: String = "", val createdAt: Long = 0, val updatedAt: Long = 0)

data class LibArtist(val name: String, val channelId: String = "", val art: String = "", val source: LibSource = LibSource.LOCAL,
                     val externalIds: Map<String, String> = emptyMap(), val followedAt: Long = 0, val updatedAt: Long = 0)

/** Saved album: [id] is the YouTube Music browse id (`MPREb_…`) or `local:<name>` for an album made of local files. */
data class LibAlbum(val id: String, val title: String, val artist: String = "", val art: String = "", val year: String = "",
                    val source: LibSource = LibSource.LOCAL, val externalIds: Map<String, String> = emptyMap(),
                    val savedAt: Long = 0, val updatedAt: Long = 0)

/** Tombstones keep deletions across merges/sync (0.25 Drive backup): identity → deletedAt. */
data class LibraryData(val tracks: Map<String, LibTrack> = emptyMap(), val likes: List<LibLike> = emptyList(),
                       val playlists: List<LibPlaylist> = emptyList(), val artists: List<LibArtist> = emptyList(),
                       val albums: List<LibAlbum> = emptyList(), val updatedAt: Long = 0) {
  fun track(key: String) = tracks[key]
  fun playlist(name: String) = playlists.firstOrNull { it.name == name }
  val likedKeys get() = likes.map { it.key }.toSet()
}

object LibrarySchema {
  /** 1 = legacy `{fav:[uri], pl:{name:[uri]}}` (≤0.22, no field). 2 = unified model. */
  const val CURRENT = 2
  val RESERVED = setOf("Tudo", "Favoritos", "Curtidas")
  fun validName(name: String) = name.isNotBlank() && name.length <= 80 && name !in RESERVED
}

/** Data outside library.json that the 1 → 2 migration folds in. */
data class LegacyInputs(val files: List<com.rise.musique.ui.HistoryItem> = emptyList(), val followed: Set<String> = emptySet(),
                        val spotify: List<SpotifyPlaylist> = emptyList(), val artistPhotos: Map<String, String> = emptyMap())

object LibraryMigrations {
  fun version(o: JSONObject) = o.optInt("schema", 1)

  /** Brings any stored JSON up to [LibrarySchema.CURRENT] without losing user data. Newer schemas are refused. */
  fun migrate(o: JSONObject, legacy: LegacyInputs = LegacyInputs(), now: Long = System.currentTimeMillis()): LibraryData {
    val v = version(o)
    require(v <= LibrarySchema.CURRENT) { "Biblioteca de uma versão mais nova do app" }
    return if (v < 2) from1(o, legacy, now) else LibraryCodec.decode(o)
  }

  /** 1 → 2: favorites and playlists keep order; tracks gain metadata from the files list; follows and Spotify saves move in. */
  fun from1(o: JSONObject, legacy: LegacyInputs, now: Long): LibraryData {
    val files = legacy.files.associateBy { it.uri }
    val tracks = LinkedHashMap<String, LibTrack>()
    fun ensure(key: String) { if (key.isBlank() || key in tracks) return
      tracks[key] = files[key]?.let { trackOf(it, now) } ?: stubTrack(key, now) }
    val fav = o.optJSONArray("fav").strings().map(::normalizeKey).distinct()
    fav.forEach(::ensure)
    val pl = o.optJSONObject("pl")
    val playlists = pl?.keys()?.asSequence()?.toList().orEmpty().mapIndexed { i, name ->
      val keys = pl!!.optJSONArray(name).strings().map(::normalizeKey).distinct(); keys.forEach(::ensure)
      LibPlaylist("pl-$now-$i", name, keys, createdAt = now, updatedAt = now)
    }.toMutableList()
    legacy.spotify.forEach { sp ->
      val keys = sp.tracks.map { t -> spotifyTrack(t, now).also { tracks.putIfAbsent(it.key, it) }.key }
      playlists += spotifyPlaylist(sp, keys, now, playlists.map { it.name }.toSet())
    }
    val artists = legacy.followed.sorted().map { LibArtist(it, art = legacy.artistPhotos[it].orEmpty(), followedAt = now, updatedAt = now) }
    return LibraryData(tracks, fav.map { LibLike(it, now) }, playlists, artists, emptyList(), now)
  }

  fun trackOf(h: com.rise.musique.ui.HistoryItem, now: Long, videoId: String = "") = LibTrack(h.uri, h.title, h.artist, h.album, h.duration, h.art,
    videoId, LibSource.LOCAL, if (videoId.isNotBlank()) mapOf("youtube" to videoId) else emptyMap(), now, now)

  /** A key whose file is gone (or a stream): keep it so nothing is lost; the video id comes from the uri when there is one. */
  fun stubTrack(key: String, now: Long): LibTrack {
    val id = streamId(key)
    return LibTrack(key, if (id != null) "" else key.substringAfterLast('/').substringBeforeLast('.'), videoId = id.orEmpty(),
      source = if (id != null) LibSource.YOUTUBE else LibSource.LOCAL, externalIds = id?.let { mapOf("youtube" to it) }.orEmpty(), addedAt = now, updatedAt = now)
  }
  /** Legacy keys from ≤0.22 (`stream:<url>` media ids) become `rise-stream://<id>`. */
  fun normalizeKey(key: String): String = if (key.startsWith("stream:")) videoIdOf(key)?.let { "rise-stream://$it" } ?: key else key
  fun videoIdOf(url: String) = Regex("(?:v=|youtu\\.be/|/shorts/|rise-stream://)([A-Za-z0-9_-]{11})").find(url)?.groupValues?.get(1)
  fun streamId(key: String) = key.takeIf { it.startsWith("rise-stream://") }?.removePrefix("rise-stream://")?.substringBefore('/')?.takeIf { it.length == 11 }

  fun spotifyTrack(t: SpotifyTrack, now: Long): LibTrack { val id = t.uri.substringAfterLast(':')
    return LibTrack("rise-spotify://track/$id", t.title, t.artist, durationMs = t.durationMs, source = LibSource.SPOTIFY,
      externalIds = mapOf("spotify" to t.uri), addedAt = now, updatedAt = now) }
  fun spotifyPlaylist(sp: SpotifyPlaylist, keys: List<String>, now: Long, taken: Set<String>): LibPlaylist {
    val base = sp.title.ifBlank { "Spotify" }.take(70).let { if (it in LibrarySchema.RESERVED) "$it (Spotify)" else it }
    val name = generateSequence(1) { it + 1 }.map { if (it == 1) base else "$base ($it)" }.first { it !in taken }
    return LibPlaylist("sp-${sp.id}", name, keys, sp.art, LibSource.SPOTIFY, mapOf("spotify" to "spotify:${sp.type}:${sp.id}", "spotifyType" to sp.type),
      sp.subtitle, now, now)
  }
}

private fun JSONArray?.strings(): List<String> = this?.let { a -> (0 until a.length()).mapNotNull { a.optString(it).takeIf(String::isNotBlank) } }.orEmpty()
private fun JSONObject?.stringMap(): Map<String, String> = this?.let { o -> o.keys().asSequence().associateWith { o.optString(it) } }.orEmpty()
private fun Map<String, String>.json() = JSONObject().also { o -> forEach { (k, v) -> o.put(k, v) } }

/** Schema-2 JSON (also the `library` part of the export file). */
object LibraryCodec {
  fun encode(d: LibraryData): JSONObject = JSONObject().put("schema", LibrarySchema.CURRENT).put("updatedAt", d.updatedAt)
    .put("tracks", JSONArray(d.tracks.values.map { t -> JSONObject().put("key", t.key).put("title", t.title).put("artist", t.artist)
      .put("album", t.album).put("durationMs", t.durationMs).put("art", t.art).put("videoId", t.videoId).put("source", t.source.id)
      .put("externalIds", t.externalIds.json()).put("addedAt", t.addedAt).put("updatedAt", t.updatedAt) }))
    .put("likes", JSONArray(d.likes.map { JSONObject().put("key", it.key).put("at", it.at) }))
    .put("playlists", JSONArray(d.playlists.map { p -> JSONObject().put("id", p.id).put("name", p.name).put("tracks", JSONArray(p.tracks))
      .put("cover", p.cover).put("source", p.source.id).put("externalIds", p.externalIds.json()).put("description", p.description)
      .put("createdAt", p.createdAt).put("updatedAt", p.updatedAt) }))
    .put("artists", JSONArray(d.artists.map { a -> JSONObject().put("name", a.name).put("channelId", a.channelId).put("art", a.art)
      .put("source", a.source.id).put("externalIds", a.externalIds.json()).put("followedAt", a.followedAt).put("updatedAt", a.updatedAt) }))
    .put("albums", JSONArray(d.albums.map { a -> JSONObject().put("id", a.id).put("title", a.title).put("artist", a.artist).put("art", a.art)
      .put("year", a.year).put("source", a.source.id).put("externalIds", a.externalIds.json()).put("savedAt", a.savedAt).put("updatedAt", a.updatedAt) }))

  fun decode(o: JSONObject): LibraryData {
    fun objs(name: String) = o.optJSONArray(name)?.let { a -> (0 until a.length()).mapNotNull(a::optJSONObject) }.orEmpty()
    val tracks = objs("tracks").filter { it.optString("key").isNotBlank() }.map { t -> LibTrack(t.getString("key"), t.optString("title"),
      t.optString("artist"), t.optString("album"), t.optLong("durationMs").coerceAtLeast(0), t.optString("art"), t.optString("videoId"),
      LibSource.of(t.optString("source")), t.optJSONObject("externalIds").stringMap(), t.optLong("addedAt"), t.optLong("updatedAt")) }
    return LibraryData(tracks.associateBy { it.key },
      objs("likes").filter { it.optString("key").isNotBlank() }.map { LibLike(it.getString("key"), it.optLong("at")) }.distinctBy { it.key },
      objs("playlists").filter { LibrarySchema.validName(it.optString("name")) }.map { p -> LibPlaylist(p.optString("id").ifBlank { "pl-" + p.getString("name").hashCode() },
        p.getString("name"), p.optJSONArray("tracks").strings(), p.optString("cover"), LibSource.of(p.optString("source")),
        p.optJSONObject("externalIds").stringMap(), p.optString("description"), p.optLong("createdAt"), p.optLong("updatedAt")) }.distinctBy { it.name },
      objs("artists").filter { it.optString("name").isNotBlank() }.map { a -> LibArtist(a.getString("name"), a.optString("channelId"), a.optString("art"),
        LibSource.of(a.optString("source")), a.optJSONObject("externalIds").stringMap(), a.optLong("followedAt"), a.optLong("updatedAt")) }.distinctBy { it.name.lowercase() },
      objs("albums").filter { it.optString("id").isNotBlank() }.map { a -> LibAlbum(a.getString("id"), a.optString("title"), a.optString("artist"), a.optString("art"),
        a.optString("year"), LibSource.of(a.optString("source")), a.optJSONObject("externalIds").stringMap(), a.optLong("savedAt"), a.optLong("updatedAt")) }.distinctBy { it.id },
      o.optLong("updatedAt"))
  }
}

/** Versioned export file (SAF). `format`/`version` gate the import; the body is the schema-2 library plus play history. */
object LibraryFile {
  const val FORMAT = "rise-musique-library"; const val VERSION = 1
  fun encode(d: LibraryData, history: JSONArray = JSONArray(), now: Long = System.currentTimeMillis()): String =
    JSONObject().put("format", FORMAT).put("version", VERSION).put("exportedAt", now).put("library", LibraryCodec.encode(d)).put("history", history).toString(2)
  data class Parsed(val library: LibraryData, val history: JSONArray)
  fun decode(text: String): Parsed {
    val o = runCatching { JSONObject(text) }.getOrElse { throw IllegalArgumentException("Arquivo não é JSON") }
    require(o.optString("format") == FORMAT) { "Esse arquivo não é uma biblioteca do Rise Musique" }
    require(o.optInt("version") in 1..VERSION) { "Versão de biblioteca incompatível (${o.optInt("version")})" }
    val lib = o.optJSONObject("library") ?: throw IllegalArgumentException("Arquivo sem biblioteca")
    return Parsed(LibraryMigrations.migrate(lib), o.optJSONArray("history") ?: JSONArray())
  }
}

data class MergeReport(val tracks: Int = 0, val likes: Int = 0, val playlists: Int = 0, val artists: Int = 0, val albums: Int = 0) {
  val total get() = tracks + likes + playlists + artists + albums
}

/**
 * Import merge. Tracks dedupe by YouTube video id (then key): an incoming track that matches one already here maps to the
 * existing key, so a downloaded file and its stream stay one track. Playlists with the same name union their tracks (mine first);
 * likes/artists/albums union. Metadata: blanks are filled from the other side, newer [LibTrack.updatedAt] wins otherwise.
 */
object LibraryMerge {
  fun merge(mine: LibraryData, other: LibraryData, now: Long = System.currentTimeMillis()): Pair<LibraryData, MergeReport> {
    val tracks = LinkedHashMap(mine.tracks)
    val byIdentity = HashMap<String, String>().apply { mine.tracks.values.forEach { put(it.identity, it.key) } }
    val remap = HashMap<String, String>()
    var newTracks = 0
    other.tracks.values.forEach { t ->
      val existing = byIdentity[t.identity] ?: t.key.takeIf { it in tracks }
      if (existing != null) { remap[t.key] = existing; tracks[existing] = combine(tracks.getValue(existing), t) }
      else { tracks[t.key] = t; byIdentity[t.identity] = t.key; remap[t.key] = t.key; newTracks++ }
    }
    fun map(k: String) = remap[k] ?: byIdentity[MergeKeys.identityOf(k)] ?: k
    val liked = mine.likes.map { it.key }.toMutableSet()
    val likes = mine.likes + other.likes.map { it.copy(key = map(it.key)) }.filter { liked.add(it.key) }
    var newPl = 0
    val playlists = mine.playlists.toMutableList()
    other.playlists.forEach { p -> val keys = p.tracks.map(::map)
      val i = playlists.indexOfFirst { it.name == p.name || (p.externalIds["spotify"] != null && it.externalIds["spotify"] == p.externalIds["spotify"]) }
      if (i < 0) { playlists += p.copy(tracks = keys.distinct(), id = if (playlists.any { it.id == p.id }) "${p.id}-i$now" else p.id); newPl++ }
      else { val cur = playlists[i]; val union = (cur.tracks + keys).distinct()
        playlists[i] = cur.copy(tracks = union, cover = cur.cover.ifBlank { p.cover }, updatedAt = maxOf(cur.updatedAt, p.updatedAt)) }
    }
    val artistNames = mine.artists.map { it.name.lowercase() }.toMutableSet()
    val artists = mine.artists + other.artists.filter { artistNames.add(it.name.lowercase()) }
    val albumIds = mine.albums.map { it.id }.toMutableSet()
    val albums = mine.albums + other.albums.filter { albumIds.add(it.id) }
    val report = MergeReport(newTracks, likes.size - mine.likes.size, newPl, artists.size - mine.artists.size, albums.size - mine.albums.size)
    return LibraryData(tracks, likes, playlists, artists, albums, now) to report
  }
  private fun combine(a: LibTrack, b: LibTrack): LibTrack {
    val (n, o) = if (b.updatedAt > a.updatedAt) b to a else a to b
    return a.copy(title = n.title.ifBlank { o.title }, artist = n.artist.ifBlank { o.artist }, album = n.album.ifBlank { o.album },
      durationMs = if (n.durationMs > 0) n.durationMs else o.durationMs, art = n.art.ifBlank { o.art }, videoId = a.videoId.ifBlank { b.videoId },
      externalIds = b.externalIds + a.externalIds, addedAt = listOf(a.addedAt, b.addedAt).filter { it > 0 }.minOrNull() ?: 0,
      updatedAt = maxOf(a.updatedAt, b.updatedAt))
  }
}

internal object MergeKeys { fun identityOf(key: String) = LibraryMigrations.streamId(key)?.let { "yt:$it" } ?: key }

/** Pure edits used by [Library]; each bumps the affected updatedAt. */
object LibraryOps {
  fun upsertTrack(d: LibraryData, t: LibTrack, now: Long): LibraryData {
    val cur = d.tracks[t.key]
    val merged = if (cur == null) t.copy(addedAt = t.addedAt.takeIf { it > 0 } ?: now, updatedAt = now)
      else cur.copy(title = t.title.ifBlank { cur.title }, artist = t.artist.ifBlank { cur.artist }, album = t.album.ifBlank { cur.album },
        durationMs = if (t.durationMs > 0) t.durationMs else cur.durationMs, art = t.art.ifBlank { cur.art }, videoId = t.videoId.ifBlank { cur.videoId },
        externalIds = cur.externalIds + t.externalIds, updatedAt = now)
    return d.copy(tracks = d.tracks + (t.key to merged), updatedAt = now)
  }
  fun toggleLike(d: LibraryData, key: String, now: Long): LibraryData =
    d.copy(likes = if (d.likes.any { it.key == key }) d.likes.filterNot { it.key == key } else listOf(LibLike(key, now)) + d.likes, updatedAt = now)
  fun createPlaylist(d: LibraryData, name: String, now: Long, tracks: List<String> = emptyList()): LibraryData {
    require(LibrarySchema.validName(name)) { "Escolha um nome de até 80 caracteres" }
    if (d.playlist(name) != null) return d
    return d.copy(playlists = d.playlists + LibPlaylist("pl-$now-${name.hashCode().toUInt()}", name, tracks.distinct(), createdAt = now, updatedAt = now), updatedAt = now)
  }
  private fun edit(d: LibraryData, name: String, now: Long, f: (LibPlaylist) -> LibPlaylist): LibraryData {
    val p = d.playlist(name) ?: throw IllegalArgumentException("Playlist não encontrada")
    return d.copy(playlists = d.playlists.map { if (it === p) f(it).copy(updatedAt = now) else it }, updatedAt = now)
  }
  fun rename(d: LibraryData, old: String, name: String, now: Long): LibraryData {
    require(LibrarySchema.validName(name)) { "Escolha um nome de até 80 caracteres" }
    if (name == old) return d
    require(d.playlist(name) == null) { "Já existe uma playlist com esse nome" }
    return edit(d, old, now) { it.copy(name = name) }
  }
  fun add(d: LibraryData, name: String, keys: List<String>, now: Long) = edit(d, name, now) { it.copy(tracks = (it.tracks + keys).distinct()) }
  /** Removes [key] or the same track under another key (a file and its stream share the video id). */
  fun remove(d: LibraryData, name: String, key: String, now: Long): LibraryData { val id = d.tracks[key]?.identity ?: MergeKeys.identityOf(key)
    return edit(d, name, now) { p -> p.copy(tracks = p.tracks.filterNot { it == key || (d.tracks[it]?.identity ?: MergeKeys.identityOf(it)) == id }) } }
  fun move(d: LibraryData, name: String, from: Int, to: Int, now: Long) = edit(d, name, now) { it.copy(tracks = PlaylistEdits.move(it.tracks, from, to)) }
  fun setCover(d: LibraryData, name: String, cover: String, now: Long) = edit(d, name, now) { it.copy(cover = cover) }
  fun delete(d: LibraryData, name: String, now: Long) = d.copy(playlists = d.playlists.filterNot { it.name == name }, updatedAt = now)
  /** Moves a playlist in the Playlists tab order. */
  fun reorderPlaylists(d: LibraryData, from: Int, to: Int, now: Long): LibraryData {
    require(from in d.playlists.indices && to in d.playlists.indices) { "Posição inválida" }
    return d.copy(playlists = d.playlists.toMutableList().apply { add(to, removeAt(from)) }, updatedAt = now)
  }
  /** Forget a deleted file everywhere. */
  fun forget(d: LibraryData, key: String, now: Long) = d.copy(tracks = d.tracks - key, likes = d.likes.filterNot { it.key == key },
    playlists = d.playlists.map { if (key in it.tracks) it.copy(tracks = it.tracks - key, updatedAt = now) else it }, updatedAt = now)
  fun toggleArtist(d: LibraryData, a: LibArtist, now: Long): Pair<LibraryData, Boolean> {
    val on = d.artists.none { it.name.equals(a.name, true) }
    return d.copy(artists = if (on) d.artists + a.copy(followedAt = now, updatedAt = now) else d.artists.filterNot { it.name.equals(a.name, true) }, updatedAt = now) to on
  }
  fun toggleAlbum(d: LibraryData, a: LibAlbum, now: Long): Pair<LibraryData, Boolean> {
    val on = d.albums.none { it.id == a.id }
    return d.copy(albums = if (on) listOf(a.copy(savedAt = now, updatedAt = now)) + d.albums else d.albums.filterNot { it.id == a.id }, updatedAt = now) to on
  }
}
