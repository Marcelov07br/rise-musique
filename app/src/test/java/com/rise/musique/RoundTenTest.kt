package com.rise.musique

import com.rise.musique.ui.HistoryItem
import org.json.JSONArray
import org.junit.Assert.*
import org.junit.Test
import java.util.Calendar

class PlayStatsTest {
  private fun at(y: Int, m: Int) = Calendar.getInstance().apply { set(y, m, 10, 12, 0) }.timeInMillis
  @Test fun countsAfterThirtySecondsOrHalf() {
    val long = PlaySession("a", "A", "X", 0)
    repeat(5) { assertFalse(long.add(5_000, 240_000)) }
    assertTrue(long.add(5_000, 240_000)); assertFalse(long.add(5_000, 240_000)); assertTrue(long.counted)
    val short = PlaySession("b", "B", "X", 0)
    assertFalse(short.add(5_000, 20_000)); assertTrue(short.add(5_000, 20_000))
    assertEquals(30_000, PlaySession.threshold(0))
  }
  @Test fun ignoresHugeDeltas() { val s = PlaySession("a", "", "", 0); s.add(600_000, 240_000); assertEquals(5_000, s.listenedMs) }
  @Test fun summarizesTopAndMonths() {
    val now = at(2026, Calendar.OCTOBER)
    val plays = listOf(Play("1", "Um", "Ana", 60_000, at(2026, Calendar.OCTOBER)), Play("1", "Um", "Ana", 60_000, at(2026, Calendar.SEPTEMBER)),
      Play("2", "Dois", "ana ", 30_000, now), Play("3", "Três", "Bia", 200_000, now))
    val s = PlayStats.summarize(plays, now)
    assertEquals(4, s.totalPlays); assertEquals(350_000, s.totalMs)
    assertEquals("Um", s.topTracks.first().name)
    assertEquals(3, s.topArtists.first().plays)
    assertEquals(6, s.months.size); assertEquals("out/26", s.months.last().label); assertEquals(3, s.months.last().plays)
    assertEquals(1, s.months[4].plays)
  }
  @Test fun mergeDedupesByKeyAndStart() {
    val a = listOf(Play("1", "", "", 1, 10)); val b = listOf(Play("1", "", "", 1, 10), Play("2", "", "", 1, 5))
    val (m, added) = PlayStats.mergeLists(a, b); assertEquals(1, added); assertEquals(listOf("2", "1"), m.map { it.key })
    assertEquals(2, PlayStats.parse(JSONArray(m.map(Play::json))).size)
  }
}

class CrossfadeRulesTest {
  @Test fun options() { assertEquals(0, CrossfadeRules.clampSeconds(-3)); assertEquals(12, CrossfadeRules.clampSeconds(40)); assertEquals(6, CrossfadeRules.clampSeconds(6)) }
  @Test fun fadeLength() { assertEquals(0, CrossfadeRules.fadeMs(0, 200_000)); assertEquals(8_000, CrossfadeRules.fadeMs(8, 200_000)); assertEquals(3_000, CrossfadeRules.fadeMs(12, 9_000)) }
  @Test fun volumesClamped() {
    assertEquals(1f to 0f, CrossfadeRules.volumes(-50, 1000)); assertEquals(0f to 1f, CrossfadeRules.volumes(5000, 1000))
    val (o, i) = CrossfadeRules.volumes(250, 1000); assertEquals(.75f, o, 1e-4f); assertEquals(.25f, i, 1e-4f)
  }
  @Test fun gaplessAlbumAndPreviews() {
    assertTrue(CrossfadeRules.sameAlbum("Disco", "Disco", "A", "A")); assertFalse(CrossfadeRules.sameAlbum("Disco", "Disco", "A", "B"))
    assertFalse(CrossfadeRules.sameAlbum("", "", "A", "A")); assertTrue(CrossfadeRules.isPreview("preview:x")); assertFalse(CrossfadeRules.isPreview("/a.mp3"))
  }
}

class AutoLibraryTest {
  private val h = listOf(HistoryItem("b", "MP3", "/m/b.mp3"), HistoryItem("A", "MP3", "/m/a.mp3"), HistoryItem("V", "MP4 · 720p", "/m/v.mp4"),
    HistoryItem("Web", "MP3", "https://x"))
  @Test fun rootAndFolders() {
    assertEquals(listOf("fav", "pl", "lib", "recent"), AutoLibrary.children("root", h, emptySet(), emptyMap()).map { it.id })
    assertEquals(listOf("/m/a.mp3", "/m/b.mp3"), AutoLibrary.children("lib", h, emptySet(), emptyMap()).map { it.id })
    assertEquals(listOf("/m/b.mp3"), AutoLibrary.children("fav", h, setOf("/m/b.mp3", "https://x"), emptyMap()).map { it.id })
    val pl = mapOf("Treino" to listOf("/m/a.mp3", "/m/v.mp4", "/gone.mp3"))
    assertEquals(listOf("pl:Treino"), AutoLibrary.children("pl", h, emptySet(), pl).map { it.id })
    assertEquals(listOf("/m/a.mp3"), AutoLibrary.children("pl:Treino", h, emptySet(), pl).map { it.id })
    assertTrue(AutoLibrary.children("nope", h, emptySet(), pl).isEmpty())
  }
  @Test fun voiceSearch() { val h = listOf(com.rise.musique.ui.HistoryItem("Sol", "MP3", "/a", artist = "Ana"), com.rise.musique.ui.HistoryItem("Lua", "MP3", "/b"))
    org.junit.Assert.assertEquals(listOf("/a"), AutoLibrary.search(h, " ana").map { it.uri }); org.junit.Assert.assertEquals(2, AutoLibrary.search(h, "").size) }

}
