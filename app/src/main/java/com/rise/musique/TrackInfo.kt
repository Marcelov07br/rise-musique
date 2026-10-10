package com.rise.musique

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.work.*
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

/** Per-track analysis cached once: loudness (LUFS + peak), genre/year tags, mood. */
data class TrackInfo(val lufs: Double? = null, val peak: Double = 1.0, val genre: String = "", val year: Int = 0, val bpm: Double = 0.0) {
  fun toJson() = JSONObject().put("lufs", lufs ?: JSONObject.NULL).put("peak", peak).put("genre", genre).put("year", year).put("bpm", bpm)
  companion object { fun fromJson(o: JSONObject) = TrackInfo(if (o.isNull("lufs")) null else o.optDouble("lufs"), o.optDouble("peak", 1.0),
    o.optString("genre"), o.optInt("year"), o.optDouble("bpm", 0.0)) }
}

object Moods {
  val ALL = listOf("Calmo", "Foco", "Animado", "Festa")
  /** BPM + loudness energy → bucket. Visible for tests. */
  fun of(i: TrackInfo): String? {
    val bpm = i.bpm.takeIf { it > 0 } ?: return null
    val energy = i.lufs ?: -14.0 // louder masters feel more energetic
    return when { bpm < 92 && energy < -11 -> "Calmo"; bpm < 110 -> "Foco"; bpm >= 122 && energy > -10 -> "Festa"; else -> "Animado" }
  }
  fun decade(year: Int) = if (year in 1900..2100) "${year / 10 * 10}s" else null
}

object TrackInfoStore {
  private fun file(c: Context) = File(c.filesDir, "trackinfo.json")
  private var mem: JSONObject? = null
  @Synchronized private fun json(c: Context) = mem ?: runCatching { JSONObject(AtomicJson.read(file(c)) ?: "{}") }.getOrDefault(JSONObject()).also { mem = it }
  @Synchronized fun get(c: Context, uri: String): TrackInfo? = json(c).optJSONObject(uri)?.let(TrackInfo::fromJson)
  @Synchronized fun all(c: Context): Map<String, TrackInfo> = json(c).let { o -> o.keys().asSequence().associateWith { TrackInfo.fromJson(o.getJSONObject(it)) } }
  @Synchronized fun put(c: Context, uri: String, i: TrackInfo) { val o = json(c); o.put(uri, i.toJson()); AtomicJson.write(file(c), o.toString()) }

  /** Gain (dB) for a local track: ReplayGain tag if the player read one, else our measurement; album mode averages the album. */
  fun gainDb(c: Context, uri: String, album: String?, albumMode: Boolean, tagGainDb: Double?, history: () -> List<com.rise.musique.ui.HistoryItem>): Double {
    // ReplayGain references -18 LUFS; we target -14 → +4 dB.
    if (tagGainDb != null) return (tagGainDb + 4.0).coerceIn(-24.0, 12.0)
    val i = get(c, uri) ?: return 0.0; val l = i.lufs ?: return 0.0
    if (albumMode && !album.isNullOrBlank()) {
      val peers = history().filter { it.album == album }.mapNotNull { get(c, it.uri) }
      val lufs = peers.mapNotNull { it.lufs }; if (lufs.size > 1) return Loudness.gainFor(Loudness.albumLufs(lufs), peers.maxOf { it.peak })
    }
    return Loudness.gainFor(l, i.peak)
  }

  /** Measures integrated loudness (EBU R128) of a whole local file. Blocking. */
  fun measure(c: Context, uri: Uri): Pair<Double, Double>? = runCatching {
    val ex = MediaExtractor(); ex.setDataSource(c, uri, null)
    val t = (0 until ex.trackCount).first { ex.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true }
    ex.selectTrack(t); val fmt = ex.getTrackFormat(t)
    val codec = MediaCodec.createDecoderByType(fmt.getString(MediaFormat.KEY_MIME)!!); codec.configure(fmt, null, null, 0); codec.start()
    var sr = fmt.getInteger(MediaFormat.KEY_SAMPLE_RATE); var ch = fmt.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
    var meter = LoudnessMeter(sr, ch); val info = MediaCodec.BufferInfo(); var inDone = false; var buf = FloatArray(0)
    try {
      while (true) {
        if (!inDone) { val i = codec.dequeueInputBuffer(10_000); if (i >= 0) { val n = ex.readSampleData(codec.getInputBuffer(i)!!, 0)
          if (n < 0) { codec.queueInputBuffer(i, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM); inDone = true } else { codec.queueInputBuffer(i, 0, n, ex.sampleTime, 0); ex.advance() } } }
        val o = codec.dequeueOutputBuffer(info, 10_000)
        if (o == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) { val f = codec.outputFormat
          sr = f.getInteger(MediaFormat.KEY_SAMPLE_RATE); ch = f.getInteger(MediaFormat.KEY_CHANNEL_COUNT); meter = LoudnessMeter(sr, ch) }
        if (o >= 0) { val sb = codec.getOutputBuffer(o)!!.order(java.nio.ByteOrder.nativeOrder()).asShortBuffer()
          val n = sb.remaining(); if (buf.size < n) buf = FloatArray(n); for (k in 0 until n) buf[k] = sb.get() / 32768f
          meter.add(buf, n); codec.releaseOutputBuffer(o, false); if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) break }
      }
    } finally { codec.stop(); codec.release(); ex.release() }
    meter.integrated() to meter.peak
  }.getOrNull()

  fun tags(c: Context, uri: Uri): Pair<String, Int> = runCatching {
    val r = MediaMetadataRetriever(); try { r.setDataSource(c, uri)
      val g = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_GENRE).orEmpty().trim().replace(Regex("^\\((\\d+)\\)$"), "")
      val y = (r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_YEAR) ?: r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DATE))
        ?.let { Regex("(19|20)\\d\\d").find(it)?.value?.toInt() } ?: 0
      g.split(';', '/', ',').first().trim().replaceFirstChar { it.uppercase() } to y } finally { r.release() }
  }.getOrDefault("" to 0)
}

/** Background, once per track: loudness, tags and BPM (shared with Automix). Low priority, resumes where it stopped. */
class LibraryScanWorker(ctx: Context, p: WorkerParameters) : CoroutineWorker(ctx, p) {
  override suspend fun doWork(): Result {
    val c = applicationContext
    val tracks = HistoryStore.load(c).filter { !it.video }
    for (h in tracks) {
      if (isStopped) break
      if (TrackInfoStore.get(c, h.uri)?.lufs != null) continue
      val uri = AutoLibrary.playUri(c, h.uri) ?: continue
      val (lufs, peak) = TrackInfoStore.measure(c, uri) ?: (null to 1.0)
      val (genre, year) = TrackInfoStore.tags(c, uri)
      val bpm = BeatStore.grid(c, h.uri, uri)?.bpm ?: 0.0
      TrackInfoStore.put(c, h.uri, TrackInfo(lufs, peak, genre, year, bpm))
    }
    return Result.success()
  }
  companion object {
    fun schedule(c: Context) = WorkManager.getInstance(c).enqueueUniqueWork("libscan", ExistingWorkPolicy.KEEP,
      OneTimeWorkRequestBuilder<LibraryScanWorker>().setConstraints(Constraints(requiresBatteryNotLow = true))
        .setBackoffCriteria(BackoffPolicy.LINEAR, 1, TimeUnit.MINUTES).build())
  }
}
