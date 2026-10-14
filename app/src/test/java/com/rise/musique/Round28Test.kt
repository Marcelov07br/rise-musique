package com.rise.musique

import com.rise.musique.ui.MiniLook
import com.rise.musique.ui.glass.glassResolutionScale
import com.rise.musique.ui.glass.glassSaturation
import com.rise.musique.ui.glass.shouldUseTranslucentGlassFallback
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Round28Test {
  @Test fun fallbackWithoutRenderEffectOrBackdrop() {
    assertTrue(shouldUseTranslucentGlassFallback(renderEffectSupported = false, hasBackdrop = true))
    assertTrue(shouldUseTranslucentGlassFallback(renderEffectSupported = true, hasBackdrop = false))
    assertFalse(shouldUseTranslucentGlassFallback(renderEffectSupported = true, hasBackdrop = true))
  }
  @Test fun squareDefaults() {
    assertEquals(1.6f, glassSaturation(1.2f), 1e-4f)
    assertEquals(1f - .25f * .7f, glassResolutionScale(2f), 1e-4f)
    assertEquals(.30f, glassResolutionScale(40f), 1e-4f)
  }
  @Test fun miniGeometry() { assertEquals(56f, MiniLook.Height.value, 0f) }
}
