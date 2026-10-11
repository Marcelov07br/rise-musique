@file:androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])
package com.rise.musique

import androidx.media3.common.C
import androidx.media3.common.DataReader
import androidx.media3.common.Format
import androidx.media3.common.util.ParsableByteArray
import androidx.media3.datasource.ByteArrayDataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.extractor.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

@RunWith(RobolectricTestRunner::class)
class Round20Test {
  /** Builds a real DSF (DSD64 stereo, 1-bit LSB-first, 4096-byte blocks) with an ID3v2 tag at the end. */
  private fun dsf(seconds: Double = 0.1, id3: ByteArray = id3("Faixa DSD", "Artista")): ByteArray {
    val fs = 2_822_400; val ch = 2; val samples = (fs * seconds).toLong(); val block = 4096
    val perCh = (samples + 7) / 8; val blocks = (perCh + block - 1) / block
    val data = ByteArray((blocks * block * ch).toInt()) { 0x69 }
    val total = 28 + 52 + 12 + data.size + id3.size
    val b = ByteBuffer.allocate(total).order(ByteOrder.LITTLE_ENDIAN)
    b.put("DSD ".toByteArray()).putLong(28).putLong(total.toLong()).putLong((28 + 52 + 12 + data.size).toLong())
    b.put("fmt ".toByteArray()).putLong(52).putInt(1).putInt(0).putInt(2).putInt(ch).putInt(fs).putInt(1).putLong(samples).putInt(block).putInt(0)
    b.put("data".toByteArray()).putLong((12 + data.size).toLong()).put(data).put(id3)
    return b.array()
  }
  private fun id3(title: String, artist: String): ByteArray {
    fun frame(id: String, v: String): ByteArray { val t = byteArrayOf(3) + v.toByteArray(Charsets.UTF_8)
      return id.toByteArray() + ByteBuffer.allocate(4).putInt(t.size).array() + byteArrayOf(0, 0) + t }
    val body = frame("TIT2", title) + frame("TPE1", artist) + frame("TALB", "Disco Hi-Res")
    val sz = body.size; val syn = byteArrayOf((sz shr 21 and 0x7f).toByte(), (sz shr 14 and 0x7f).toByte(), (sz shr 7 and 0x7f).toByte(), (sz and 0x7f).toByte())
    return "ID3".toByteArray() + byteArrayOf(4, 0, 0) + syn + body
  }
  private fun dff(seconds: Double = 0.1): ByteArray {
    val fs = 5_644_800; val ch = 2; val bytes = (fs * seconds / 8).toInt() * ch
    val o = ByteArrayOutputStream()
    fun be32(v: Int): ByteArray = ByteBuffer.allocate(4).putInt(v).array()
    fun be64(v: Long): ByteArray = ByteBuffer.allocate(8).putLong(v).array()
    val prop = "SND ".toByteArray() + "FS  ".toByteArray() + be64(4) + be32(fs) + "CHNL".toByteArray() + be64(10) + byteArrayOf(0, 2) + "SLFT".toByteArray() + "SRGT".toByteArray() +
      "CMPR".toByteArray() + be64(20) + "DSD ".toByteArray() + byteArrayOf(14) + "not compressed".toByteArray() + byteArrayOf(0)
    val body = "FVER".toByteArray() + be64(4) + be32(0x01050000) + "PROP".toByteArray() + be64(prop.size.toLong()) + prop + "DSD ".toByteArray() + be64(bytes.toLong()) + ByteArray(bytes) { 0x69 }
    o.write("FRM8".toByteArray()); o.write(be64((body.size + 4).toLong())); o.write("DSD ".toByteArray()); o.write(body); return o.toByteArray()
  }
  private fun res(n: String) = javaClass.classLoader!!.getResourceAsStream("hires/$n")!!.readBytes()

  private class Out : ExtractorOutput, TrackOutput {
    var format: Format? = null; var seek: SeekMap? = null; val sizes = mutableListOf<Int>(); val times = mutableListOf<Long>(); var bytes = 0L
    override fun track(id: Int, type: Int) = this
    override fun endTracks() {}
    override fun seekMap(s: SeekMap) { seek = s }
    override fun format(f: Format) { format = f }
    override fun sampleData(input: DataReader, length: Int, allowEndOfInput: Boolean, sampleDataPart: Int): Int {
      val buf = ByteArray(length); val r = input.read(buf, 0, length); if (r == C.RESULT_END_OF_INPUT) return r; bytes += r; return r }
    override fun sampleData(data: ParsableByteArray, length: Int, sampleDataPart: Int) { data.skipBytes(length); bytes += length }
    override fun sampleMetadata(timeUs: Long, flags: Int, size: Int, offset: Int, cryptoData: TrackOutput.CryptoData?) { sizes += size; times += timeUs }
  }
  private fun run(e: Extractor, bytes: ByteArray): Out {
    val out = Out(); e.init(out)
    fun input(pos: Long): ExtractorInput { val ds = ByteArrayDataSource(bytes); val len = ds.open(DataSpec.Builder().setUri("x").setPosition(pos).build())
      return DefaultExtractorInput(ds, pos, len + pos) }
    assertTrue(e.sniff(input(0)))
    var inp = input(0); val ph = PositionHolder(); var guard = 0
    while (guard++ < 100_000) { when (e.read(inp, ph)) { Extractor.RESULT_SEEK -> inp = input(ph.position); Extractor.RESULT_END_OF_INPUT -> return out } }
    fail("não terminou"); return out
  }

  @Test fun dsfHeaderAndPackets() {
    val f = dsf(); val l = HiRes.parseDsf(f)!!
    assertEquals(HiRes.DSD_LSBF_PLANAR, l.mime); assertEquals(352_800, l.rate); assertEquals(2, l.channels); assertEquals(8192, l.packetBytes)
    val o = run(ChunkExtractor(HiRes::parseDsf), f)
    assertEquals(HiRes.DSD_LSBF_PLANAR, o.format!!.sampleMimeType); assertEquals(352_800, o.format!!.sampleRate)
    assertTrue(o.sizes.all { it == 8192 }); assertEquals(l.dataSize, o.bytes)
    assertEquals(0L, o.times.first()); assertTrue(o.times.zipWithNext().all { (a, b) -> b > a })
    assertTrue(o.seek!!.durationUs in 90_000L..130_000L)
    // seek lands on a block boundary
    val sp = o.seek!!.getSeekPoints(50_000).first; assertEquals(0L, (sp.position - l.dataStart) % 8192)
  }
  @Test fun dsfTagsAndLabel() {
    val f = dsf(); val t = Lossless.tags { f.inputStream() }!!
    assertEquals("Faixa DSD", t.title); assertEquals("Artista", t.artist); assertEquals("Disco Hi-Res", t.album)
    assertEquals("DSD64 · 2.8 MHz → PCM 16/176", t.quality)
    assertEquals("DSD64 · 2.8 MHz → PCM 16/176 · Estéreo", AudioQuality.label(HiRes.DSD_LSBF_PLANAR, 0, 352_800, 2))
    assertEquals("DSD128 · 5.6 MHz → PCM 24/176", HiRes.dsdLabel(5_644_800, HiRes.outRate(705_600), true))
    assertEquals(176_400, HiRes.outRate(352_800)); assertEquals(96_000, HiRes.outRate(96_000))
  }
  @Test fun dffInterleavedMsbf() {
    val f = dff(); val l = HiRes.parseDff(f)!!
    assertEquals(HiRes.DSD_MSBF, l.mime); assertEquals(705_600, l.rate); assertEquals(2, l.channels)
    val o = run(ChunkExtractor(HiRes::parseDff), f); assertEquals(l.dataSize, o.bytes); assertTrue(o.sizes.dropLast(1).all { it == 8192 })
    assertTrue(o.seek!!.durationUs in 90_000L..110_000L)
  }
  @Test fun aiffFromFfmpeg() {
    val a = res("sine24.aiff"); val l = HiRes.parseAiff(a)!!
    assertEquals("audio/x-rise-pcm-s24be", l.mime); assertEquals(48_000, l.rate); assertEquals(2, l.channels); assertEquals(6, l.bytesPerFrame)
    val o = run(ChunkExtractor(HiRes::parseAiff), a); assertEquals(48_000L * 6 / 2, o.bytes); assertEquals(500_000L, o.seek!!.durationUs)
    val b = HiRes.parseAiff(res("sine16.aif"))!!; assertEquals("audio/x-rise-pcm-s16be", b.mime); assertEquals(44_100, b.rate)
    val t = Lossless.tags { res("sine24.aiff").inputStream() }!!; assertEquals("Seno", t.title); assertEquals("Box", t.artist)
    assertEquals("AIFF", AudioQuality.codec("audio/x-rise-pcm-s24be"))
  }
  @Test fun factorySniffsOursFirstAndRejectsOthers() {
    assertNull(HiRes.parseDsf(res("sine24.aiff"))); assertNull(HiRes.parseAiff(dsf())); assertNull(HiRes.parseDff(dsf()))
    assertTrue(RiseExtractorsFactory().createExtractors().size > 3)
    assertEquals("dsd_lsbf_planar", androidx.media3.decoder.ffmpeg.FfmpegLibrary.getCodecName(HiRes.DSD_LSBF_PLANAR))
  }

  private fun json(n: String) = org.json.JSONObject(java.util.zip.GZIPInputStream(javaClass.classLoader!!.getResourceAsStream("ytm/$n")).bufferedReader().readText())
  @Test fun artistPageFromYoutubeMusic() {
    val p = ArtistBrowse.parseArtist(json("artist.json.gz"), "UCrtBsnR6zPUFP0UbVLKKKWg")
    assertEquals("Marisa Monte", p.name)
    assertTrue(p.image, p.image.contains("googleusercontent") && p.image.endsWith("=w1080-h1080-p-l90-rj"))
    assertTrue(p.top.size >= 5); assertTrue(p.top.all { it.kind == "song" && it.url.contains("watch?v=") })
    assertEquals("Vilarejo", p.top[0].title); assertEquals("Infinito Particular", p.top[0].album)
    assertTrue(p.albums.size >= 5); assertTrue(p.albums.all { it.kind == "album" && it.url.contains("/browse/MPRE") })
    assertTrue(p.singles.isNotEmpty()); assertTrue(p.videos.isNotEmpty() && p.videos.all { it.kind == "video" })
    assertTrue(p.similar.isNotEmpty() && p.similar.all { it.kind == "artist" && it.url.contains("/channel/UC") })
    assertTrue(p.about.startsWith("Marisa de Azevedo Monte"))
  }
  @Test fun streamedAlbumTracklist() {
    val a = ArtistBrowse.parseAlbum(json("album.json.gz"), "MPREb_wK4FoXcYZT6")
    assertEquals("Portas", a.title); assertEquals("Marisa Monte", a.artist); assertEquals("2022", a.year)
    assertTrue(a.tracks.size >= 10); assertEquals("Portas", a.tracks[0].title); assertEquals(163, a.tracks[0].duration)
    assertTrue(a.tracks.all { it.channel == "Marisa Monte" && it.album == "Portas" && it.thumb == a.art })
  }
}
