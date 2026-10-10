package com.rise.musique

object DownloadStage {
  fun fromLine(line: String, previous: String = "Preparando"): String = when {
    line.startsWith("[download]") -> "Baixando"
    line.startsWith("[ExtractAudio]") || line.startsWith("[Merger]") || line.startsWith("[VideoConvertor]") || line.trimStart().startsWith("size=") -> "Convertendo"
    line.startsWith("[EmbedThumbnail]") || line.startsWith("[Metadata]") || line.startsWith("[ThumbnailsConvertor]") || line.startsWith("[Fixup") -> "Finalizando"
    else -> previous
  }
}
