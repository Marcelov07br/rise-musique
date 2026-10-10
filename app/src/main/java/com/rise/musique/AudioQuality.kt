package com.rise.musique

/** "FLAC · 1411 kbps · 44.1 kHz · Estéreo" style label from the decoded track format. Visible for tests. */
object AudioQuality {
  fun codec(mime: String?): String = when {
    mime == null -> ""
    mime.contains("flac", true) -> "FLAC"
    mime.contains("opus", true) -> "Opus"
    mime.contains("vorbis", true) -> "Vorbis"
    mime.contains("mpeg", true) && !mime.contains("mp4", true) -> "MP3"
    mime.contains("mp4a", true) || mime.contains("aac", true) || mime.contains("audio/mp4", true) -> "AAC"
    mime.contains("raw", true) || mime.contains("wav", true) -> "PCM"
    else -> mime.substringAfter('/').uppercase()
  }
  fun label(mime: String?, bitrate: Int, sampleRate: Int, channels: Int): String = listOfNotNull(
    codec(mime).ifBlank { null },
    bitrate.takeIf { it > 0 }?.let { "${(it + 500) / 1000} kbps" },
    sampleRate.takeIf { it > 0 }?.let { r -> val k = r / 1000.0; (if (r % 1000 == 0) "${r / 1000}" else String.format(java.util.Locale.US, "%.1f", k)) + " kHz" },
    when (channels) { 1 -> "Mono"; 2 -> "Estéreo"; in 3..16 -> "$channels canais"; else -> null }
  ).joinToString(" · ")
}
