package com.rise.musique

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import com.rise.musique.ui.VoiceUi

/**
 * Voice search with the platform SpeechRecognizer (pt-BR, partial results, on-device when the phone offers it).
 * Humming search is not included: there's no legitimate offline/public recognizer for melodies on Android.
 */
class VoiceSearch(private val ctx: Context, private val onState: (VoiceUi?) -> Unit, private val onResult: (String) -> Unit) {
  private var rec: SpeechRecognizer? = null
  fun available() = SpeechRecognizer.isRecognitionAvailable(ctx)
  fun start() {
    stop(false)
    if (!available()) { onState(VoiceUi(false, error = "Reconhecimento de voz indisponível neste aparelho")); return }
    val r = SpeechRecognizer.createSpeechRecognizer(ctx); rec = r
    r.setRecognitionListener(object : RecognitionListener {
      override fun onReadyForSpeech(p: Bundle?) = onState(VoiceUi())
      override fun onBeginningOfSpeech() {}
      override fun onRmsChanged(db: Float) = onState(VoiceUi(partial = last, level = VoiceLevel.of(db)))
      override fun onBufferReceived(b: ByteArray?) {}
      override fun onEndOfSpeech() = onState(VoiceUi(listening = false, partial = last))
      override fun onError(e: Int) { onState(if (e == SpeechRecognizer.ERROR_NO_MATCH || e == SpeechRecognizer.ERROR_SPEECH_TIMEOUT)
        VoiceUi(false, error = "Não entendi. Toque no microfone e fale de novo.") else VoiceUi(false, error = "Erro no reconhecimento ($e)")); release() }
      override fun onResults(b: Bundle?) { val t = b?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
        release(); onState(null); if (t.isNotBlank()) onResult(t) }
      override fun onPartialResults(b: Bundle?) { last = b?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty(); onState(VoiceUi(partial = last)) }
      override fun onEvent(t: Int, p: Bundle?) {}
    })
    last = ""
    r.startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
      .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "pt-BR").putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
      .putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, false).putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1))
  }
  private var last = ""
  private fun release() { rec?.destroy(); rec = null }
  fun stop(clear: Boolean = true) { rec?.stopListening(); release(); if (clear) onState(null) }
}

object VoiceLevel { /** RMS dB from the recognizer (≈ -2..10) → 0..1. */ fun of(db: Float) = ((db + 2f) / 12f).coerceIn(0f, 1f) }
