package com.rise.musique

import androidx.compose.runtime.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rise.musique.ui.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** Regression for 0.13.0: "SeekableTransitionState has been used in different Transitions" on launch/recomposition. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
class TransitionCrashTest {
  @get:Rule val rule = createComposeRule()
  private val album = (1..8).map { HistoryItem("Faixa $it", "M4A", "u:$it", artist = "A", album = "Disco") }

  @Test fun openBackThemeAndRecreate() {
    var state by mutableStateOf(UiState(history = album, playlists = mapOf("Treino" to listOf("u:1"))))
    var dark by mutableStateOf(true)
    var alive by mutableStateOf(true)
    rule.setContent { if (alive) RiseTheme(dark) { RiseApp(state, Actions(albumFilter = { state = state.copy(albumFilter = it) },
      historyView = { state = state.copy(historyView = it) })) } }
    rule.waitForIdle()
    repeat(3) {
      state = state.copy(albumFilter = "Disco"); rule.waitForIdle()
      state = state.copy(albumFilter = ""); rule.waitForIdle()
      state = state.copy(historyView = "Treino"); rule.mainClock.advanceTimeBy(120) // interrupt mid-flight
      state = state.copy(historyView = "Tudo"); rule.waitForIdle()
      dark = !dark; rule.waitForIdle()
      state = state.copy(page = "Buscar"); rule.waitForIdle(); state = state.copy(page = "Biblioteca"); rule.waitForIdle()
      alive = false; rule.waitForIdle(); alive = true; rule.waitForIdle() // config-change-like recreation
    }
  }
}
