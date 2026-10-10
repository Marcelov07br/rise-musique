package com.rise.musique
import androidx.media3.common.C
import androidx.media3.exoplayer.audio.TeeAudioProcessor
import kotlinx.coroutines.flow.MutableStateFlow
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.*

/**
 * Lê o áudio que o player está tocando (cópia do PCM, sem microfone) e resume em 8 faixas de energia.
 * As faixas vêm de filtros passa-baixa encadeados: grave, médio-grave, médio... até agudo.
 */
@androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])
object AudioLevels : TeeAudioProcessor.AudioBufferSink {
  val bands = MutableStateFlow(FloatArray(8))
  private var enc = C.ENCODING_INVALID; private var ch = 2; private var sr = 44100
  private val lp = FloatArray(8); private val acc = FloatArray(8); private var n = 0
  private val smooth = FloatArray(8)

  override fun flush(sampleRateHz: Int, channelCount: Int, encoding: Int) { sr = sampleRateHz; ch = channelCount; enc = encoding }

  override fun handleBuffer(buffer: ByteBuffer) {
    if (enc != C.ENCODING_PCM_16BIT && enc != C.ENCODING_PCM_FLOAT) return
    val b = buffer.duplicate().order(ByteOrder.LITTLE_ENDIAN)
    val frameBytes = ch * if (enc == C.ENCODING_PCM_16BIT) 2 else 4
    while (b.remaining() >= frameBytes) {
      var s = 0f
      repeat(ch) { s += if (enc == C.ENCODING_PCM_16BIT) b.short / 32768f else b.float }
      s /= ch
      // banco de passa-baixas: cada faixa é a diferença entre dois cortes vizinhos
      var prev = s
      for (i in 0 until 8) {
        val cut = 8000f / (1 shl i)        // 8k, 4k, 2k ... 62 Hz
        val a = (1 - exp(-2 * PI * cut / sr)).toFloat()
        lp[i] += a * (prev - lp[i])
        val band = prev - lp[i]; acc[7 - i] += band * band
        prev = lp[i]
      }
      if (++n >= sr / 60) { // ~60 vezes por segundo
        val out = FloatArray(8) { i -> val v = (sqrt(acc[i] / n) * (3f + i * 0.6f)).coerceIn(0f, 1f)
          smooth[i] = if (v > smooth[i]) v else smooth[i] * 0.85f + v * 0.15f; smooth[i] }
        bands.value = out; acc.fill(0f); n = 0
      }
    }
  }
  fun silence() { smooth.fill(0f); bands.value = FloatArray(8) }
}

