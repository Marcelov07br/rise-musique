package com.rise.musique

import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.rise.musique.ui.*
import org.junit.Rule
import org.junit.Test

class UpgradeScreens {
  @get:Rule val p = Paparazzi(deviceConfig = DeviceConfig.PIXEL_6)
  private fun render(s: UiState) = p.snapshot {
    StaticPreview.on = true
    RiseTheme(true) { RiseApp(s, Actions()) }
  }
  @Test fun library() = render(UiState(history = sampleHistory.mapIndexed { i, h -> h.copy(uri = "u$i") },
    favorites = setOf("u0"), playlists = mapOf("Treino" to listOf("u0", "u2"))))
  @Test fun mediaChoice() = render(UiState(page = "Buscar", results = sampleResults, selected = setOf("a")))
  @Test fun audioQuality() = render(UiState(page = "Buscar", results = sampleResults, selected = setOf("a"), choices = DownloadChoices(true, true, true)))
  @Test fun videoQuality() = render(UiState(page = "Buscar", mode = Mode.Video, quality = "1080p", results = sampleResults, choices = DownloadChoices(true, false, true)))
  @Test fun flac() = render(UiState(page = "Buscar", format = "FLAC", choices = DownloadChoices(true, true), results = sampleResults))
}
