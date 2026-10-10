package com.rise.musique

import com.rise.musique.ui.Mode
import org.junit.Assert.*
import org.junit.Test

class DownloadChoicesTest {
  @Test fun rememberedDefaultsDoNotAuthorizeDownload() {
    assertFalse(DownloadChoices().ready(Mode.Musica, "MP3"))
    assertFalse(DownloadChoices().ready(Mode.Video, "MP4"))
  }
  @Test fun musicNeedsThreeExplicitChoices() {
    val media = DownloadChoices().chooseMedia()
    assertFalse(media.ready(Mode.Musica, "MP3"))
    val format = media.chooseFormat()
    assertFalse(format.ready(Mode.Musica, "MP3"))
    assertTrue(format.chooseQuality().ready(Mode.Musica, "MP3"))
  }
  @Test fun videoDoesNotNeedAudioFormat() {
    assertTrue(DownloadChoices().chooseMedia().chooseQuality().ready(Mode.Video, "MP4"))
  }
  @Test fun flacDoesNotNeedQuality() {
    assertFalse(DownloadChoices().chooseMedia().ready(Mode.Musica, "FLAC"))
    assertTrue(DownloadChoices().chooseMedia().chooseFormat().ready(Mode.Musica, "FLAC"))
  }
  @Test fun switchingMediaResetsFormatAndQuality() {
    val choices = DownloadChoices(true, true, true).chooseMedia()
    assertFalse(choices.ready(Mode.Video, "MP4"))
    assertFalse(choices.ready(Mode.Musica, "MP3"))
  }
  @Test fun switchingFromFlacRequiresQualityAgain() {
    assertFalse(DownloadChoices(true, true, true).chooseFormat().ready(Mode.Musica, "MP3"))
  }
}
