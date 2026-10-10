package com.rise.musique

import com.rise.musique.ui.SearchResult
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class Round16Test {
  private fun fixture(n: String) = JSONObject(javaClass.classLoader!!.getResource(n)!!.readText())

  @Test fun parsesYouTubeMusicSongs() {
    val p = MusicSearch.parseMusic(fixture("im_songs.json"), SearchKind.Songs)
    assertEquals(20, p.items.size); assertNotNull(p.next)
    val first = p.items.first()
    assertEquals("Instant Crush (feat. Julian Casablancas)", first.title)
    assertEquals("Daft Punk e Julian Casablancas", first.channel)
    assertEquals("Random Access Memories", first.album)
    assertEquals(338, first.duration); assertEquals("song", first.kind)
    assertEquals("https://music.youtube.com/watch?v=khnokW3Mw24", first.url)
    assertTrue(first.thumb, first.thumb.contains("w544-h544"))
    assertTrue(p.items.all { it.playable && it.duration > 0 })
  }

  @Test fun parsesVideosAlbumsArtistsPlaylists() {
    val v = MusicSearch.parseMusic(fixture("im_videos.json"), SearchKind.Videos).items
    assertEquals("Digital Love", v[0].title); assertEquals(302, v[0].duration); assertEquals("https://i.ytimg.com/vi/4whD6uAryMs/hqdefault.jpg", v[0].thumb)
    val al = MusicSearch.parseMusic(fixture("im_albums.json"), SearchKind.Albums).items
    assertEquals("https://music.youtube.com/browse/MPREb_lZBXOfVv2Tl", al[0].url); assertEquals("album", al[0].kind); assertFalse(al[0].playable)
    assertTrue(al.all { it.url.contains("/browse/MPRE") })
    val ar = MusicSearch.parseMusic(fixture("im_artists.json"), SearchKind.Artists).items
    assertEquals("Daft Punk", ar[0].title); assertEquals("artist", ar[0].kind)
    val pl = MusicSearch.parseMusic(fixture("im_playlists.json"), SearchKind.Playlists).items
    assertTrue(pl.isNotEmpty() && pl.all { it.url.startsWith("https://music.youtube.com/playlist?list=") && !it.url.contains("list=VL") })
  }

  @Test fun youtubeFilterProtobuf() {
    assertEquals("EgIQAQ==", MusicSearch.spFor(SearchQuery("x", SearchKind.Videos)))
    // upload date today (field 1 = 2) + type video + short (field 3 = 1)
    assertEquals("EgYIAhABGAE=", MusicSearch.spFor(SearchQuery("x", SearchKind.Videos, DurationFilter.Short, DateFilter.Today)))
    assertTrue(DurationFilter.Short.accepts(200)); assertFalse(DurationFilter.Short.accepts(300))
    assertTrue(DurationFilter.Medium.accepts(600)); assertTrue(DurationFilter.Long.accepts(1300)); assertFalse(DurationFilter.Long.accepts(1100))
  }

  @Test fun rankingPrefersTheRealRecording() {
    val q = SearchQuery("one more time", SearchKind.Videos)
    val r = listOf(SearchResult("1", "One More Time (Live)", "Fan", 300, "", "video"), SearchResult("2", "One More Time (slowed)", "x", 300, "", "video"),
      SearchResult("3", "One More Time", "Daft Punk - Topic", 320, "", "video"), SearchResult("4", "One More Time (Official Video)", "Daft Punk", 320, "", "video"))
    assertEquals(listOf("3", "4"), MusicSearch.rank(q, r).take(2).map { it.url })
    // asked for live → not penalized
    assertEquals("1", MusicSearch.rank(q.copy(text = "one more time live"), r).first { it.url != "3" }.url)
    assertEquals(r, MusicSearch.rank(q.copy(kind = SearchKind.Albums), r))
  }

  @Test fun hiResThumbnails() {
    assertEquals("https://lh3.googleusercontent.com/abc=w544-h544-l90-rj", MusicSearch.hiRes("https://lh3.googleusercontent.com/abc=w60-h60-l90-rj", null))
    assertEquals("https://i.ytimg.com/vi/ID/hqdefault.jpg", MusicSearch.hiRes("https://i.ytimg.com/vi/ID/hqdefault.jpg?sqp=xx", "ID"))
    assertEquals("https://i.ytimg.com/vi/ID/hqdefault.jpg", MusicSearch.hiRes("", "ID"))
  }

  @Test fun streamUrisAndExpiry() {
    assertEquals("rise-stream://dQw4w9WgXcQ", Streams.uriFor("https://music.youtube.com/watch?v=dQw4w9WgXcQ").toString())
    assertNull(Streams.uriFor("https://example.com/x"))
    assertEquals(1760000000000L, Streams.expiryOf("https://rr1.googlevideo.com/videoplayback?expire=1760000000&itag=140"))
    val now = 1_000_000L
    assertEquals(now + 5 * 60_000, Streams.expiryOf("https://x/y", now))
    assertFalse(Streams.fresh(Streams.Resolved("u", emptyMap(), now + 30_000, "140", "m4a"), now))
    assertTrue(Streams.fresh(Streams.Resolved("u", emptyMap(), now + 3_600_000, "140", "m4a"), now))
    assertFalse(Streams.fresh(null, now))
  }

  @Test fun videoFormatsMergeAndSubtitles() {
    val i = Engine.parseVideo(javaClass.classLoader!!.getResource("video.json")!!.readText())
    assertEquals(listOf(240, 360, 720, 1080), i.formats.map { it.height })
    assertTrue("prefers h264", i.formats.first { it.height == 1080 }.codec.startsWith("avc1"))
    assertEquals("140", i.audio!!.formatId); assertEquals("m4a", i.audio!!.ext)
    assertEquals(listOf("pt (auto)"), i.subtitles.keys.toList())
    assertEquals(1080, VideoQuality.pick(i.formats.map { it.height }, 0))
    assertEquals(360, VideoQuality.pick(listOf(360, 720, 2160), 360))
    assertEquals(720, VideoQuality.pick(listOf(360, 720, 2160), 0))
    assertEquals(1440, VideoQuality.pick(listOf(1440, 2160), 0))
    assertEquals("4K", VideoQuality.label(2160))
  }

  @Test fun pipAspectIsClampedToAndroidLimits() {
    assertEquals(1777 to 1000, PipMath.ratio(16f / 9f))
    assertTrue(PipMath.ratio(5f).first < 2390); assertTrue(PipMath.ratio(.1f).first > 418)
    assertEquals(1777 to 1000, PipMath.ratio(Float.NaN))
  }

  @Test fun voiceLevelAndSafeNames() {
    assertEquals(0f, VoiceLevel.of(-10f), 0f); assertEquals(1f, VoiceLevel.of(20f), 0f); assertEquals(.5f, VoiceLevel.of(4f), .01f)
    assertEquals("AC_DC_ Back in Black", PrefillName.of("AC/DC: Back in Black"))
    assertEquals("Faixa", PrefillName.of("  "))
  }
}
