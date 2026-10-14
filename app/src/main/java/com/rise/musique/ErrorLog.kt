package com.rise.musique

import android.content.Context
import android.content.Intent
import android.os.Build
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Registro de erro exportável (0.24, igual ao do Rise Edit): log em anel num arquivo (`filesDir/log/app.log`,
 * ~256 KB, metade mais antiga descartada), crashes não tratados, ApplicationExitInfo e logcat do próprio processo.
 */
object ErrorLog {
  const val MaxBytes = 256 * 1024
  @Volatile private var dir: File? = null
  private val lock = Any()
  private val fmt = ThreadLocal.withInitial { SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US) }

  fun init(ctx: Context) { dir = File(ctx.filesDir, "log").apply { mkdirs() } }
  private fun file() = dir?.let { File(it, "app.log") }

  fun i(tag: String, msg: String) = write("I", tag, msg, null)
  fun w(tag: String, msg: String, e: Throwable? = null) = write("W", tag, msg, e)
  fun e(tag: String, msg: String, e: Throwable? = null) = write("E", tag, msg, e)

  fun write(level: String, tag: String, msg: String, e: Throwable?) {
    val f = file() ?: return
    val line = buildString { append(fmt.get()!!.format(Date())).append(' ').append(level).append('/').append(tag).append(": ").append(msg).append('\n')
      if (e != null) append(e.stackTraceToString()).append('\n') }
    synchronized(lock) { runCatching { f.appendText(line); trim(f, MaxBytes) } }
  }

  /** Mantém só a metade mais nova quando passa de [max] (corta numa quebra de linha). Visível para testes. */
  fun trim(f: File, max: Int) {
    if (f.length() <= max) return
    val b = f.readBytes(); var from = b.size - max / 2
    while (from < b.size && b[from] != '\n'.code.toByte()) from++
    f.writeBytes(b.copyOfRange((from + 1).coerceAtMost(b.size), b.size))
  }

  fun read(): String = file()?.takeIf { it.exists() }?.readText().orEmpty()

  private fun exitInfo(ctx: Context): String {
    if (Build.VERSION.SDK_INT < 30) return "(ApplicationExitInfo requer Android 11+)"
    return runCatching {
      val am = ctx.getSystemService(android.app.ActivityManager::class.java)
      am.getHistoricalProcessExitReasons(ctx.packageName, 0, 8).joinToString("\n") { x ->
        "${fmt.get()!!.format(Date(x.timestamp))} pid=${x.pid} motivo=${x.reason} status=${x.status} imp=${x.importance} ${x.description.orEmpty()}" }
        .ifBlank { "(nenhuma saída registrada)" }
    }.getOrElse { "(indisponível: ${it.message})" }
  }

  private fun logcat(): String = runCatching {
    val p = ProcessBuilder("logcat", "-d", "-v", "threadtime", "-t", "2000", "--pid=${android.os.Process.myPid()}").redirectErrorStream(true).start()
    p.inputStream.bufferedReader().use { it.readText() }.also { p.waitFor() }
  }.getOrElse { "(logcat indisponível: ${it.message})" }

  /** Monta o relatório completo. */
  fun report(ctx: Context): String = buildString {
    appendLine("Rise Musique ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
    appendLine("${Build.MANUFACTURER} ${Build.MODEL} · Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
    appendLine("Gerado em ${fmt.get()!!.format(Date())}")
    File(ctx.filesDir, "crash.txt").takeIf { it.exists() }?.let { appendLine("\n== Último crash ==\n${it.readText()}") }
    appendLine("\n== Saídas do processo (ApplicationExitInfo) ==\n${exitInfo(ctx)}")
    appendLine("\n== Registro do app ==\n${read().ifBlank { "(vazio)" }}")
    appendLine("\n== Logcat do app ==\n${logcat()}")
  }

  /** Grava em `cacheDir/share/` e abre a folha de compartilhamento. */
  fun export(ctx: Context) {
    val out = File(ctx.cacheDir, "share").apply { mkdirs() }.let { File(it, "rise-musique-registro-${BuildConfig.VERSION_NAME}.txt") }
    out.writeText(report(ctx))
    val uri = androidx.core.content.FileProvider.getUriForFile(ctx, ctx.packageName + ".files", out)
    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_STREAM, uri)
      .putExtra(Intent.EXTRA_SUBJECT, "Registro de erro do Rise Musique ${BuildConfig.VERSION_NAME}").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    ctx.startActivity(Intent.createChooser(send, "Exportar registro de erro").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
  }
}
