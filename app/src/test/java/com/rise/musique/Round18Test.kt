package com.rise.musique

import com.rise.musique.ui.*
import org.junit.Assert.*
import org.junit.Test

class Round18Test {
  private val ly = listOf(Lyrics.Line(0, "a"), Lyrics.Line(10_000, "b"), Lyrics.Line(20_000, ""), Lyrics.Line(24_000, "c"), Lyrics.Line(40_000, "d"))
  @Test fun snippetLinesFollowTheRange() {
    assertEquals(listOf("b", "c"), Snippet.linesIn(ly, 12_000).map { it.text })       // active line at start + lines inside, blanks skipped
    assertEquals(listOf("b", "c"), Snippet.linesIn(ly, 10_000).map { it.text })       // starts exactly on a line: no duplicate
    assertTrue(Snippet.linesIn(null, 0).isEmpty())
    assertEquals(0L, Snippet.clampStart(5_000, 10_000)); assertEquals(185_000L, Snippet.clampStart(999_000, 200_000)); assertEquals(30_000L, Snippet.clampStart(30_000, 200_000))
  }
  @Test fun coverMotionIsSubtleAndLoopsSeamlessly() {
    val a = CoverMotion.at(0f); val b = CoverMotion.at(1f)
    assertEquals(a.first, b.first, 1e-4f); assertEquals(a.second, b.second, 1e-4f); assertEquals(1f, a.first, 1e-4f)
    (0..100).map { CoverMotion.at(it / 100f) }.forEach { (s, x, y) -> assertTrue(s in 1f..1.0801f); assertTrue(kotlin.math.abs(x) <= .026f && kotlin.math.abs(y) <= .021f) }
  }
  @Test fun fourLauncherShortcuts() {
    assertEquals(listOf("Tocar aleatório", "Estações", "Buscar", "Downloads"), Shortcuts.all.map { it.second })
    assertEquals(4, Shortcuts.all.map { it.first }.distinct().size)
  }
}
