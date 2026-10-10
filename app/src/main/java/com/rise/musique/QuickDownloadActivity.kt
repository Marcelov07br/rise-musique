package com.rise.musique
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import com.rise.musique.ui.Mode

/** "Baixar com Rise" no Compartilhar: põe na fila com o último formato usado, sem abrir o app. */
class QuickDownloadActivity : Activity() {
  override fun onCreate(s: Bundle?) { super.onCreate(s)
    val text = intent?.getStringExtra(Intent.EXTRA_TEXT).orEmpty()
    val url = Regex("https?://\\S+").find(text)?.value
    if (url == null) { Toast.makeText(this, "Nenhum link encontrado", Toast.LENGTH_SHORT).show(); finish(); return }
    val p = getSharedPreferences("rise", 0)
    val mode = runCatching { Mode.valueOf(p.getString("mode", "Musica")!!) }.getOrDefault(Mode.Musica)
    val quality = p.getString("quality_${mode.name}", mode.qualities.last())!!
    val format = if (mode == Mode.Musica) p.getString("format", "MP3")!! else "MP4"
    val title = intent.getStringExtra(Intent.EXTRA_SUBJECT)?.removePrefix("Watch \"")?.removeSuffix("\" on YouTube")?.ifBlank { null } ?: url
    val id = Regex("(?:v=|youtu\\.be/|shorts/)([\\w-]{11})").find(url)?.groupValues?.get(1)
    val art = id?.let { "https://i.ytimg.com/vi/$it/hqdefault.jpg" }.orEmpty()
    DownloadWorker.enqueue(this, DownloadWorker.data(url, title, art, "", mode, quality, format, null, p.getString("tree", null)?.let(Uri::parse)))
    Toast.makeText(this, "Na fila: ${mode.label} · ${if (format == "FLAC") "FLAC" else quality}", Toast.LENGTH_SHORT).show()
    finish()
  }
}
