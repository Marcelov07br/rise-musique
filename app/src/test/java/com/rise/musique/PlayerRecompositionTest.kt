package com.rise.musique

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rise.musique.ui.*
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** 0.20: opening/closing the full player animates only graphicsLayer state — the sheet must not recompose per frame. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "w411dp-h914dp-xxhdpi")
class PlayerRecompositionTest {
  @get:Rule val rule = createComposeRule()
  private var outer = 0; private var theme = 0
  private val now = NowPlaying("x", "Faixa", "Artista", "", playing = false, position = 1000, duration = 200_000, hasNext = true, count = 3, index = 0)

  @Test fun expandAndCollapseDoNotRecomposePerFrame() {
    rule.setContent { androidx.compose.runtime.SideEffect { outer++ }; RiseTheme(true) { androidx.compose.runtime.SideEffect { theme++ }; PlayerSheet(now, Actions()) } }
    rule.waitForIdle()
    PlayerProbe.reset()
    rule.onNodeWithText("Faixa").performClick(); rule.waitForIdle()   // mini → full: the whole spring runs (~40+ frames)
    val open = PlayerProbe.total()
    println("PROBE after-open=$open faixa=" + rule.onAllNodesWithText("Faixa").fetchSemanticsNodes().map { it.boundsInRoot } + " root=" + rule.onRoot().fetchSemanticsNode().boundsInRoot)
    rule.onNodeWithContentDescription("Recolher").assertExists()
    PlayerProbe.reset()
    rule.onNodeWithContentDescription("Recolher").performClick(); rule.waitForIdle()  // full → mini
    val close = PlayerProbe.total()
    rule.onNodeWithText("Faixa").assertExists()
    println("PROBE open=$open close=$close sheet=${PlayerProbe.sheet} body=${PlayerProbe.body} inner=${PlayerProbe.sheetBody} outer=$outer theme=$theme")
    // Only the 7 threshold crossings (mini/glass/full/background/settled…) may recompose the two scopes once each — never once per frame (~45 frames).
    assertTrue("open recomposed $open times", open <= 18)
    assertTrue("close recomposed $close times", close <= 18)
  }
}
