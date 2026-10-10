package com.rise.musique

import org.junit.Assert.*
import org.junit.Test

class AudioStreamParseTest {
  @Test fun parsesUrlAndHeaders() {
    val s = Engine.parseStream("[info] x\n{\"url\":\"https://rr1.googlevideo.com/videoplayback?a=1\",\"http_headers\":{\"User-Agent\":\"UA-X\",\"Accept\":\"*/*\"}}")
    assertEquals("https://rr1.googlevideo.com/videoplayback?a=1", s.url)
    assertEquals("UA-X", s.headers["User-Agent"])
  }
  @Test(expected = IllegalArgumentException::class) fun rejectsMissingUrl() { Engine.parseStream("{\"title\":\"a\"}") }
}
