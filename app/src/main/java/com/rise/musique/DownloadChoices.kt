package com.rise.musique

import com.rise.musique.ui.Mode

/** A remembered default is a suggestion, never an implicit confirmation. */
data class DownloadChoices(val media: Boolean = false, val format: Boolean = false, val quality: Boolean = false) {
  fun chooseMedia() = DownloadChoices(media = true)
  fun chooseFormat() = copy(format = true, quality = false)
  fun chooseQuality() = copy(quality = true)
  fun ready(mode: Mode, audioFormat: String) = media &&
    if (mode == Mode.Video) quality else format && (audioFormat == "FLAC" || quality)
}
