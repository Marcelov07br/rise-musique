package com.rise.musique

import android.content.Context
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.pow

/** Shared, thread-safe settings read by every player's processor. */
object AudioFx {
  @Volatile var eq = EqSettings(); private set
  @Volatile var version = 0; private set
  fun apply(s: EqSettings) { eq = s; version++ }
}

/**
 * The app's own audio chain, the same on every device (no system Equalizer):
 * normalize (per-track gain) → 10-band EQ + bass shelf + preamp → peak limiter.
 * One instance per ExoPlayer (main and the crossfade player), so each carries its own track gain.
 */
@androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])
class RiseAudioProcessor : BaseAudioProcessor() {
  @Volatile var normGainDb = 0.0
  private var sr = 48000; private var ch = 2; private var enc = C.ENCODING_PCM_16BIT
  private var seen = -1
  private var chain: List<List<Biquad>> = emptyList()
  private var preamp = 1.0
  private var limiter = Limiter()

  override fun onConfigure(f: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
    if (f.encoding != C.ENCODING_PCM_16BIT && f.encoding != C.ENCODING_PCM_FLOAT) throw AudioProcessor.UnhandledAudioFormatException(f)
    sr = f.sampleRate; ch = f.channelCount; enc = f.encoding; seen = -1; limiter = Limiter(sr = sr.toDouble()); return f
  }
  private fun rebuild() {
    val s = AudioFx.eq; seen = AudioFx.version
    if (!s.on) { chain = emptyList(); preamp = 1.0; return }
    val proto = Eq.filters(s, sr.toDouble())
    chain = List(ch) { proto.map { b -> Biquad().also { it.set(b) } } }
    preamp = 10.0.pow(s.effectivePreamp() / 20.0)
  }
  override fun queueInput(input: ByteBuffer) {
    if (seen != AudioFx.version) rebuild()
    val bytes = input.remaining(); if (bytes == 0) return
    val out = replaceOutputBuffer(bytes)
    val gain = 10.0.pow(normGainDb / 20.0) * preamp
    val float = enc == C.ENCODING_PCM_FLOAT
    val frame = DoubleArray(ch)
    input.order(ByteOrder.nativeOrder()); out.order(ByteOrder.nativeOrder())
    while (input.remaining() >= ch * (if (float) 4 else 2)) {
      var peak = 0.0
      for (c in 0 until ch) {
        var x = if (float) input.float.toDouble() else input.short / 32768.0
        x *= gain
        val fl = chain.getOrNull(c); if (fl != null) for (b in fl) x = b.process(x)
        frame[c] = x; peak = maxOf(peak, abs(x))
      }
      val g = limiter.gain(peak)
      for (c in 0 until ch) { val y = (frame[c] * g).coerceIn(-1.0, 1.0)
        if (float) out.putFloat(y.toFloat()) else out.putShort((y * 32767).toInt().toShort()) }
    }
    out.flip()
  }
  override fun onFlush() { chain.forEach { l -> l.forEach { it.reset() } } }
}

/** EQ profiles per output device ("speaker", "wired", "bt:<name>", "usb"). */
object EqStore {
  private fun prefs(c: Context) = c.getSharedPreferences("eq", 0)
  fun profileKey(c: Context): String {
    val am = c.getSystemService(android.media.AudioManager::class.java)
    val outs = am.getDevices(android.media.AudioManager.GET_DEVICES_OUTPUTS)
    fun has(vararg t: Int) = outs.firstOrNull { it.type in t }
    has(android.media.AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, 26 /* BLE headset */, 27 /* BLE speaker */)?.let { return "bt:" + it.productName }
    if (has(android.media.AudioDeviceInfo.TYPE_WIRED_HEADPHONES, android.media.AudioDeviceInfo.TYPE_WIRED_HEADSET) != null) return "wired"
    if (has(android.media.AudioDeviceInfo.TYPE_USB_HEADSET, android.media.AudioDeviceInfo.TYPE_USB_DEVICE) != null) return "usb"
    return "speaker"
  }
  fun label(key: String) = when { key == "speaker" -> "Alto-falante"; key == "wired" -> "Fone com fio"; key == "usb" -> "USB"
    key.startsWith("bt:") -> key.removePrefix("bt:").ifBlank { "Bluetooth" }; else -> key }
  fun load(c: Context, key: String) = prefs(c).getString(key, null)?.let { runCatching { EqSettings.fromJson(org.json.JSONObject(it)) }.getOrNull() } ?: EqSettings()
  fun save(c: Context, key: String, s: EqSettings) = prefs(c).edit().putString(key, s.toJson().toString()).apply()
  fun profiles(c: Context) = prefs(c).all.keys.toList()
  /** Loads the profile of the current output into the audio chain. */
  fun applyCurrent(c: Context): String { val k = profileKey(c); AudioFx.apply(load(c, k)); return k }
}
