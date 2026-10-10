package com.rise.musique

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import org.json.JSONObject
import java.io.File
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** Beat grid of a track: tempo, first downbeat (ms) and confidence 0..1. */
data class BeatGrid(val bpm: Double, val firstDownbeatMs: Long, val confidence: Double) {
  val beatMs get() = 60_000.0 / bpm
  val barMs get() = beatMs * 4
  /** Downbeat at or before [ms]. */
  fun downbeatAtOrBefore(ms: Long): Long { if (ms < firstDownbeatMs) return firstDownbeatMs
    val n = ((ms - firstDownbeatMs) / barMs).toLong(); return firstDownbeatMs + (n * barMs).roundToInt() }
  fun json() = JSONObject().put("bpm", bpm).put("db", firstDownbeatMs).put("c", confidence)
  companion object { fun of(o: JSONObject) = BeatGrid(o.getDouble("bpm"), o.getLong("db"), o.getDouble("c")) }
}

/** Offline tempo + downbeat estimation from an onset-strength envelope. Pure; visible for tests. */
object BeatAnalysis {
  const val HOP_MS = 10.0

  /** Energy envelope (one value per hop) of mono PCM samples in -1..1. */
  fun envelope(samples: FloatArray, sampleRate: Int): FloatArray {
    val hop = (sampleRate * HOP_MS / 1000).toInt().coerceAtLeast(1)
    val n = samples.size / hop
    return FloatArray(n) { i -> var e = 0f; val base = i * hop; for (k in 0 until hop) { val v = samples[base + k]; e += v * v }; sqrt(e / hop) }
  }

  /** Half-wave rectified first difference of the log envelope = onset strength. */
  fun onsets(env: FloatArray): FloatArray {
    val log = FloatArray(env.size) { kotlin.math.ln(1f + 100f * env[it]) }
    val o = FloatArray(env.size) { i -> if (i == 0) 0f else (log[i] - log[i - 1]).coerceAtLeast(0f) }
    val mean = o.average().toFloat(); return FloatArray(o.size) { (o[it] - mean).coerceAtLeast(0f) }
  }

  /** Tempo by autocorrelation over 70..180 BPM with a mild preference for 90..140; then beat phase and downbeat. */
  fun analyze(onset: FloatArray): BeatGrid? {
    if (onset.size < 600) return null
    val minLag = (60_000 / 180 / HOP_MS).toInt(); val maxLag = (60_000 / 70 / HOP_MS).toInt()
    val zero = onset.sumOf { (it * it).toDouble() }.takeIf { it > 0 } ?: return null
    val ac = DoubleArray(maxLag + 2)
    for (lag in minLag..maxLag + 1) { var s = 0.0; for (i in lag until onset.size) s += onset[i] * onset[i - lag]; ac[lag] = s / zero }
    var best = minLag; var bestScore = -1.0
    for (lag in minLag..maxLag) {
      val bpm = 60_000 / (lag * HOP_MS)
      val w = if (bpm in 90.0..140.0) 1.0 else 0.85
      val score = (ac[lag] + 0.5 * ac.getOrElse(lag * 2) { 0.0 }) * w
      if (score > bestScore) { bestScore = score; best = lag }
    }
    // Parabolic refinement of the lag.
    val y0 = ac[best - 1]; val y1 = ac[best]; val y2 = ac[best + 1]
    val d = (y0 - y2) / (2 * (y0 - 2 * y1 + y2)).let { if (it == 0.0) 1e9 else it }
    val lag = best + d.coerceIn(-.5, .5)
    val bpm = 60_000 / (lag * HOP_MS)
    val meanAc = (minLag..maxLag).map { ac[it] }.average()
    val confidence = ((ac[best] - meanAc) / (ac[best] + 1e-9)).coerceIn(0.0, 1.0)
    // Phase: offset in 0..lag maximizing onset sum on the grid.
    val period = lag
    var bestPhase = 0; var bestSum = -1.0
    for (ph in 0 until period.toInt()) { var s = 0.0; var t = ph.toDouble(); while (t < onset.size) { s += onset[t.toInt()]; t += period }
      if (s > bestSum) { bestSum = s; bestPhase = ph } }
    // Downbeat: which of the 4 beats in a bar carries most onset energy.
    val beatSums = DoubleArray(4); var k = 0; var t = bestPhase.toDouble()
    while (t < onset.size) { beatSums[k % 4] += onset[t.toInt()]; k++; t += period }
    val which = beatSums.indices.maxBy { beatSums[it] }
    val first = ((bestPhase + which * period) * HOP_MS).toLong()
    return BeatGrid(bpm, first, confidence)
  }

  /** Overlap length snapped to whole bars (at least 2, at most what fits in [maxMs]). */
  fun barsOverlap(grid: BeatGrid, wantMs: Long, maxMs: Long): Long {
    val bars = (wantMs / grid.barMs).roundToInt().coerceAtLeast(2)
    var ms = (bars * grid.barMs).toLong(); while (ms > maxMs && ms > grid.barMs) ms -= grid.barMs.toLong()
    return ms
  }
  /** Speed for the incoming track so its tempo matches the outgoing one; 1 when too far apart (>8%). */
  fun tempoRatio(outBpm: Double, inBpm: Double): Float {
    val candidates = listOf(inBpm, inBpm * 2, inBpm / 2)
    val r = candidates.map { outBpm / it }.minBy { abs(it - 1) }
    return if (abs(r - 1) <= .08) r.toFloat() else 1f
  }
}

/** Decodes local audio (MediaCodec) and caches beat grids per track in filesDir/beats.json. */
object BeatStore {
  private const val MIN_CONF = 0.25
  private fun file(c: Context) = File(c.filesDir, "beats.json")
  private val cache = HashMap<String, BeatGrid?>()

  @Synchronized private fun load(c: Context): JSONObject = runCatching { JSONObject(AtomicJson.read(file(c)) ?: "{}") }.getOrDefault(JSONObject())
  @Synchronized fun cached(c: Context, key: String): BeatGrid? = cache.getOrPut(key) {
    load(c).optJSONObject(key)?.let(BeatGrid::of) }
  /** Usable grid (confidence high enough) or null. Blocking; call off the main thread. */
  fun grid(c: Context, key: String, uri: Uri, tailOfMs: Long = 0): BeatGrid? {
    val k = if (tailOfMs > 0) "$key#tail" else key
    val g = cached(c, k) ?: analyzeFile(c, uri, if (tailOfMs > 0) 75 else 60, (tailOfMs - 75_000).coerceAtLeast(0))?.also { save(c, k, it) }
    return g?.takeIf { it.confidence >= MIN_CONF }
  }
  @Synchronized private fun save(c: Context, key: String, g: BeatGrid) {
    cache[key] = g; val o = load(c).put(key, g.json()); AtomicJson.write(file(c), o.toString()) }

  /** Decode up to [maxSec] seconds to mono 11 kHz-ish floats, then analyze. */
  fun analyzeFile(c: Context, uri: Uri, maxSec: Int = 90, fromMs: Long = 0): BeatGrid? = runCatching {
    val ex = MediaExtractor(); ex.setDataSource(c, uri, null)
    val track = (0 until ex.trackCount).first { ex.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true }
    ex.selectTrack(track); val fmt = ex.getTrackFormat(track)
    if (fromMs > 0) ex.seekTo(fromMs * 1000, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
    var startUs = -1L
    val rate = fmt.getInteger(MediaFormat.KEY_SAMPLE_RATE); val ch = fmt.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
    val codec = MediaCodec.createDecoderByType(fmt.getString(MediaFormat.KEY_MIME)!!); codec.configure(fmt, null, null, 0); codec.start()
    val step = (rate / 11025).coerceAtLeast(1); val outRate = rate / step
    val out = FloatArray(outRate * maxSec); var n = 0; var inDone = false; val info = MediaCodec.BufferInfo()
    try {
      while (n < out.size) {
        if (!inDone) { val i = codec.dequeueInputBuffer(10_000); if (i >= 0) { val size = ex.readSampleData(codec.getInputBuffer(i)!!, 0)
          if (size < 0) { codec.queueInputBuffer(i, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM); inDone = true }
          else { codec.queueInputBuffer(i, 0, size, ex.sampleTime, 0); ex.advance() } } }
        val o = codec.dequeueOutputBuffer(info, 10_000)
        if (o >= 0) { if (startUs < 0 && info.size > 0) startUs = info.presentationTimeUs
          val buf = codec.getOutputBuffer(o)!!.order(java.nio.ByteOrder.nativeOrder()).asShortBuffer()
          var frame = 0; while (buf.remaining() >= ch && n < out.size) { var s = 0f; repeat(ch) { s += buf.get() / 32768f }
            if (frame++ % step == 0) out[n++] = s / ch }
          codec.releaseOutputBuffer(o, false); if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) break }
      }
    } finally { codec.stop(); codec.release(); ex.release() }
    BeatAnalysis.analyze(BeatAnalysis.onsets(BeatAnalysis.envelope(out.copyOf(n), outRate)))
      ?.let { g -> g.copy(firstDownbeatMs = g.firstDownbeatMs + startUs.coerceAtLeast(0) / 1000) }
  }.getOrNull()
}
