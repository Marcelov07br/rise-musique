package com.rise.musique

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.util.Calendar

class BeatAnalysisTest {
  /** Synthetic 120 BPM click track (accent on beat 1 of each bar) starting at [offsetMs]. */
  private fun clicks(bpm: Double, seconds: Int, offsetMs: Int, rate: Int = 11025): FloatArray {
    val out = FloatArray(rate * seconds); val beat = 60.0 / bpm
    var t = offsetMs / 1000.0; var k = 0
    while (t < seconds) { val start = (t * rate).toInt(); val amp = if (k % 4 == 0) 1f else .45f
      for (i in 0 until 400) if (start + i < out.size) out[start + i] = amp * (1 - i / 400f) * (if (i % 2 == 0) 1 else -1)
      t += beat; k++ }
    return out
  }
  private fun grid(bpm: Double, off: Int) = BeatAnalysis.analyze(BeatAnalysis.onsets(BeatAnalysis.envelope(clicks(bpm, 40, off), 11025)))!!
  @Test fun findsTempo() {
    listOf(100.0, 120.0, 128.0, 140.0).forEach { b -> assertEquals("bpm $b", b, grid(b, 0).bpm, 1.5) }
  }
  @Test fun findsDownbeatAndConfidence() {
    val g = grid(120.0, 250)
    assertEquals(250.0, (g.firstDownbeatMs % 2000).toDouble(), 30.0)
    assertTrue(g.confidence > .25)
    val noise = java.util.Random(1).let { r -> FloatArray(11025 * 30) { (r.nextFloat() - .5f) * .2f } }
    val n = BeatAnalysis.analyze(BeatAnalysis.onsets(BeatAnalysis.envelope(noise, 11025)))
    assertTrue(n == null || n.confidence < g.confidence)
  }
  @Test fun gridHelpers() {
    val g = BeatGrid(120.0, 500, .9)
    assertEquals(500, g.downbeatAtOrBefore(100)); assertEquals(4500, g.downbeatAtOrBefore(5000))
    assertEquals(8000, BeatAnalysis.barsOverlap(g, 8000, 60_000)); assertEquals(4000, BeatAnalysis.barsOverlap(g, 1000, 60_000))
    assertEquals(1.04f, BeatAnalysis.tempoRatio(124.8, 120.0), .001f); assertEquals(1f, BeatAnalysis.tempoRatio(150.0, 120.0), 0f)
    assertEquals(1.0f, BeatAnalysis.tempoRatio(120.0, 60.0), .001f)
    assertEquals(g, BeatGrid.of(g.json()))
  }
}

class LastFmTest {
  @Test fun signatureSortsAndSkipsFormat() {
    val sig = LastFm.signature(mapOf("method" to "auth.getMobileSession", "username" to "u", "password" to "p", "api_key" to "k", "format" to "json"), "s")
    val expected = java.security.MessageDigest.getInstance("MD5").digest("api_keykmethodauth.getMobileSessionpasswordpusernameus".toByteArray())
      .joinToString("") { "%02x".format(it) }
    assertEquals(expected, sig)
  }
  @Test fun scrobbleRules() {
    assertFalse(ScrobbleRules.eligible(30_000, 30_000)); assertFalse(ScrobbleRules.eligible(200_000, 99_000))
    assertTrue(ScrobbleRules.eligible(200_000, 100_000)); assertTrue(ScrobbleRules.eligible(900_000, 240_000)); assertFalse(ScrobbleRules.eligible(0, 999_000))
  }
  @Test fun batchParams() {
    val p = LastFm.scrobbleParams(listOf(Scrobble("A", "T", "", 10, 0), Scrobble("B", "U", "Al", 20, 180)))
    assertEquals("A", p["artist[0]"]); assertEquals("Al", p["album[1]"]); assertNull(p["album[0]"]); assertEquals("180", p["duration[1]"])
  }
}

class RadioTest {
  @Test fun parsesMixAndIds() {
    val json = javaClass.classLoader!!.getResource("radio.json")!!.readText()
    val r = Engine.parseEntries(json)
    assertEquals(4, r.size); assertTrue(r.all { it.title.isNotBlank() && it.url.startsWith("https://") })
    assertEquals("dQw4w9WgXcQ", Engine.videoId("https://music.youtube.com/watch?v=dQw4w9WgXcQ&si=x"))
    assertEquals("dQw4w9WgXcQ", Engine.videoId("https://youtu.be/dQw4w9WgXcQ"))
    assertNull(Engine.videoId("https://example.com"))
    assertEquals("https://www.youtube.com/watch?v=abc&list=RDAMVMabc", Engine.radioUrl("abc"))
  }
}

class RecapTest {
  @Test fun monthAndYear() {
    val now = Calendar.getInstance().apply { set(2026, Calendar.OCTOBER, 20, 12, 0) }.timeInMillis
    val sep = Calendar.getInstance().apply { set(2026, Calendar.SEPTEMBER, 3, 12, 0) }.timeInMillis
    val lastYear = Calendar.getInstance().apply { set(2025, Calendar.OCTOBER, 3, 12, 0) }.timeInMillis
    val plays = listOf(Play("1", "Um", "Ana", 120_000, now), Play("1", "Um", "Ana", 120_000, sep), Play("2", "Dois", "Bia", 60_000, lastYear))
    val m = RecapBuilder.build(plays, false, now); val y = RecapBuilder.build(plays, true, now)
    assertEquals("Seu outubro", m.title); assertEquals(1, m.plays); assertEquals(2, m.minutes)
    assertEquals("Seu 2026", y.title); assertEquals(2, y.plays); assertEquals("Ana", y.topArtist?.name)
  }
}

class CutTest {
  @Test fun storyboardFrames() {
    val b = Storyboard(listOf("a" to 100.0, "b" to 50.0), 80, 45, 10, 10)
    assertEquals(Triple(0, 0, 0), b.frameAt(0.0)); assertEquals(Triple(0, 5, 1), b.frameAt(15.2)); assertEquals(Triple(1, 0, 0), b.frameAt(100.1))
    assertEquals(Triple(1, 9, 9), b.frameAt(9999.0))
  }
  @Test fun picksStoryboard() {
    val j = JSONObject("""{"formats":[{"format_note":"storyboard","width":48,"height":27,"rows":10,"columns":10,"fragments":[{"url":"u3","duration":213}]},
      {"format_note":"storyboard","width":80,"height":45,"rows":10,"columns":10,"fragments":[{"url":"u2a","duration":197},{"url":"u2b","duration":16}]},{"format_id":"140"}]}""")
    val b = CutInfo.storyboard(j)!!; assertEquals(80, b.width); assertEquals(2, b.sheets.size)
    assertNull(CutInfo.storyboard(JSONObject("{}")))
  }
  @Test fun peaksNormalised() {
    val p = CutInfo.peaks(floatArrayOf(0f, .5f, -1f, .25f), 4, 2); assertEquals(listOf(.5f, 1f), p)
    assertEquals(3, CutInfo.peaks(FloatArray(0), 0, 3).size)
  }
}

class MotionTest {
  @Test fun curves() {
    val d = com.rise.musique.ui.Motion.EmphasizedDecelerate; val a = com.rise.musique.ui.Motion.EmphasizedAccelerate
    assertEquals(0f, d.transform(0f), 1e-3f); assertEquals(1f, d.transform(1f), 1e-3f)
    assertTrue(d.transform(.3f) > .7f); assertTrue(a.transform(.3f) < .1f)
    for (i in 0..100) { val t = i / 100f; assertTrue(d.transform(t) in -0.001f..1.001f) } // never overshoots
  }
}
