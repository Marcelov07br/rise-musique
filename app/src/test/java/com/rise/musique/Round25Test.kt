package com.rise.musique

import com.rise.musique.ui.*
import org.junit.Assert.*
import org.junit.Test

class Round25Test {
  @Test fun reduceMotionRule() {
    assertTrue(ReduceMotion.reduced(true, 1f)); assertTrue(ReduceMotion.reduced(false, 0f)); assertFalse(ReduceMotion.reduced(false, .5f))
  }
  @Test fun particlesStayElegant() {
    assertEquals(0f, LikeBurst.dist(0f), 0f); assertEquals(1f, LikeBurst.dist(1f), 1e-6f); assertEquals(1f, LikeBurst.dist(5f), 1e-6f)
    assertEquals(1f, LikeBurst.alpha(.2f), 0f); assertEquals(0f, LikeBurst.alpha(1f), 1e-6f)
    assertTrue(LikeBurst.Count in 6..14)
  }
  @Test fun playMorphEndsAndClamps() {
    assertEquals(.22f, PlayMorph.point(0f, 0), 1e-6f); assertEquals(.26f, PlayMorph.point(1f, 0), 1e-6f)
    assertEquals(PlayMorph.point(1f, 3), PlayMorph.point(3f, 3), 0f) // never overshoots the shape
  }
  @Test fun tiltIsClamped() {
    assertEquals(0f, TiltMath.axis(3f, 3f), 0f); assertEquals(1f, TiltMath.axis(30f, 0f), 0f); assertEquals(-1f, TiltMath.axis(-30f, 0f), 0f)
    assertTrue(TiltMath.MaxDeg <= 3f)
  }
  @Test fun flightIgnoresEmptyBounds() {
    CoverFlight.forget(); CoverFlight.launch(null, "k", ""); assertNull(CoverFlight.origin)
    CoverFlight.launch(androidx.compose.ui.geometry.Rect(0f, 0f, 60f, 60f), "k", "a"); assertNotNull(CoverFlight.origin); CoverFlight.forget(); assertNull(CoverFlight.origin)
  }
}
