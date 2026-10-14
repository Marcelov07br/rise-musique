package com.rise.musique

import com.rise.musique.ui.MotionArtPrefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.io.IOException

/** 0.26: capas animadas — parsing (respostas reais da API), cache, TTL, erros. */
class Round26Test {
  private val real = """{"url":"https://mvod.itunes.apple.com/itunes-assets/HLSMusic112/v4/1f/76/00/1f7600ac/P517392561_default.m3u8","url_tall":"https://mvod.itunes.apple.com/itunes-assets/HLSMusic122/v4/da/c0/33/dac033fe/P517391562_default.m3u8","artist":"Taylor Swift","album":"Midnights","isCached":true}"""
  private val notFound = """{"message":"No animated artwork found."}"""

  private class FakeHttp(var code: Int = 200, var body: String = "", var fail: Boolean = false) : MotionHttp {
    val urls = mutableListOf<String>()
    override fun get(url: String): Pair<Int, String> { urls += url; if (fail) throw IOException("offline"); return code to body }
  }
  private var now = 1_000_000L
  private fun repo(h: MotionHttp, f: File? = null) = MotionArtworkRepository(f, h, { now }, Dispatchers.Unconfined, "https://x.test")

  @Test fun parsesRealResponse() {
    val a = MotionArtParser.parse(real)!!
    assertTrue(a.videoUrl.endsWith("P517392561_default.m3u8")); assertTrue(a.isHls); assertTrue(a.tallUrl.endsWith(".m3u8"))
  }
  @Test fun prefersMp4AndRejectsJunk() {
    assertEquals("https://a/v.mp4", MotionArtParser.parse("""{"url":"https://a/m.m3u8","mp4":"https://a/v.mp4"}""")!!.videoUrl)
    assertFalse(MotionArtParser.parse("""{"mp4":"https://a/v.mp4"}""")!!.isHls)
    assertNull(MotionArtParser.parse(notFound)); assertNull(MotionArtParser.parse("<html>502</html>")); assertNull(MotionArtParser.parse(""))
    assertNull(MotionArtParser.parse("""{"url":"http://insecure/x.m3u8"}""")); assertNull(MotionArtParser.parse("""{"url":null}"""))
  }
  @Test fun normalizesKeyAndQuery() = runBlocking {
    assertEquals(MotionArtKey.of("Beyoncé", "Renaissance"), MotionArtKey.of("beyonce ", "RENAISSANCE (Deluxe Edition)"))
    assertEquals(MotionArtKey.of("The Weeknd", "After Hours"), MotionArtKey.of("The Weeknd feat. Daft Punk", "After Hours - Single"))
    val h = FakeHttp(body = real); repo(h).find("Taylor Swift", "Midnights (3am Edition)")
    assertEquals("https://x.test/api/v1/artwork/search?artist=Taylor%20Swift&album=Midnights", h.urls.single())
  }
  @Test fun cachesPositiveAndPersists() = runBlocking {
    val f = File.createTempFile("motion", ".json").apply { delete() }
    val h = FakeHttp(body = real); val r = repo(h, f)
    assertNotNull(r.find("Taylor Swift", "Midnights")); assertNotNull(r.find("taylor swift", "midnights"))
    assertEquals(1, h.urls.size)
    val r2 = repo(h, f); assertNotNull(r2.find("Taylor Swift", "Midnights")); assertEquals(1, h.urls.size)
    now += MotionArtworkRepository.TTL_FOUND + 1; r2.find("Taylor Swift", "Midnights"); assertEquals(2, h.urls.size)
  }
  @Test fun negativeCachedWithTtl() = runBlocking {
    val h = FakeHttp(404, notFound); val r = repo(h)
    assertNull(r.find("Billie Eilish", "Hit Me Hard and Soft")); assertNull(r.find("Billie Eilish", "Hit Me Hard and Soft"))
    assertEquals(1, h.urls.size)
    now += MotionArtworkRepository.TTL_NONE - 1; r.find("Billie Eilish", "Hit Me Hard and Soft"); assertEquals(1, h.urls.size)
    now += 2; h.code = 200; h.body = real; assertNotNull(r.find("Billie Eilish", "Hit Me Hard and Soft")); assertEquals(2, h.urls.size)
  }
  @Test fun errorsBecomeNoneWithShortTtl() = runBlocking {
    val h = FakeHttp(fail = true); val r = repo(h)
    assertNull(r.find("A", "B")); assertNull(r.find("A", "B")); assertEquals(1, h.urls.size)
    assertEquals(MotionArtworkRepository.TTL_ERROR, r.cached("A", "B")!!.ttl)
    now += MotionArtworkRepository.TTL_ERROR + 1; h.fail = false; h.code = 503; h.body = "oops"
    assertNull(r.find("A", "B")); assertEquals(MotionArtworkRepository.TTL_ERROR, r.cached("A", "B")!!.ttl)
    now += MotionArtworkRepository.TTL_ERROR + 1; h.code = 200; h.body = "{not json"
    assertNull(r.find("A", "B")); assertEquals(MotionArtworkRepository.TTL_ERROR, r.cached("A", "B")!!.ttl)
    assertNull(r.find("", "B")); assertNull(r.find("A", "  ")); assertEquals(3, h.urls.size)
  }
  @Test fun corruptCacheFileIsIgnored() = runBlocking {
    val f = File.createTempFile("motion", ".json").apply { writeText("garbage") }
    val h = FakeHttp(body = real); assertNotNull(repo(h, f).find("Taylor Swift", "Midnights")); assertEquals(1, h.urls.size)
  }
  @Test fun settingRules() {
    assertTrue(MotionArtPrefs.allowed(2, false, false)); assertTrue(MotionArtPrefs.allowed(1, false, true))
    assertFalse(MotionArtPrefs.allowed(1, false, false)); assertFalse(MotionArtPrefs.allowed(0, false, true))
    assertFalse(MotionArtPrefs.allowed(2, true, true))
  }
}
