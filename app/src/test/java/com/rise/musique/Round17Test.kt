package com.rise.musique

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.rise.musique.ui.*
import org.junit.Assert.*
import org.junit.Test

class Round17Test {
  private val lib = (1..6).map { HistoryItem("Faixa $it", "MP3", "u$it", artist = if (it <= 4) "Ana" else "Bia", album = "Disco ${it % 2}") }

  @Test fun softTintsAreMutedAndThemeClamped() {
    listOf(Color.Red, Color(0xFF00FF66), Color(0xFF2040FF), Color.Yellow, Color.Black).forEach { base ->
      val d = Tint.hsl(Tint.soft(base, dark = true)); val l = Tint.hsl(Tint.soft(base, dark = false))
      assertTrue("chroma ${d[1]}", d[1] <= .27f); assertTrue(l[1] <= .27f)
      assertEquals(.22f, d[2], .02f); assertEquals(.88f, l[2], .02f)
    }
    assertTrue(Tint.hsl(Tint.scrim(Color.Red))[2] < .25f)   // white text stays readable over the scrim
  }

  @Test fun mixOfTintedAndNeutralCards() {
    val keys = (1..300).map { "cover-al-Album $it" }
    val ratio = keys.count(Tint::tinted) / 300f
    assertTrue("ratio $ratio", ratio in .55f..0.78f)
    assertEquals(Tint.seeded("x"), Tint.seeded("x"))
    assertEquals(Color(0xFF16161A), contentFor(Color(0xFFE8E2EA))); assertEquals(Color.White, contentFor(Color(0xFF2A2630)))
  }

  @Test fun stationsFromStatsMoodsAndDiscover() {
    val info = mapOf("u1" to TrackInfo(-9.0, bpm = 128.0), "u2" to TrackInfo(-9.0, bpm = 130.0), "u3" to TrackInfo(-16.0, bpm = 80.0))
    val st = Stations.build(lib, StatsSummary(topArtists = listOf(Ranked("Bia", "", 9, 1), Ranked("Ana", "", 5, 1))), info)
    assertEquals(listOf("Estação de Bia", "Estação de Ana", "Mix Festa", "Descobertas"), st.map { it.name })
    assertTrue(st.first().seeds.all { it.artist == "Bia" })
    assertFalse(st.last().mixLocal); assertTrue(st.first().mixLocal)
    // without stats: most common artist in the library
    assertEquals("Estação de Ana", Stations.build(lib, null, emptyMap()).first().name)
    assertTrue(Stations.build(emptyList(), null, emptyMap()).isEmpty())
  }

  @Test fun stationQueueInterleavesAndMixesLocal() {
    fun r(id: String) = SearchResult(id, id, "", 0, "")
    val q = StationQueue.interleave(listOf(listOf(r("a1"), r("a2"), r("x")), listOf(r("b1"), r("x"), r("b3"))))
    assertEquals(listOf("a1", "b1", "a2", "x", "b3"), q.map { it.url })
    val mixed = StationQueue.mix(q, lib.take(2), every = 2)
    assertEquals(listOf("u1", "a1", "b1", "u2", "a2", "x", "b3"), mixed.map { (it as? HistoryItem)?.uri ?: (it as SearchResult).url })
  }

  @Test fun playerCoverFitsEverySize() {
    assertEquals(304.dp, PlayerLayout.art(360.dp, 330.dp))   // small: limited by height (space − 16dp)
    assertEquals(356.dp, PlayerLayout.art(412.dp, 500.dp))   // regular: full width minus margins
    assertEquals(420.dp, PlayerLayout.art(600.dp, 900.dp))   // never above 420dp
    assertEquals(120.dp, PlayerLayout.art(360.dp, 60.dp))
  }

  @Test fun heroesCoverBackStationsAndNew() {
    val st = Stations.build(lib, null, emptyMap())
    val h = heroItems(UiState(history = lib), lib, st, Actions())
    assertEquals(listOf("Volte a ouvir", "Feito pra você", "Feito pra você", "Adicionado recentemente"), h.map { it.label })
    assertTrue(h.size <= 5)
  }
}
