package com.rise.musique

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import org.json.JSONObject
import kotlin.math.abs

/** YouTube storyboard: sprite sheets of [cols]×[rows] frames, each sheet covering [sheetSec] seconds. */
data class Storyboard(val sheets: List<Pair<String, Double>>, val width: Int, val height: Int, val cols: Int, val rows: Int) {
  /** Which sheet and which cell show the moment [sec]. */
  fun frameAt(sec: Double): Triple<Int, Int, Int> {
    var t = sec.coerceAtLeast(0.0); var i = 0
    while (i < sheets.lastIndex && t >= sheets[i].second) { t -= sheets[i].second; i++ }
    val per = sheets[i].second / (cols * rows)
    val cell = (t / per).toInt().coerceIn(0, cols * rows - 1)
    return Triple(i, cell % cols, cell / cols)
  }
}

/** What the cut sheet draws: waveform peaks (music) or a storyboard (video). */
data class CutVisual(val loading: Boolean = true, val peaks: List<Float>? = null, val storyboard: Storyboard? = null, val error: String? = null)

object CutInfo {
  /** Picks a mid-size storyboard (prefers ~80 px wide frames) from yt-dlp -j output. Visible for tests. */
  fun storyboard(json: JSONObject): Storyboard? {
    val f = json.optJSONArray("formats") ?: return null
    val boards = (0 until f.length()).map(f::getJSONObject).filter { it.optString("format_note") == "storyboard" && it.has("fragments") }
    val pick = boards.minByOrNull { abs(it.optInt("width") - 80) } ?: return null
    val fr = pick.getJSONArray("fragments")
    return Storyboard((0 until fr.length()).map { fr.getJSONObject(it).let { o -> o.getString("url") to o.optDouble("duration", 0.0) } },
      pick.optInt("width"), pick.optInt("height"), pick.optInt("columns", 1).coerceAtLeast(1), pick.optInt("rows", 1).coerceAtLeast(1))
  }

  /** Reduces PCM magnitudes into [buckets] peaks normalised to 0..1. Visible for tests. */
  fun peaks(values: FloatArray, count: Int, buckets: Int): List<Float> {
    if (count <= 0) return List(buckets) { 0f }
    val out = FloatArray(buckets); val per = (count.toDouble() / buckets)
    for (b in 0 until buckets) { val from = (b * per).toInt(); val to = ((b + 1) * per).toInt().coerceAtMost(count)
      var m = 0f; for (i in from until to) m = maxOf(m, abs(values[i])); out[b] = m }
    val max = out.maxOrNull()?.takeIf { it > 0 } ?: 1f
    return out.map { (it / max).coerceIn(0f, 1f) }
  }

  /** Decodes the remote audio stream (with yt-dlp's headers) and returns [buckets] peaks. Blocking. */
  fun waveform(url: String, headers: Map<String, String>, buckets: Int = 120): List<Float> {
    val ex = MediaExtractor(); ex.setDataSource(url, headers)
    val track = (0 until ex.trackCount).first { ex.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true }
    ex.selectTrack(track); val fmt = ex.getTrackFormat(track)
    val durUs = if (fmt.containsKey(MediaFormat.KEY_DURATION)) fmt.getLong(MediaFormat.KEY_DURATION) else 0L
    val codec = MediaCodec.createDecoderByType(fmt.getString(MediaFormat.KEY_MIME)!!); codec.configure(fmt, null, null, 0); codec.start()
    // One max per ~20 ms window keeps memory small.
    val windows = FloatArray(((durUs / 20_000) + 2).toInt().coerceIn(1, 60_000)); var info = MediaCodec.BufferInfo(); var inDone = false
    try {
      while (true) {
        if (!inDone) { val i = codec.dequeueInputBuffer(10_000); if (i >= 0) { val size = ex.readSampleData(codec.getInputBuffer(i)!!, 0)
          if (size < 0) { codec.queueInputBuffer(i, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM); inDone = true }
          else { codec.queueInputBuffer(i, 0, size, ex.sampleTime, 0); ex.advance() } } }
        val o = codec.dequeueOutputBuffer(info, 10_000)
        if (o >= 0) { val w = (info.presentationTimeUs / 20_000).toInt().coerceIn(0, windows.lastIndex)
          val buf = codec.getOutputBuffer(o)!!.order(java.nio.ByteOrder.nativeOrder()).asShortBuffer()
          var m = windows[w]; while (buf.hasRemaining()) { val v = abs(buf.get() / 32768f); if (v > m) m = v }; windows[w] = m
          codec.releaseOutputBuffer(o, false); if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) break }
      }
    } finally { codec.stop(); codec.release(); ex.release() }
    return peaks(windows, windows.size, buckets)
  }
}
