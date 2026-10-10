package com.rise.musique

import com.rise.musique.ui.*
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.*

class Round15Test {
  private val sr = 48000.0

  @Test fun peakingFilterHitsItsGainAtCenter() {
    val b = Biquad.peaking(1000.0, Eq.Q, 6.0, sr)
    assertEquals(6.0, b.magnitudeDb(1000.0, sr), .05)
    assertEquals(0.0, b.magnitudeDb(20.0, sr), .3)
    assertEquals(0.0, b.magnitudeDb(16000.0, sr), .3)
  }

  @Test fun flatEqIsTransparentAndAutoPreampCancelsBoost() {
    assertTrue(Eq.filters(EqSettings(on = true), sr).isEmpty())
    val bass = EqSettings(on = true, bands = Eq.PRESETS["Bass"]!!, bass = 4f)
    val peak = Eq.peakBoost(bass)
    assertTrue("bass preset boosts", peak > 5f)
    assertEquals(-peak, bass.effectivePreamp(), .01f)
    // with auto preamp, nowhere on the curve goes above 0 dB → no clipping from the EQ itself
    val worst = (0..200).maxOf { i -> Eq.responseDb(bass, 20.0 * 10.0.pow(i / 200.0 * 3)) }
    assertTrue("max $worst", worst <= .3f)
    assertEquals(-3f, bass.copy(autoPreamp = false, preamp = -3f).effectivePreamp(), 0f)
  }

  @Test fun presetsCoverTheRequestedSet() {
    assertEquals(listOf("Flat", "Bass", "Vocal", "Rock", "Eletrônica", "Acústico", "Podcast"), Eq.PRESETS.keys.toList())
    Eq.PRESETS.values.forEach { assertEquals(10, it.size); it.forEach { v -> assertTrue(abs(v) <= Eq.RANGE) } }
    assertEquals(10, Eq.FREQS.size); assertEquals(31.0, Eq.FREQS.first(), 0.0); assertEquals(16000.0, Eq.FREQS.last(), 0.0)
  }

  @Test fun eqSettingsRoundTrip() {
    val s = EqSettings(true, List(10) { it - 5f }, 3f, -2f, "Personalizado", false)
    assertEquals(s, EqSettings.fromJson(s.toJson()))
  }

  @Test fun limiterNeverExceedsCeiling() {
    val l = Limiter(.97, sr = sr)
    var max = 0.0
    for (i in 0 until 48000) { val x = 1.8 * sin(2 * PI * 440 * i / sr); val y = x * l.gain(abs(x)); max = max(max, abs(y)) }
    assertTrue("max $max", max <= .9700001)
  }

  private fun sineLufs(amp: Double, f: Double = 997.0, sec: Int = 5, channels: Int = 1): Double {
    val m = LoudnessMeter(48000, channels); val n = 48000 * sec
    val buf = FloatArray(n * channels) { k -> (amp * sin(2 * PI * f * (k / channels) / 48000)).toFloat() }
    m.add(buf); return m.integrated()
  }

  @Test fun r128MatchesReferenceSine() {
    // EBU Tech 3341: 1 kHz sine at -20 dBFS (mono) ≈ -23 LUFS (K-weighting adds ~+0.7 dB at 1k, mono = −3 dB)
    val lufs = sineLufs(10.0.pow(-20 / 20.0))
    assertEquals(-23.0, lufs, .5)
    // 6 dB louder → 6 LU louder
    assertEquals(lufs + 6, sineLufs(10.0.pow(-14 / 20.0)), .1)
    // silence is gated out
    assertEquals(-70.0, sineLufs(0.0), .01)
  }

  @Test fun gainTargetsMinus14WithHeadroom() {
    assertEquals(4.0, Loudness.gainFor(-18.0, .5), .01)            // quiet, plenty of headroom
    assertEquals(-6.0, Loudness.gainFor(-8.0, 1.0), .01)           // loud master turned down
    assertTrue(Loudness.gainFor(-30.0, .9) <= 6.0)                 // big boost on a peaky track is capped
    assertEquals(-7.3, Loudness.parseReplayGain("-7.30 dB")!!, 1e-9)
    assertEquals(2.1, Loudness.parseReplayGain("+2.1 dB")!!, 1e-9)
    assertNull(Loudness.parseReplayGain(null))
    assertEquals(-14.0, Loudness.albumLufs(listOf(-14.0, -14.0)), 1e-9)
    assertTrue(Loudness.albumLufs(listOf(-10.0, -20.0)) > -15.0)   // power average leans to the louder track
  }

  @Test fun moodsAndDecades() {
    assertEquals("Calmo", Moods.of(TrackInfo(-16.0, bpm = 80.0)))
    assertEquals("Foco", Moods.of(TrackInfo(-12.0, bpm = 100.0)))
    assertEquals("Festa", Moods.of(TrackInfo(-8.0, bpm = 128.0)))
    assertEquals("Animado", Moods.of(TrackInfo(-12.0, bpm = 125.0)))
    assertNull(Moods.of(TrackInfo(-12.0)))
    assertEquals("1990s", Moods.decade(1994)); assertNull(Moods.decade(0))
  }

  @Test fun smartFoldersGroupAndHideSingles() {
    val h = (1..4).map { HistoryItem("T$it", "MP3", "u$it") } + HistoryItem("V", "MP4 · 720p", "v")
    val info = mapOf("u1" to TrackInfo(-9.0, genre = "Rock", year = 1994, bpm = 128.0), "u2" to TrackInfo(-9.0, genre = "Rock", year = 1999, bpm = 130.0),
      "u3" to TrackInfo(-16.0, genre = "Jazz", year = 2001, bpm = 80.0), "v" to TrackInfo(-9.0, genre = "Rock"))
    val f = smartFolders(UiState(history = h, trackInfo = info))
    assertEquals(listOf("Clima:Festa", "Gênero:Rock", "Década:1990s"), f.map { "${it.kind}:${it.value}" })
    assertTrue(f.none { f2 -> f2.tracks.any { it.video } })
    assertTrue(smartFolders(UiState(history = h, trackInfo = info, hidden = setOf("u1"))).isEmpty())
  }

  @Test fun lyricLineIndex() {
    val l = listOf(Lyrics.Line(1000, "a"), Lyrics.Line(3000, "b"), Lyrics.Line(5000, "c"))
    assertEquals(-1, LyricPlayer.lineAt(l, 500)); assertEquals(0, LyricPlayer.lineAt(l, 1000))
    assertEquals(1, LyricPlayer.lineAt(l, 4999)); assertEquals(2, LyricPlayer.lineAt(l, 99999))
  }

  @Test fun downloadRetryOnlyForTransientErrors() {
    assertTrue(DownloadRetry.shouldRetry("ERROR: Unable to download webpage: <urlopen error timed out>", 0))
    assertTrue(DownloadRetry.shouldRetry("HTTP Error 503: Service Unavailable", 2))
    assertFalse(DownloadRetry.shouldRetry("ERROR: Video unavailable", 0))
    assertFalse(DownloadRetry.shouldRetry("connection reset", DownloadRetry.MAX))
  }

  @Test fun followOnlyNotifiesNewUploadsAfterFirstCheck() {
    val latest = listOf(SearchResult("a", "A", "X", 1, ""), SearchResult("b", "B", "X", 1, ""))
    assertTrue(FollowStore.newOnes(emptySet(), latest, firstCheck = true).isEmpty())
    assertEquals(listOf("b"), FollowStore.newOnes(setOf("a"), latest, firstCheck = false).map { it.url })
  }

  @Test fun widgetLayoutBySize() {
    assertEquals(WidgetLayout.Small, WidgetLayout.of(RiseWidget.SMALL))
    assertEquals(WidgetLayout.Wide, WidgetLayout.of(RiseWidget.WIDE))
    assertEquals(WidgetLayout.Large, WidgetLayout.of(RiseWidget.LARGE))
    assertEquals(0f, WidgetLayout.progress(10, 0), 0f); assertEquals(1f, WidgetLayout.progress(20, 10), 0f)
  }
}
