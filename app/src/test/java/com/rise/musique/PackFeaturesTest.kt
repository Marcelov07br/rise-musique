package com.rise.musique

import com.rise.musique.ui.*
import org.json.*
import org.junit.Assert.*
import org.junit.Test

class PackFeaturesTest {
  private val a = HistoryItem("Zulu", "MP3", "/music/a.mp3", artist = "Ana", duration = 125000, album = "Primeiro", imported = true)
  private val b = HistoryItem("Alfa", "MP4", "/music/b.mp4", artist = "Bruno", video = true)
  @Test fun oldHistoryRemainsReadable() {
    val old = JSONObject().put("t", "Uma").put("m", "WEBM · 720p").put("u", "/old.webm")
    val h = TrackJson.decode(old)
    assertEquals(0L, h.duration); assertEquals("", h.album); assertFalse(h.imported); assertTrue(h.video)
  }
  @Test fun metadataSurvivesBackupRoundTrip() { assertEquals(a, TrackJson.decode(TrackJson.encode(a))) }
  @Test fun playlistRenamePreservesOrder() {
    val pl = JSONObject().put("Treino", JSONArray(listOf("b", "a")))
    PlaylistEdits.rename(pl, "Treino", "Caminhada")
    assertFalse(pl.has("Treino")); assertEquals("[\"b\",\"a\"]", pl.getJSONArray("Caminhada").toString())
  }
  @Test fun renameCannotOverwriteAnotherPlaylist() {
    val pl = JSONObject().put("Um", JSONArray()).put("Dois", JSONArray(listOf("a")))
    try { PlaylistEdits.rename(pl, "Um", "Dois"); fail() } catch (_: IllegalArgumentException) { assertTrue(pl.has("Um")); assertEquals(1, pl.getJSONArray("Dois").length()) }
  }
  @Test fun reservedPlaylistNamesAreRejected() {
    val pl = JSONObject().put("Um", JSONArray())
    try { PlaylistEdits.rename(pl, "Um", "Favoritos"); fail() } catch (_: IllegalArgumentException) { assertTrue(pl.has("Um")) }
  }
  @Test fun reorderKeepsEveryTrack() { assertEquals(listOf("b", "c", "a"), PlaylistEdits.move(listOf("a", "b", "c"), 0, 2)) }
  @Test fun invalidReorderCannotMutateInput() {
    val tracks = listOf("a", "b")
    try { PlaylistEdits.move(tracks, 0, 2); fail() } catch (_: IllegalArgumentException) { assertEquals(listOf("a", "b"), tracks) }
  }
  @Test fun audioFilterDoesNotShowVideos() { assertEquals(listOf(a), visibleHistory(UiState(history = listOf(a, b), mediaFilter = "Músicas"))) }
  @Test fun titleSortAndArtistSortAreIndependent() {
    assertEquals(listOf(b, a), visibleHistory(UiState(history = listOf(a, b), sort = "Título")))
    assertEquals(listOf(a, b), visibleHistory(UiState(history = listOf(b, a), sort = "Artista")))
  }
  @Test fun playlistOrderOverridesLibrarySort() {
    assertEquals(listOf(a, b), visibleHistory(UiState(history = listOf(a, b), playlists = mapOf("Treino" to listOf(a.uri, b.uri)), historyView = "Treino", sort = "Título")))
  }
  @Test fun albumSearchAndFilterWorkTogether() {
    assertEquals(listOf(a), visibleHistory(UiState(history = listOf(a, b), historyFilter = "primeiro")))
    assertEquals(listOf(a), visibleHistory(UiState(history = listOf(a, b), albumFilter = "Primeiro")))
  }
  @Test fun restoreKeepsQueuePositionAndControls() {
    val saved = PlaybackSnapshot(listOf(a, b), 1, 42000, 2, true)
    assertEquals(saved, PlaybackSnapshot.parse(saved.json()))
  }
  @Test fun invalidStoredPositionAndRepeatAreClamped() {
    val o = JSONObject(PlaybackSnapshot(listOf(a), 0, 0, 0, false).json()).put("index", 50).put("position", -100).put("repeat", 99)
    val restored = PlaybackSnapshot.parse(o.toString())
    assertEquals(0, restored.index); assertEquals(0L, restored.position); assertEquals(0, restored.repeat)
  }
  @Test fun previewUrlsCannotBeRestored() {
    try { PlaybackSnapshot.parse(PlaybackSnapshot(listOf(a.copy(uri = "https://expired/stream")), 0, 0, 0, false).json()); fail() }
    catch (_: IllegalArgumentException) { }
  }
  @Test fun stagesDistinguishDownloadAndConversion() {
    assertEquals("Baixando", DownloadStage.fromLine("[download] 45%"))
    assertEquals("Convertendo", DownloadStage.fromLine("[ExtractAudio] Destination"))
    assertEquals("Finalizando", DownloadStage.fromLine("[EmbedThumbnail] ffmpeg"))
    assertEquals("Convertendo", DownloadStage.fromLine("ffmpeg version…", "Convertendo"))
  }
}
