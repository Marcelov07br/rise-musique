package com.rise.musique

import com.rise.musique.ui.QueueMotion
import org.junit.Assert.*
import org.junit.Test

class Round24Test {
  @Test fun edgeAutoScroll() {
    // Viewport 0..1000, edge 100: middle → still, near bottom → down, near top → up, capped at max.
    assertEquals(0f, QueueMotion.edgeSpeed(400f, 468f, 0f, 1000f, 100f), 0f)
    assertTrue(QueueMotion.edgeSpeed(880f, 950f, 0f, 1000f, 100f) > 0f)
    assertTrue(QueueMotion.edgeSpeed(20f, 88f, 0f, 1000f, 100f) < 0f)
    assertEquals(22f, QueueMotion.edgeSpeed(990f, 1200f, 0f, 1000f, 100f), 0f)
    assertEquals(0f, QueueMotion.edgeSpeed(990f, 1200f, 0f, 1000f, 0f), 0f)
  }

  @Test fun friendlyNetworkErrors() {
    assertTrue(NetErrors.friendly(java.net.UnknownHostException("music.youtube.com")).startsWith("Sem conexão"))
    assertTrue(NetErrors.isOffline(RuntimeException("x", java.net.ConnectException())))
    assertTrue(NetErrors.friendly(java.net.SocketTimeoutException()).contains("demorou"))
    assertEquals("linha final", NetErrors.friendly(RuntimeException("ERROR: x\nlinha final\n")))
    assertEquals("Algo deu errado. Tente de novo.", NetErrors.friendly(RuntimeException()))
  }

  @Test fun logRingBufferKeepsNewestHalf() {
    val f = java.io.File.createTempFile("rise", ".log")
    f.writeText((1..400).joinToString("") { "linha $it\n" })
    ErrorLog.trim(f, 1000)
    val t = f.readText()
    assertTrue(f.length() <= 1000); assertTrue(t.endsWith("linha 400\n")); assertTrue(t.startsWith("linha "))
    assertFalse(t.contains("linha 1\n"))
    f.delete()
  }

  @Test fun prefetchPicksDistinctNonBlank() {
    assertEquals(listOf("a", "c"), ArtPrefetch.pick(listOf("", "a", "a", "b", "c", "d"), 2, setOf("b")))
  }

  @Test fun sharedSpringIsClamped() {
    val s = com.rise.musique.ui.Motion.spring<Float>(300f, damping = 0.2f) as androidx.compose.animation.core.SpringSpec<Float>
    assertEquals(com.rise.musique.ui.Motion.PopBounce, s.dampingRatio, 0f)
    assertEquals(com.rise.musique.ui.Motion.Bounce, (com.rise.musique.ui.Motion.spring<Float>() as androidx.compose.animation.core.SpringSpec<Float>).dampingRatio, 0f)
  }
}
