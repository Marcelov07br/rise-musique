package com.rise.musique.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Open-source pieces the app ships with (name, what for, license). */
val Credits = listOf(
  Triple("yt-dlp", "Busca, streams e downloads", "Unlicense"),
  Triple("youtubedl-android", "yt-dlp e Python no Android", "GPL-3.0"),
  Triple("FFmpeg", "Conversão, corte e capas", "LGPL-2.1+ / GPL"),
  Triple("AndroidX Media3 (ExoPlayer, Session, Transformer)", "Reprodução, sessão de mídia, vídeos de trecho", "Apache-2.0"),
  Triple("Jetpack Compose e Material 3", "Interface", "Apache-2.0"),
  Triple("Glance", "Widget", "Apache-2.0"),
  Triple("WorkManager", "Downloads e análise em segundo plano", "Apache-2.0"),
  Triple("Coil", "Carregamento de imagens", "Apache-2.0"),
  Triple("Palette", "Cores das capas", "Apache-2.0"),
  Triple("Kotlin e Coroutines", "Linguagem e concorrência", "Apache-2.0"),
  Triple("LRCLIB", "Letras sincronizadas (serviço)", "Dados públicos"),
)
const val ReleasesUrl = "https://github.com/Marcelov07br/rise-musique-releases/releases"

/** "What's new" from the bundled RELEASE_NOTES.md (copied into assets at build time). */
fun releaseNotes(ctx: android.content.Context): String = runCatching { ctx.assets.open("release_notes.md").bufferedReader().use { it.readText() } }.getOrDefault("")

/** Light markdown: "# " title, "- **x**: y" bullets. */
@Composable private fun Notes(md: String) {
  val c = MaterialTheme.colorScheme
  md.lines().filter { it.isNotBlank() && !it.startsWith("# ") }.forEach { l ->
    val bullet = l.startsWith("- ")
    val t = l.removePrefix("- ")
    val bold = Regex("^\\*\\*(.+?)\\*\\*:?").find(t)
    Row(Modifier.padding(vertical = 3.dp)) {
      if (bullet) Text("•  ", color = c.onSurfaceVariant)
      Text(androidx.compose.ui.text.buildAnnotatedString {
        if (bold != null) { pushStyle(androidx.compose.ui.text.SpanStyle(fontWeight = FontWeight.SemiBold)); append(bold.groupValues[1]); pop(); append(":"); append(t.substring(bold.range.last + 1).removePrefix(":")) }
        else append(t) }, fontSize = 14.sp, lineHeight = 20.sp)
    }
  }
}

@Composable fun AboutContent(version: String, notes: String, openReleases: () -> Unit, exportLog: () -> Unit = {}, back: () -> Unit) {
  val c = MaterialTheme.colorScheme
  Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(bottom = 32.dp).navigationBarsPadding(),
    verticalArrangement = Arrangement.spacedBy(12.dp)) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      IconButton(back) { Icon(Icons.Rounded.ArrowBack, "Voltar") }
      Text("Sobre", style = MaterialTheme.typography.titleLarge)
    }
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
      Text("rise", fontSize = 56.sp, fontWeight = FontWeight.Bold, letterSpacing = (-2.3).sp)
      Text("Rise Musique $version", color = c.onSurfaceVariant)
    }
    Text("Novidades desta versão", fontSize = 18.sp, fontWeight = FontWeight.Bold)
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(c.surfaceVariant.copy(alpha = .6f)).padding(16.dp)) {
      if (notes.isBlank()) Text("Notas indisponíveis.", color = c.onSurfaceVariant) else Notes(notes) }
    PillButton("Todas as versões", openReleases, Modifier.fillMaxWidth(), Icons.Rounded.OpenInNew, filled = false)
    PillButton("Exportar registro de erro", exportLog, Modifier.fillMaxWidth(), Icons.Rounded.Share, filled = false)
    Text("Código aberto", fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
    Credits.forEach { (name, why, lic) ->
      Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) { Text(name, fontSize = 15.sp); Text(why, color = c.onSurfaceVariant, fontSize = 12.sp) }
        Text(lic, color = c.onSurfaceVariant, fontSize = 12.sp, modifier = Modifier.padding(start = 8.dp))
      }
    }
    Text("Feito com carinho para o Marce.", color = c.onSurfaceVariant, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
  }
}

/** Crash report: everything needed to find the cause. Visible for tests. */
object CrashReport {
  fun build(version: String, code: Int, manufacturer: String, model: String, release: String, sdk: Int, thread: String, at: Long, e: Throwable) = buildString {
    appendLine("Rise Musique $version ($code)")
    appendLine("Aparelho: $manufacturer $model")
    appendLine("Android $release (API $sdk)")
    appendLine("Thread: $thread")
    appendLine("Quando: " + java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z", java.util.Locale.ROOT).format(java.util.Date(at)))
    appendLine(); append(e.stackTraceToString())
  }
  /** One-line summary for the dialog: deepest cause "Tipo: mensagem" and the first app frame. */
  fun summary(report: String): String {
    val lines = report.lines()
    val causes = lines.filter { Regex("^(Caused by: )?[a-zA-Z_$][\\w.$]*(Exception|Error)\\b.*").matches(it.trim()) }
    val top = (causes.lastOrNull() ?: lines.firstOrNull { it.isNotBlank() } ?: "").trim().removePrefix("Caused by: ")
    val short = top.substringAfterLast('.', top).let { if (top.contains(':')) top.substringBefore(':').substringAfterLast('.') + ":" + top.substringAfter(':') else it }
    val frame = lines.firstOrNull { it.trim().startsWith("at com.rise.musique") }?.trim()?.removePrefix("at com.rise.musique.")
    return listOfNotNull(short.take(160), frame?.let { "em $it" }).joinToString("\n")
  }
}

/** Glass dialog after a crash: summary, Enviar relatório (share sheet), Copiar, Fechar. */
@Composable fun CrashDialog(report: String, send: () -> Unit, copy: () -> Unit, close: () -> Unit) {
  val c = MaterialTheme.colorScheme
  androidx.compose.ui.window.Dialog(close) { CrashCard(report, send, copy, close) }
}

@Composable fun CrashCard(report: String, send: () -> Unit, copy: () -> Unit, close: () -> Unit) {
  val c = MaterialTheme.colorScheme
  var copied by remember { mutableStateOf(false) }
  val shape = RoundedCornerShape(28.dp)
  Column(Modifier.fillMaxWidth().clip(shape).background(c.surface.copy(alpha = .86f)).border(1.dp, androidx.compose.ui.graphics.Brush.verticalGradient(listOf(c.onSurface.copy(.22f), c.onSurface.copy(.04f))), shape)
      .padding(22.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
    Icon(Icons.Rounded.BugReport, null, tint = accentColor(), modifier = Modifier.size(28.dp))
    Text("O app fechou sozinho da última vez", fontSize = 20.sp, fontWeight = FontWeight.Bold, lineHeight = 26.sp)
    Text("Mande o relatório para a gente achar a causa. Ele traz o aparelho, a versão do Android e do app e o rastro do erro, nada pessoal.",
      color = c.onSurfaceVariant, fontSize = 14.sp, lineHeight = 20.sp)
    Text(CrashReport.summary(report), Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color.Black.copy(alpha = .25f)).padding(12.dp),
      fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace, fontSize = 12.sp, lineHeight = 17.sp, maxLines = 5)
    PillButton("Enviar relatório", send, Modifier.fillMaxWidth(), Icons.Rounded.Send)
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
      PillButton(if (copied) "Copiado" else "Copiar", { copy(); copied = true }, Modifier.weight(1f), if (copied) Icons.Rounded.Check else Icons.Rounded.ContentCopy, filled = false)
      PillButton("Fechar", close, Modifier.weight(1f), filled = false)
    }
  }
}
