package com.rise.musique

import com.rise.musique.ui.*
import org.junit.Assert.*
import org.junit.Test

class Round22Test {
  private fun h(i: Int, artist: String, album: String = "Disco $artist") = HistoryItem("Faixa $i", "MP3", "u$i", artist = artist, album = album)
  private val lib = (0 until 12).map { h(it, if (it < 6) "Marisa Monte" else "Tim Maia") }

  @Test fun forYouMixesLikesAndLibrary() {
    val now = 100L * 24 * 3600 * 1000
    val fy = ForYou.build(lib, setOf("u0", "u1", "u7"), mapOf("u0" to now), round = 3, now = now)
    assertTrue(fy.any { it.kind == "likes" && it.seeds.map { s -> s.uri }.toSet() == setOf("u0", "u1", "u7") })
    assertTrue(fy.any { it.name.startsWith("Parecidos com") })
    val old = fy.first { it.kind == "rediscover" }
    assertFalse("tocada agora não entra em Redescobrir", old.seeds.any { it.uri == "u0" })
    assertTrue(old.localOnly && !fy.first { it.name.startsWith("Parecidos") }.localOnly)
    assertEquals("mesma rodada, mesmo resultado", fy.map { it.id }, ForYou.build(lib, setOf("u0", "u1", "u7"), mapOf("u0" to now), 3, now).map { it.id })
    assertNotEquals("puxar para atualizar muda os ids (recarrega)", fy.map { it.id }, ForYou.build(lib, setOf("u0", "u1", "u7"), mapOf("u0" to now), 4, now).map { it.id })
    assertTrue(ForYou.build(emptyList(), emptySet(), emptyMap(), 0).isEmpty())
  }

  @Test fun queueDragTarget() {
    assertEquals(0, QueueMotion.target(0, 10f, 100f, 5))
    assertEquals(1, QueueMotion.target(0, 60f, 100f, 5))
    assertEquals(4, QueueMotion.target(2, 900f, 100f, 5))
    assertEquals(0, QueueMotion.target(2, -900f, 100f, 5))
  }

  @Test fun queueKeysStayUniqueForRepeats() {
    val e = queueEntries(listOf(h(1, "a"), h(2, "b"), h(1, "a")))
    assertEquals(listOf("u1#1", "u2#1", "u1#2"), e.map { it.key })
  }

  @Test fun smartQueueSkipsWhatIsQueued() {
    val r = listOf(SearchResult("https://www.youtube.com/watch?v=AAAAAAAAAAA", "a", "x", 1, "", "song"),
      SearchResult("https://www.youtube.com/watch?v=BBBBBBBBBBB", "b", "x", 1, "", "song"),
      SearchResult("https://www.youtube.com/watch?v=BBBBBBBBBBB", "b again", "x", 1, "", "song"),
      SearchResult("https://music.youtube.com/browse/MPREb_x", "album", "x", 0, "", "album"))
    val picked = SmartQueue.pick(r, setOf("stream:https://www.youtube.com/watch?v=AAAAAAAAAAA", "/sdcard/x.mp3"))
    assertEquals(listOf("b"), picked.map { it.title })
  }

  @Test fun sleepFadeIsGradual() {
    assertEquals(1f, PlaybackService.fadeVolume(40_000, 30_000), 0f)
    assertEquals(.25f, PlaybackService.fadeVolume(15_000, 30_000), 1e-4f)
    assertEquals(0f, PlaybackService.fadeVolume(-5, 30_000), 0f)
  }

  @Test fun historyGroupsByDayAndCollapsesRepeats() {
    val now = java.util.Calendar.getInstance().apply { set(2026, 9, 14, 15, 0) }.timeInMillis
    val plays = listOf(Play("u1", "A", "x", 1, now - 60_000), Play("u1", "A", "x", 1, now - 120_000), Play("u2", "B", "y", 1, now - 26 * 3_600_000L))
    val g = ListeningHistory.groups(plays, now)
    assertEquals(listOf("Hoje", "Ontem"), g.map { it.first })
    assertEquals(1, g[0].second.size)
  }

  @Test fun snapshotKeepsStreams() {
    val snap = PlaybackSnapshot(listOf(HistoryItem("A", "", "stream:https://www.youtube.com/watch?v=AAAAAAAAAAA"), HistoryItem("B", "", "/x/b.mp3")), 1, 5_000, 0, false)
    val back = PlaybackSnapshot.parse(snap.json())
    assertEquals(2, back.tracks.size); assertEquals(1, back.index); assertEquals(5_000, back.position)
  }
}
