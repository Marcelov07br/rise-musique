@file:androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])
package com.rise.musique

import android.content.Context
import android.net.Uri
import androidx.media3.extractor.metadata.id3.ApicFrame
import androidx.media3.extractor.metadata.id3.Id3Decoder
import androidx.media3.extractor.metadata.id3.TextInformationFrame
import java.io.File
import java.io.InputStream

/** Import-side support for hi-res formats: tags/covers for DSF/DFF/AIFF (ID3v2 inside) and APE/WavPack → FLAC via the bundled FFmpeg CLI. */
object Lossless {
  val native = setOf("dsf", "dff", "aif", "aiff", "aifc")
  val transcode = setOf("ape", "wv")
  data class Tags(val title: String = "", val artist: String = "", val album: String = "", val cover: ByteArray? = null, val durationMs: Long = 0, val quality: String = "")

  /** Reads the header (and the ID3 block it points to) from a stream. Visible for tests with plain byte streams. */
  fun tags(open: () -> InputStream?): Tags? {
    val head = open()?.use { read(it, 64 * 1024) } ?: return null
    val l = HiRes.parseDsf(head) ?: HiRes.parseDff(head) ?: HiRes.parseAiff(head) ?: return null
    val quality = if (l.mime in HiRes.DSD) HiRes.dsdLabel(l.bitsOrDsdRate, HiRes.outRate(l.rate), false) else "AIFF · ${l.bitsOrDsdRate}-bit · ${"%.1f".format(java.util.Locale.ROOT, l.rate / 1000.0)} kHz"
    val dur = l.durationUs.takeIf { it > 0 }?.div(1000) ?: 0
    var tagAt = l.tagStart
    if (tagAt < 0 && head.size > 3) tagAt = findId3(head)
    if (tagAt < 0) return Tags(durationMs = dur, quality = quality)
    val id3 = open()?.use { s -> skip(s, tagAt); read(s, 16 * 1024 * 1024) } ?: return Tags(durationMs = dur, quality = quality)
    // Tag pointers may land on an IFF chunk header ("ID3 " + size) or a few chunks before it: find the real "ID3" magic.
    val at = (0 until minOf(id3.size - 3, 1 shl 20)).firstOrNull { i -> id3[i] == 'I'.code.toByte() && id3[i + 1] == 'D'.code.toByte() && id3[i + 2] == '3'.code.toByte() && id3[i + 3] in 2..4 }
      ?: return Tags(durationMs = dur, quality = quality)
    val tag = id3.copyOfRange(at, id3.size)
    val meta = runCatching { Id3Decoder().decode(tag, tag.size) }.getOrNull()
    var t = Tags(durationMs = dur, quality = quality)
    if (meta != null) for (i in 0 until meta.length()) when (val e = meta[i]) {
      is TextInformationFrame -> when (e.id) { "TIT2" -> t = t.copy(title = e.values.firstOrNull().orEmpty()); "TPE1" -> t = t.copy(artist = e.values.firstOrNull().orEmpty()); "TALB" -> t = t.copy(album = e.values.firstOrNull().orEmpty()) }
      is ApicFrame -> if (t.cover == null) t = t.copy(cover = e.pictureData)
    }
    return t
  }
  private fun read(s: InputStream, max: Int): ByteArray { val o = java.io.ByteArrayOutputStream(); val b = ByteArray(16384)
    while (o.size() < max) { val n = s.read(b, 0, minOf(b.size, max - o.size())); if (n < 0) break; o.write(b, 0, n) }; return o.toByteArray() }
  private fun skip(s: InputStream, n: Long) { var left = n; while (left > 0) { val k = s.skip(left); if (k <= 0) { if (s.read() < 0) return; left-- } else left -= k } }
  private fun findId3(h: ByteArray): Long { for (i in 0 until h.size - 3) if (h[i] == 'I'.code.toByte() && h[i + 1] == 'D'.code.toByte() && h[i + 2] == '3'.code.toByte() && h[i + 3] < 5) return i.toLong(); return -1 }

  /** APE / WavPack → FLAC (lossless, tags + cover kept) in app storage; returns the new file. */
  fun toFlac(c: Context, uri: Uri, name: String): File {
    val dir = File(c.filesDir, "lossless").apply { mkdirs() }
    val src = File(c.cacheDir, "lossless-src.${name.substringAfterLast('.')}")
    (c.contentResolver.openInputStream(uri) ?: error("Arquivo indisponível")).use { i -> src.outputStream().use { i.copyTo(it) } }
    val out = File(dir, name.substringBeforeLast('.').replace(Regex("[^\\w .()-]"), "_") + "-" + Integer.toHexString(uri.toString().hashCode()) + ".flac")
    try {
      val bin = File(c.applicationInfo.nativeLibraryDir, "libffmpeg.so")
      val libs = File(c.noBackupFilesDir, "youtubedl-android").walkTopDown().firstOrNull { it.name == "libavcodec.so" }?.parentFile
      val pb = ProcessBuilder(bin.absolutePath, "-y", "-v", "error", "-i", src.absolutePath, "-map", "0", "-c:a", "flac", "-c:v", "copy", out.absolutePath).redirectErrorStream(true)
      libs?.let { pb.environment()["LD_LIBRARY_PATH"] = it.absolutePath }
      val p = pb.start(); val log = p.inputStream.bufferedReader().readText()
      if (p.waitFor() != 0 || !out.exists() || out.length() == 0L) { out.delete(); error("Conversão falhou: ${log.take(120)}") }
    } finally { src.delete() }
    return out
  }
}
