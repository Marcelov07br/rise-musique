package com.rise.musique

import kotlin.math.*

/** RBJ-cookbook biquad (transposed direct form II), one instance per channel. */
class Biquad(var b0: Double = 1.0, var b1: Double = 0.0, var b2: Double = 0.0, var a1: Double = 0.0, var a2: Double = 0.0) {
  private var z1 = 0.0; private var z2 = 0.0
  fun process(x: Double): Double { val y = b0 * x + z1; z1 = b1 * x - a1 * y + z2; z2 = b2 * x - a2 * y; return y }
  fun reset() { z1 = 0.0; z2 = 0.0 }
  fun set(o: Biquad) { b0 = o.b0; b1 = o.b1; b2 = o.b2; a1 = o.a1; a2 = o.a2 }
  /** |H(f)| in dB at frequency [f] for sample rate [sr]. */
  fun magnitudeDb(f: Double, sr: Double): Double {
    val w = 2 * PI * f / sr; val c1 = cos(w); val s1 = sin(w); val c2 = cos(2 * w); val s2 = sin(2 * w)
    val nr = b0 + b1 * c1 + b2 * c2; val ni = -(b1 * s1 + b2 * s2)
    val dr = 1 + a1 * c1 + a2 * c2; val di = -(a1 * s1 + a2 * s2)
    return 10 * log10(((nr * nr + ni * ni) / (dr * dr + di * di)).coerceAtLeast(1e-12))
  }
  companion object {
    fun peaking(f: Double, q: Double, gainDb: Double, sr: Double): Biquad {
      val a = 10.0.pow(gainDb / 40); val w = 2 * PI * (f.coerceAtMost(sr * .45)) / sr; val alpha = sin(w) / (2 * q); val c = cos(w)
      val a0 = 1 + alpha / a
      return Biquad((1 + alpha * a) / a0, -2 * c / a0, (1 - alpha * a) / a0, -2 * c / a0, (1 - alpha / a) / a0)
    }
    fun lowShelf(f: Double, gainDb: Double, sr: Double, s: Double = 1.0): Biquad {
      val a = 10.0.pow(gainDb / 40); val w = 2 * PI * f / sr; val c = cos(w); val sn = sin(w)
      val alpha = sn / 2 * sqrt((a + 1 / a) * (1 / s - 1) + 2); val k = 2 * sqrt(a) * alpha
      val a0 = (a + 1) + (a - 1) * c + k
      return Biquad(a * ((a + 1) - (a - 1) * c + k) / a0, 2 * a * ((a - 1) - (a + 1) * c) / a0, a * ((a + 1) - (a - 1) * c - k) / a0,
        -2 * ((a - 1) + (a + 1) * c) / a0, ((a + 1) + (a - 1) * c - k) / a0)
    }
    fun highShelf(f: Double, gainDb: Double, sr: Double): Biquad {
      val a = 10.0.pow(gainDb / 40); val w = 2 * PI * f / sr; val c = cos(w); val alpha = sin(w) / 2 * sqrt(2.0); val k = 2 * sqrt(a) * alpha
      val a0 = (a + 1) - (a - 1) * c + k
      return Biquad(a * ((a + 1) + (a - 1) * c + k) / a0, -2 * a * ((a - 1) + (a + 1) * c) / a0, a * ((a + 1) + (a - 1) * c - k) / a0,
        2 * ((a - 1) - (a + 1) * c) / a0, ((a + 1) - (a - 1) * c - k) / a0)
    }
    fun highPass(f: Double, sr: Double, q: Double = 0.5): Biquad {
      val w = 2 * PI * f / sr; val c = cos(w); val alpha = sin(w) / (2 * q); val a0 = 1 + alpha
      return Biquad((1 + c) / 2 / a0, -(1 + c) / a0, (1 + c) / 2 / a0, -2 * c / a0, (1 - alpha) / a0)
    }
  }
}

/** 10-band graphic EQ settings (dB), bass boost (low shelf) and preamp. */
data class EqSettings(val on: Boolean = false, val bands: List<Float> = List(10) { 0f }, val bass: Float = 0f, val preamp: Float = 0f,
                      val preset: String = "Flat", val autoPreamp: Boolean = true) {
  /** Preamp actually applied: user preamp, minus the curve's peak boost when auto (keeps the chain from clipping). */
  fun effectivePreamp(): Float = if (!autoPreamp) preamp else preamp - max(0f, Eq.peakBoost(this))
  companion object {
    fun fromJson(o: org.json.JSONObject) = EqSettings(o.optBoolean("on"), o.optJSONArray("bands")?.let { a -> List(10) { (a.optDouble(it, 0.0)).toFloat() } } ?: List(10) { 0f },
      o.optDouble("bass", 0.0).toFloat(), o.optDouble("preamp", 0.0).toFloat(), o.optString("preset", "Flat"), o.optBoolean("auto", true))
  }
  fun toJson() = org.json.JSONObject().put("on", on).put("bands", org.json.JSONArray(bands.map { it.toDouble() })).put("bass", bass.toDouble())
    .put("preamp", preamp.toDouble()).put("preset", preset).put("auto", autoPreamp)
}

object Eq {
  val FREQS = listOf(31.0, 62.0, 125.0, 250.0, 500.0, 1000.0, 2000.0, 4000.0, 8000.0, 16000.0)
  val LABELS = listOf("31", "62", "125", "250", "500", "1k", "2k", "4k", "8k", "16k")
  const val Q = 1.41; const val RANGE = 12f; const val BASS_HZ = 90.0
  val PRESETS: Map<String, List<Float>> = linkedMapOf(
    "Flat" to List(10) { 0f },
    "Bass" to listOf(6f, 5f, 4f, 2f, 0f, 0f, 0f, 0f, 0f, 0f),
    "Vocal" to listOf(-2f, -2f, -1f, 1f, 3f, 4f, 3f, 1f, 0f, -1f),
    "Rock" to listOf(4f, 3f, 2f, 0f, -1f, -1f, 1f, 3f, 4f, 4f),
    "Eletrônica" to listOf(5f, 4f, 1f, 0f, -2f, 1f, 0f, 2f, 4f, 5f),
    "Acústico" to listOf(3f, 2f, 1f, 1f, 2f, 2f, 3f, 3f, 2f, 1f),
    "Podcast" to listOf(-6f, -4f, -2f, 0f, 2f, 4f, 4f, 2f, 0f, -2f))
  fun filters(s: EqSettings, sr: Double): List<Biquad> =
    FREQS.mapIndexedNotNull { i, f -> s.bands[i].takeIf { abs(it) > .01f }?.let { Biquad.peaking(f, Q, it.toDouble(), sr) } } +
      listOfNotNull(s.bass.takeIf { it > .01f }?.let { Biquad.lowShelf(BASS_HZ, it.toDouble(), sr) })
  /** Total response (dB) of the curve at [f], preamp included. */
  fun responseDb(s: EqSettings, f: Double, sr: Double = 48000.0): Float =
    (filters(s, sr).sumOf { it.magnitudeDb(f, sr) } + s.effectivePreamp()).toFloat()
  /** Highest boost of the curve over the audible range (used for auto preamp). */
  fun peakBoost(s: EqSettings): Float { val raw = s.copy(preamp = 0f, autoPreamp = false)
    return (0..60).maxOf { i -> val f = 20.0 * 10.0.pow(i / 60.0 * 3); (filters(raw, 48000.0).sumOf { it.magnitudeDb(f, 48000.0) }).toFloat() } }
}

/** Lookahead-free peak limiter: instant attack, smooth release; never lets |x| exceed [ceiling]. */
class Limiter(private val ceiling: Double = 0.97, releaseMs: Double = 80.0, sr: Double = 48000.0) {
  private val rel = exp(-1.0 / (releaseMs / 1000 * sr)); private var g = 1.0
  fun gain(peak: Double): Double {
    val released = (1 - rel) + rel * g // recover toward unity
    g = if (peak > 0) min(released, ceiling / peak) else released; return g }
}

/** EBU R128 / ITU BS.1770 integrated loudness (K-weighting, 400 ms blocks, 75% overlap, -70 LUFS abs + -10 LU rel gates). */
class LoudnessMeter(private val sr: Int, private val channels: Int) {
  private val pre = List(channels) { Biquad.highShelf(1681.97, 4.0, sr.toDouble()) }
  private val hp = List(channels) { Biquad.highPass(38.135, sr.toDouble(), 0.5003) }
  private val step = sr / 10 // 100 ms
  private val sub = ArrayList<Double>() // mean square per 100 ms (sum over channels)
  private var acc = 0.0; private var n = 0
  var peak = 0.0; private set
  /** [x] interleaved samples in -1..1. */
  fun add(x: FloatArray, count: Int = x.size) {
    var i = 0
    while (i + channels <= count) {
      var s = 0.0
      for (c in 0 until channels) { val v = x[i + c].toDouble(); peak = max(peak, abs(v)); val k = hp[c].process(pre[c].process(v)); s += k * k }
      acc += s; n++; i += channels
      if (n == step) { sub.add(acc / n); acc = 0.0; n = 0 }
    }
  }
  fun integrated(): Double {
    if (sub.size < 4) return -70.0
    val blocks = (0..sub.size - 4).map { i -> (sub[i] + sub[i + 1] + sub[i + 2] + sub[i + 3]) / 4 }
    fun lufs(ms: Double) = -0.691 + 10 * log10(ms.coerceAtLeast(1e-12))
    val abs = blocks.filter { lufs(it) > -70 }; if (abs.isEmpty()) return -70.0
    val rel = lufs(abs.average()) - 10
    val gated = abs.filter { lufs(it) > rel }
    return lufs(gated.ifEmpty { abs }.average())
  }
}

object Loudness {
  const val TARGET = -14.0
  /** Gain (dB) to reach the target, limited so the measured peak stays under -1 dBTP-ish (the limiter catches the rest). */
  fun gainFor(lufs: Double, peak: Double, target: Double = TARGET): Double {
    val g = target - lufs; val headroom = -1.0 - 20 * log10(peak.coerceAtLeast(1e-6))
    return min(g, max(headroom, g.coerceAtMost(6.0))).coerceIn(-24.0, 12.0)
  }
  /** "REPLAYGAIN_TRACK_GAIN=-7.3 dB" → -7.3 */
  fun parseReplayGain(v: String?): Double? = v?.let { Regex("([-+]?[0-9]*\\.?[0-9]+)").find(it)?.value?.toDoubleOrNull() }
  /** Album gain from per-track (lufs) — power average, as ReplayGain album mode. */
  fun albumLufs(tracks: List<Double>): Double = if (tracks.isEmpty()) -14.0 else 10 * log10(tracks.map { 10.0.pow(it / 10) }.average())
}
