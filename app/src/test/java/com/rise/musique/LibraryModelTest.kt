package com.rise.musique

import com.rise.musique.ui.HistoryItem
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

/** 0.23: unified library — migrations, merge/dedupe and the versioned JSON file. */
class LibraryModelTest {
  private val now = 1_760_000_000_000L
  private val vid = "dQw4w9WgXcQ"
  private val legacy = JSONObject("""{"fav":["/m/a.mp3","stream:https://music.youtube.com/watch?v=$vid","/m/gone.mp3"],
    "pl":{"Treino":["/m/b.mp3","/m/a.mp3","/m/b.mp3"],"Vazia":[]}}""")
  private val files = listOf(HistoryItem("A", "MP3 · 320k", "/m/a.mp3", "art-a", "Artista A", 180_000, "Disco"),
    HistoryItem("B", "FLAC", "/m/b.mp3", "", "Artista B"))
  private val sp = SpotifyPlaylist("37i9", "playlist", "Favoritos", "Spotify", "sp-art",
    listOf(SpotifyTrack("spotify:track:111", "Song", "Singer", 200_000)))

  // ---- migrations ----
  @Test fun legacyWithoutSchemaIsVersion1() { assertEquals(1, LibraryMigrations.version(legacy)) }

  @Test fun migration1to2KeepsEveryLikeAndPlaylist() {
    val d = LibraryMigrations.migrate(legacy, LegacyInputs(files, setOf("Djavan"), listOf(sp), mapOf("Djavan" to "photo")), now)
    assertEquals(listOf("/m/a.mp3", "rise-stream://$vid", "/m/gone.mp3"), d.likes.map { it.key })
    assertEquals(listOf("/m/b.mp3", "/m/a.mp3"), d.playlist("Treino")!!.tracks) // order kept, duplicate dropped
    assertNotNull(d.playlist("Vazia"))
    // metadata comes from the files list; missing file kept as a stub; stream keeps its video id
    assertEquals("Artista A", d.track("/m/a.mp3")!!.artist); assertEquals(180_000, d.track("/m/a.mp3")!!.durationMs)
    assertEquals("gone", d.track("/m/gone.mp3")!!.title)
    d.track("rise-stream://$vid")!!.let { assertEquals(vid, it.videoId); assertEquals(LibSource.YOUTUBE, it.source); assertEquals(vid, it.externalIds["youtube"]) }
    // follows and Spotify saves move in, with provenance
    assertEquals(listOf("Djavan"), d.artists.map { it.name }); assertEquals("photo", d.artists[0].art)
    val spl = d.playlists.single { it.source == LibSource.SPOTIFY }
    assertEquals("Favoritos (Spotify)", spl.name) // reserved name avoided
    assertEquals("spotify:playlist:37i9", spl.externalIds["spotify"])
    assertEquals(LibSource.SPOTIFY, d.track(spl.tracks.single())!!.source)
    assertTrue(d.tracks.values.all { it.updatedAt == now })
  }

  @Test fun migratedDataIsStableOnReload() {
    val d = LibraryMigrations.migrate(legacy, LegacyInputs(files), now)
    val stored = LibraryCodec.encode(d)
    assertEquals(LibrarySchema.CURRENT, LibraryMigrations.version(stored))
    assertEquals(d, LibraryMigrations.migrate(JSONObject(stored.toString()), LegacyInputs(files, setOf("ignored")), now + 1))
  }

  @Test fun emptyStoreMigratesToEmptyLibrary() {
    val d = LibraryMigrations.migrate(JSONObject(), LegacyInputs(), now)
    assertTrue(d.likes.isEmpty() && d.playlists.isEmpty() && d.tracks.isEmpty())
  }

  @Test(expected = IllegalArgumentException::class) fun newerSchemaIsRefused() { LibraryMigrations.migrate(JSONObject().put("schema", 99)) }

  // ---- merge / dedupe ----
  private fun t(key: String, video: String = "", title: String = "", at: Long = now) = LibTrack(key, title, videoId = video, addedAt = at, updatedAt = at)

  @Test fun mergeDedupesByVideoId() {
    val mine = LibraryData(mapOf("/m/a.mp3" to t("/m/a.mp3", vid, "A")), listOf(LibLike("/m/a.mp3", now)),
      listOf(LibPlaylist("p1", "Treino", listOf("/m/a.mp3"))))
    val other = LibraryData(mapOf("rise-stream://$vid" to t("rise-stream://$vid", vid, "A (stream)", now + 5), "rise-stream://xxxxxxxxxxx" to t("rise-stream://xxxxxxxxxxx", "xxxxxxxxxxx")),
      listOf(LibLike("rise-stream://$vid", now), LibLike("rise-stream://xxxxxxxxxxx", now)),
      listOf(LibPlaylist("p9", "Treino", listOf("rise-stream://xxxxxxxxxxx", "rise-stream://$vid")), LibPlaylist("p2", "Nova", listOf("rise-stream://$vid"))),
      listOf(LibArtist("Djavan")))
    val (m, r) = LibraryMerge.merge(mine, other, now)
    assertEquals(2, m.tracks.size) // the stream of the same video maps onto the downloaded file
    assertEquals(listOf("/m/a.mp3", "rise-stream://xxxxxxxxxxx"), m.likes.map { it.key })
    assertEquals(listOf("/m/a.mp3", "rise-stream://xxxxxxxxxxx"), m.playlist("Treino")!!.tracks)
    assertEquals(listOf("/m/a.mp3"), m.playlist("Nova")!!.tracks)
    assertEquals("A (stream)", m.track("/m/a.mp3")!!.title) // newer metadata wins, key stays
    assertEquals(MergeReport(tracks = 1, likes = 1, playlists = 1, artists = 1, albums = 0), r)
  }

  @Test fun mergingTwiceAddsNothing() {
    val other = LibraryData(mapOf("k" to t("k", "aaaaaaaaaaa")), listOf(LibLike("k", now)), listOf(LibPlaylist("p", "X", listOf("k"))),
      listOf(LibArtist("Elis")), listOf(LibAlbum("MPREb_1", "Álbum")))
    val once = LibraryMerge.merge(LibraryData(), other, now).first
    val (twice, r) = LibraryMerge.merge(once, other, now)
    assertEquals(0, r.total); assertEquals(once.copy(updatedAt = 0), twice.copy(updatedAt = 0))
  }

  @Test fun mergeFillsBlanksAndKeepsExternalIds() {
    val mine = LibraryData(mapOf("k" to LibTrack("k", "Título", art = "", externalIds = mapOf("youtube" to "v"), updatedAt = now + 10)))
    val other = LibraryData(mapOf("k" to LibTrack("k", "Velho", art = "capa", externalIds = mapOf("spotify" to "spotify:track:1"), updatedAt = now)))
    val t = LibraryMerge.merge(mine, other, now).first.track("k")!!
    assertEquals("Título", t.title); assertEquals("capa", t.art); assertEquals(setOf("youtube", "spotify"), t.externalIds.keys)
  }

  @Test fun artistsMergeCaseInsensitive() {
    val (m, _) = LibraryMerge.merge(LibraryData(artists = listOf(LibArtist("Djavan"))), LibraryData(artists = listOf(LibArtist("djavan"))), now)
    assertEquals(1, m.artists.size)
  }

  // ---- JSON round trip ----
  private val full = LibraryData(
    mapOf("/m/a.mp3" to LibTrack("/m/a.mp3", "A", "Art", "Disco", 1000, "art", vid, LibSource.LOCAL, mapOf("youtube" to vid), 1, 2),
      "rise-spotify://track/1" to LibTrack("rise-spotify://track/1", "S", "Sp", source = LibSource.SPOTIFY, externalIds = mapOf("spotify" to "spotify:track:1"))),
    listOf(LibLike("/m/a.mp3", 5)),
    listOf(LibPlaylist("p", "Minha “lista” ✨", listOf("/m/a.mp3", "rise-spotify://track/1"), "content://cover", LibSource.SPOTIFY, mapOf("spotify" to "x"), "desc", 3, 4)),
    listOf(LibArtist("Djavan", "UC1", "a", LibSource.YOUTUBE, mapOf("ytm" to "UC1"), 6, 7)),
    listOf(LibAlbum("MPREb_1", "Álbum", "Djavan", "c", "1982", LibSource.YOUTUBE, mapOf("ytmAlbum" to "MPREb_1"), 8, 9)), 10)

  @Test fun codecRoundTrip() { assertEquals(full, LibraryCodec.decode(JSONObject(LibraryCodec.encode(full).toString()))) }

  @Test fun fileRoundTripWithHistory() {
    val history = JSONArray().put(JSONObject().put("key", "/m/a.mp3").put("at", 1))
    val p = LibraryFile.decode(LibraryFile.encode(full, history, now))
    assertEquals(full, p.library); assertEquals(1, p.history.length())
    assertTrue(LibraryFile.encode(full).contains("\"format\": \"${LibraryFile.FORMAT}\""))
  }

  @Test fun fileRejectsForeignOrNewerFiles() {
    for (text in listOf("{}", "not json", JSONObject().put("format", LibraryFile.FORMAT).put("version", 99).toString(),
      JSONObject().put("format", LibraryFile.FORMAT).put("version", 1).toString()))
      assertThrows(IllegalArgumentException::class.java) { LibraryFile.decode(text) }
  }

  @Test fun fileImportAcceptsLegacyLibraryBody() {
    val text = JSONObject().put("format", LibraryFile.FORMAT).put("version", 1).put("library", legacy).toString()
    assertEquals(3, LibraryFile.decode(text).library.likes.size)
  }

  // ---- edits ----
  @Test fun playlistEdits() {
    var d = LibraryOps.createPlaylist(LibraryData(), "A", now, listOf("1", "2", "3"))
    d = LibraryOps.move(d, "A", 0, 2, now + 1); assertEquals(listOf("2", "3", "1"), d.playlist("A")!!.tracks)
    assertEquals(now + 1, d.playlist("A")!!.updatedAt)
    d = LibraryOps.rename(LibraryOps.createPlaylist(d, "B", now), "A", "C", now)
    assertThrows(IllegalArgumentException::class.java) { LibraryOps.rename(d, "C", "B", now) }
    assertThrows(IllegalArgumentException::class.java) { LibraryOps.createPlaylist(d, "Curtidas", now) }
    d = LibraryOps.reorderPlaylists(d, 1, 0, now); assertEquals(listOf("B", "C"), d.playlists.map { it.name })
    d = LibraryOps.setCover(d, "B", "content://x", now); assertEquals("content://x", d.playlist("B")!!.cover)
    d = LibraryOps.delete(d, "B", now); assertNull(d.playlist("B"))
  }

  @Test fun removingAFileRemovesItsStreamTwinFromPlaylist() {
    val d = LibraryData(mapOf("/f.mp3" to t("/f.mp3", vid)), playlists = listOf(LibPlaylist("p", "A", listOf("rise-stream://$vid", "x"))))
    assertEquals(listOf("x"), LibraryOps.remove(d, "A", "/f.mp3", now).playlist("A")!!.tracks)
  }

  @Test fun likeToggleAndForget() {
    var d = LibraryOps.toggleLike(LibraryData(), "k", now); assertEquals(setOf("k"), d.likedKeys)
    d = LibraryOps.add(LibraryOps.createPlaylist(d, "A", now), "A", listOf("k"), now)
    d = LibraryOps.forget(d, "k", now); assertTrue(d.likedKeys.isEmpty()); assertTrue(d.playlist("A")!!.tracks.isEmpty())
  }

  @Test fun legacyStreamKeysNormalize() {
    assertEquals("rise-stream://$vid", LibraryMigrations.normalizeKey("stream:https://www.youtube.com/watch?v=$vid&list=x"))
    assertEquals("/m/a.mp3", LibraryMigrations.normalizeKey("/m/a.mp3"))
  }
}
