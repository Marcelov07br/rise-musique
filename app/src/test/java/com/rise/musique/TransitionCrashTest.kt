package com.rise.musique

import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rise.musique.ui.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Regression for 0.13.0 ("SeekableTransitionState has been used in different Transitions") extended in 0.14:
 * real taps through the UI — open album/playlist/artist, back, interrupt mid-flight, tab switches and reselects,
 * theme flips and full removal/re-creation of the UI (config-change-like).
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "w411dp-h2400dp-mdpi")
class TransitionCrashTest {
  @get:Rule val rule = createComposeRule()
  private val album = (1..3).map { HistoryItem("Faixa $it", "M4A", "u:$it", artist = "Fulano", album = "Disco") }

  @Test fun navigateEverywhere() {
    var state by mutableStateOf(UiState(history = album, playlists = mapOf("Treino" to listOf("u:1"))))
    var dark by mutableStateOf(true)
    var alive by mutableStateOf(true)
    rule.setContent { if (alive) RiseTheme(dark) { RiseApp(state, Actions(page = { state = state.copy(page = it) },
      historyView = { state = state.copy(historyView = it) })) } }
    rule.waitForIdle()
    repeat(2) {
      rule.onAllNodesWithText("Disco")[0].performClick(); rule.waitForIdle()               // album (shared element)
      rule.onNodeWithContentDescription("Voltar").performClick(); rule.waitForIdle()
      rule.onAllNodesWithText("Treino")[0].performClick(); rule.mainClock.advanceTimeBy(120) // playlist, interrupted
      rule.onNodeWithContentDescription("Voltar").performClick(); rule.waitForIdle()
      rule.onAllNodesWithText("Fulano")[0].performClick(); rule.waitForIdle()              // artist
      rule.onAllNodesWithText("Disco")[0].performClick(); rule.waitForIdle()               // artist → album
      rule.onAllNodesWithText("Biblioteca")[0].performClick(); rule.waitForIdle()          // tab switch keeps stacks
      rule.onAllNodesWithText("Buscar")[0].performClick(); rule.waitForIdle()
      rule.onAllNodesWithText("Início")[0].performClick(); rule.waitForIdle()              // back to the stacked album
      rule.onAllNodesWithText("Início")[0].performClick(); rule.waitForIdle()              // reselect: pop to root
      rule.onAllNodesWithText("Ouvir agora").assertCountEquals(1)
      dark = !dark; rule.waitForIdle()
      alive = false; rule.waitForIdle(); alive = true; rule.waitForIdle()
    }
  }

  /** 0.15: smart folder (genre/mood/decade) opens through the same single-owner transition. */
  @Test fun smartFolders() {
    val info = album.associate { it.uri to TrackInfo(-9.0, .9, "Rock", 1994, 128.0) }
    var state by mutableStateOf(UiState(history = album, page = "Biblioteca", trackInfo = info))
    rule.setContent { RiseTheme(true) { RiseApp(state, Actions(page = { state = state.copy(page = it) })) } }
    rule.waitForIdle()
    repeat(2) {
      rule.onAllNodesWithText("Rock")[0].performClick(); rule.mainClock.advanceTimeBy(150)
      rule.onNodeWithContentDescription("Voltar").performClick(); rule.waitForIdle()
      rule.onAllNodesWithText("Festa")[0].performClick(); rule.waitForIdle()
      rule.onAllNodesWithText("GÊNERO", substring = true).assertCountEquals(0)
      rule.onNodeWithContentDescription("Voltar").performClick(); rule.waitForIdle()
      rule.onAllNodesWithText("1990s")[0].performClick(); rule.waitForIdle()
      rule.onAllNodesWithText("Biblioteca")[0].performClick(); rule.waitForIdle()   // reselect pops to root
    }
  }
}
