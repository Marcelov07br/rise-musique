@file:androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])
package com.rise.musique

import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.extractor.*
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Hi-res / lossless containers Media3 lacks: DSF and DFF (DSD) and AIFF/AIFC (big-endian PCM). Each is a simple
 * "header, then one contiguous data chunk" file, so they share [ChunkExtractor]; samples go to the bundled FFmpeg
 * decoder (custom MIME types mapped in FfmpegLibrary). APE / WavPack are transcoded to FLAC on import (see [Lossless]).
 */
object HiRes {
  const val DSD_LSBF_PLANAR = "audio/x-rise-dsd-lsbf-planar"; const val DSD_MSBF_PLANAR = "audio/x-rise-dsd-msbf-planar"; const val DSD_MSBF = "audio/x-rise-dsd-msbf"
  val DSD = setOf(DSD_LSBF_PLANAR, DSD_MSBF_PLANAR, DSD_MSBF)
  /** Parsed layout: where the audio is and how to cut it. Visible for tests. */
  data class Layout(val mime: String, val channels: Int, val rate: Int, /** container rate (DSD: 2 822 400) */ val bitsOrDsdRate: Int,
                    val dataStart: Long, val dataSize: Long, val packetBytes: Int, val bytesPerFrame: Int, val frameRate: Int, val tagStart: Long = -1, val pcmEncoding: Int = Format.NO_VALUE) {
    /** Frames (PCM) or DSD bytes-per-channel per second. */
    val durationUs get() = if (bytesPerFrame <= 0 || frameRate <= 0) C.TIME_UNSET else dataSize / bytesPerFrame * 1_000_000L / frameRate
    fun timeUs(offset: Long) = offset / bytesPerFrame * 1_000_000L / frameRate
  }

  /** "DSD64 · 2.8 MHz → PCM 24/176" style label. */
  fun dsdLabel(dsdRate: Int, outRate: Int, floatOut: Boolean): String {
    val mult = Math.round(dsdRate / 44_100f); val mhz = "%.1f".format(java.util.Locale.ROOT, dsdRate / 1e6)
    return "DSD$mult · $mhz MHz → PCM ${if (floatOut) 24 else 16}/${outRate / 1000}"
  }
  fun outRate(r: Int): Int { var x = r; while (x > 192_000) x /= 2; return x }

  private fun le(b: ByteArray) = ByteBuffer.wrap(b).order(ByteOrder.LITTLE_ENDIAN)
  private fun be(b: ByteArray) = ByteBuffer.wrap(b).order(ByteOrder.BIG_ENDIAN)
  private fun id(b: ByteArray, o: Int) = String(b, o, 4, Charsets.US_ASCII)

  /** DSF: "DSD " (28) + "fmt " (52) + "data" (12 + payload), little-endian; metadata (ID3v2) pointer in the first chunk. */
  fun parseDsf(h: ByteArray): Layout? {
    if (h.size < 92 || id(h, 0) != "DSD " || id(h, 28) != "fmt ") return null
    val b = le(h); val meta = b.getLong(20); val fmtSize = b.getLong(32).toInt()
    val ch = b.getInt(52); val fs = b.getInt(56); val bits = b.getInt(60); val samples = b.getLong(64); val block = b.getInt(72)
    val d = 28 + fmtSize; if (h.size < d + 12 || id(h, d) != "data") return null
    val payload = b.getLong(d + 4) - 12
    val usable = (payload / (block * ch) * (block * ch)).coerceAtLeast(0) // whole blocks; the last one is zero-padded per channel
    return Layout(if (bits == 8) DSD_MSBF_PLANAR else DSD_LSBF_PLANAR, ch, fs / 8, fs, (d + 12).toLong(), usable, block * ch, ch, fs / 8, if (meta > 0) meta else -1)
  }

  /** DSDIFF (.dff): big-endian IFF "FRM8"/"DSD ", PROP/SND with FS and CHNL, then the "DSD " data chunk (DST compression unsupported). */
  fun parseDff(h: ByteArray): Layout? {
    if (h.size < 16 || id(h, 0) != "FRM8" || id(h, 12) != "DSD ") return null
    val b = be(h); var p = 16; var fs = 0; var ch = 0
    while (p + 12 <= h.size) {
      val cid = id(h, p); val size = b.getLong(p + 4)
      when (cid) {
        "PROP" -> { var q = p + 16; val end = (p + 12 + size).toInt().coerceAtMost(h.size)
          while (q + 12 <= end) { val sid = id(h, q); val ss = b.getLong(q + 4)
            if (sid == "FS  " && q + 16 <= h.size) fs = b.getInt(q + 12)
            if (sid == "CHNL" && q + 14 <= h.size) ch = b.getShort(q + 12).toInt()
            if (sid == "CMPR" && q + 16 <= h.size && id(h, q + 12) != "DSD ") return null
            q += 12 + ss.toInt() + (ss.toInt() and 1) } }
        "DSD " -> return if (fs > 0 && ch > 0) Layout(DSD_MSBF, ch, fs / 8, fs, (p + 12).toLong(), size, 4096 * ch, ch, fs / 8, p + 12 + size + (size and 1)) else null
        "DST " -> return null
      }
      p += 12 + size.toInt() + (size.toInt() and 1)
    }
    return null
  }

  /** IEEE 754 80-bit extended (AIFF sample rate). */
  fun ext80(b: ByteArray, o: Int): Int {
    val e = ((b[o].toInt() and 0x7f) shl 8) or (b[o + 1].toInt() and 0xff)
    var m = 0L; for (i in 0 until 8) m = (m shl 8) or (b[o + 2 + i].toLong() and 0xff)
    return if (e == 0) 0 else Math.round((m ushr 1).toDouble() * 2.0 * Math.pow(2.0, (e - 16383 - 63).toDouble())).toInt()
  }

  /** AIFF / AIFC: COMM (channels, frames, bits, rate[, compression]) + SSND (offset, block size, data). */
  fun parseAiff(h: ByteArray): Layout? {
    if (h.size < 12 || id(h, 0) != "FORM" || (id(h, 8) != "AIFF" && id(h, 8) != "AIFC")) return null
    val aifc = id(h, 8) == "AIFC"; val b = be(h); var p = 12
    var ch = 0; var bits = 0; var rate = 0; var comp = "NONE"; var tag = -1L
    while (p + 8 <= h.size) {
      val cid = id(h, p); val size = b.getInt(p + 4).toLong() and 0xffffffffL
      when (cid) {
        "COMM" -> { ch = b.getShort(p + 8).toInt(); bits = b.getShort(p + 14).toInt(); rate = ext80(h, p + 16); if (aifc && p + 30 <= h.size) comp = id(h, p + 26) }
        "ID3 ", "id3 " -> tag = (p + 8).toLong()
        "SSND" -> { if (ch <= 0 || rate <= 0) return null
          val off = b.getInt(p + 8); val start = (p + 16 + off).toLong(); val bytes = (bits + 7) / 8 * ch
          val (mime, enc) = when {
            comp == "sowt" && bits == 16 -> MimeTypes.AUDIO_RAW to C.ENCODING_PCM_16BIT
            comp == "fl32" || comp == "FL32" -> "audio/x-rise-pcm-f32be" to Format.NO_VALUE
            comp != "NONE" && comp != "twos" -> return null
            bits <= 16 -> "audio/x-rise-pcm-s16be" to Format.NO_VALUE
            bits <= 24 -> "audio/x-rise-pcm-s24be" to Format.NO_VALUE
            else -> "audio/x-rise-pcm-s32be" to Format.NO_VALUE }
          return Layout(mime, ch, rate, bits, start, size - 8 - off, 4096 * bytes, bytes, rate, if (tag >= 0) tag else p + 8 + size + (size and 1), enc) }
      }
      p += 8 + size.toInt() + (size.toInt() and 1)
    }
    return null
  }
}

/** One extractor for the three formats: parse the header from the first bytes, then emit fixed-size packets with a linear seek map. */
class ChunkExtractor(private val parse: (ByteArray) -> HiRes.Layout?, private val headerMax: Int = 64 * 1024) : Extractor {
  private lateinit var out: ExtractorOutput; private var track: TrackOutput? = null
  private var layout: HiRes.Layout? = null; private var pos = 0L; private var pending = 0; private var sampleStart = 0L

  override fun sniff(input: ExtractorInput): Boolean {
    val h = ByteArray(minOf(headerMax.toLong(), input.length.takeIf { it > 0 } ?: headerMax.toLong()).toInt())
    val n = peekUpTo(input, h); return parse(h.copyOf(n)) != null
  }
  private fun peekUpTo(input: ExtractorInput, h: ByteArray): Int { var n = 0
    while (n < h.size) { val r = input.peek(h, n, h.size - n); if (r == C.RESULT_END_OF_INPUT) break; n += r }; return n }

  override fun init(output: ExtractorOutput) { out = output }

  override fun read(input: ExtractorInput, seekPosition: PositionHolder): Int {
    val l = layout ?: run {
      val h = ByteArray(minOf(headerMax.toLong(), input.length.takeIf { it > 0 } ?: headerMax.toLong()).toInt())
      input.resetPeekPosition(); val n = peekUpTo(input, h)
      val p = parse(h.copyOf(n)) ?: throw androidx.media3.common.ParserException.createForMalformedContainer("Rise: cabeçalho inválido", null)
      layout = p
      val t = out.track(0, C.TRACK_TYPE_AUDIO)
      t.format(Format.Builder().setSampleMimeType(p.mime).setChannelCount(p.channels).setSampleRate(p.rate).setPcmEncoding(p.pcmEncoding)
        .setAverageBitrate(if (p.mime in HiRes.DSD) p.bitsOrDsdRate * p.channels else p.rate * p.bytesPerFrame * 8).setMaxInputSize(p.packetBytes).build())
      track = t
      out.seekMap(object : SeekMap {
        override fun isSeekable() = true
        override fun getDurationUs() = p.durationUs
        override fun getSeekPoints(timeUs: Long): SeekMap.SeekPoints {
          val frames = timeUs * p.frameRate / 1_000_000L; val packetFrames = (p.packetBytes / p.bytesPerFrame).toLong()
          val off = (frames / packetFrames * p.packetBytes).coerceIn(0, (p.dataSize - 1).coerceAtLeast(0) / p.packetBytes * p.packetBytes)
          return SeekMap.SeekPoints(SeekPoint(p.timeUs(off), p.dataStart + off)) }
      })
      out.endTracks()
      seekPosition.position = p.dataStart; pos = 0; return Extractor.RESULT_SEEK
    }
    val t = track!!
    if (pos >= l.dataSize) return Extractor.RESULT_END_OF_INPUT
    if (pending == 0) { sampleStart = pos }
    val want = (minOf(l.packetBytes.toLong(), l.dataSize - sampleStart) - pending).toInt()
    val r = t.sampleData(input, want, true)
    if (r == C.RESULT_END_OF_INPUT) { if (pending > 0) { t.sampleMetadata(l.timeUs(sampleStart), C.BUFFER_FLAG_KEY_FRAME, pending, 0, null); pending = 0 }; return Extractor.RESULT_END_OF_INPUT }
    pending += r; pos += r
    if (pending >= minOf(l.packetBytes.toLong(), l.dataSize - sampleStart)) { t.sampleMetadata(l.timeUs(sampleStart), C.BUFFER_FLAG_KEY_FRAME, pending, 0, null); pending = 0 }
    return Extractor.RESULT_CONTINUE
  }

  override fun seek(position: Long, timeUs: Long) { val l = layout ?: return; pending = 0; pos = (position - l.dataStart).coerceAtLeast(0) }
  override fun release() {}
}

/** Default Media3 extractors + ours (tried first; their sniffers only accept their own magic). */
class RiseExtractorsFactory : ExtractorsFactory {
  private val base = DefaultExtractorsFactory().setConstantBitrateSeekingEnabled(true)
  override fun createExtractors(): Array<Extractor> = arrayOf<Extractor>(ChunkExtractor(HiRes::parseDsf), ChunkExtractor(HiRes::parseDff), ChunkExtractor(HiRes::parseAiff)) + base.createExtractors()
  override fun createExtractors(uri: android.net.Uri, responseHeaders: Map<String, List<String>>): Array<Extractor> =
    arrayOf<Extractor>(ChunkExtractor(HiRes::parseDsf), ChunkExtractor(HiRes::parseDff), ChunkExtractor(HiRes::parseAiff)) + base.createExtractors(uri, responseHeaders)
}
