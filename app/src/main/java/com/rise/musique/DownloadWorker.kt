package com.rise.musique
import android.app.*
import android.content.Context
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.*
import com.rise.musique.ui.Mode
import com.yausername.youtubedl_android.YoutubeDL
import kotlinx.coroutines.sync.*
import kotlinx.coroutines.CancellationException

/** Cada item é um trabalho independente (cancelar um não derruba os outros); o Mutex garante um por vez. */
class DownloadWorker(ctx: Context, p: WorkerParameters) : CoroutineWorker(ctx, p) {
  private val nm = ctx.getSystemService(NotificationManager::class.java)
  private fun notif(title: String, p: Float) = NotificationCompat.Builder(applicationContext, CH)
    .setSmallIcon(android.R.drawable.stat_sys_download).setContentTitle(title).setOngoing(true).setSilent(true)
    .setProgress(100, (p * 100).toInt(), p <= 0f).build()
  private fun info(title: String, p: Float) = if (Build.VERSION.SDK_INT >= 29)
    ForegroundInfo(id.hashCode(), notif(title, p), ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC) else ForegroundInfo(id.hashCode(), notif(title, p))

  override suspend fun doWork(): Result = lock.withLock {
    if (Build.VERSION.SDK_INT >= 26) nm.createNotificationChannel(NotificationChannel(CH, "Downloads", NotificationManager.IMPORTANCE_LOW))
    val d = inputData; val title = d.getString("title") ?: "Download"
    var last = 0L
    var lastStage = "Preparando"
    runCatching {
      setForeground(info(title, 0f))
      setProgress(workDataOf("p" to 0f, "running" to true, "stage" to "Preparando"))
      val item = Engine.download(applicationContext, d.getString("url")!!, Mode.valueOf(d.getString("mode")!!), d.getString("quality")!!,
        d.getString("format") ?: "MP3", d.getString("cut"), d.getString("tree")?.let(Uri::parse), id.toString(),
        d.getString("prefill")?.let { java.io.File(it) }, d.getString("formatId"), title) { p, line ->
        val now = System.currentTimeMillis()
        val stage = if (line.startsWith("[RiseSave]")) "Salvando" else DownloadStage.fromLine(line, lastStage)
        if (now - last > 500 || stage != lastStage) { last = now; lastStage = stage
          setProgressAsync(workDataOf("p" to p, "line" to line, "running" to true, "stage" to stage)); nm.notify(id.hashCode(), notif("$title · $stage", p)) }
      }
      val saved = item.copy(art = item.art.ifBlank { d.getString("art").orEmpty() }, artist = item.artist.ifBlank { d.getString("artist").orEmpty() })
      HistoryStore.add(applicationContext, saved)
      // The library keeps the YouTube id of every download so it can still play (or re-download) from anywhere.
      Engine.videoId(d.getString("url").orEmpty())?.let { runCatching { Library.rememberFile(applicationContext, saved, it) } }
    }.fold({ java.io.File(applicationContext.filesDir, "dl/$id").deleteRecursively(); Result.success(workDataOf("stage" to "Concluído")) },
      { if (it is CancellationException) { YoutubeDL.getInstance().destroyProcessById(id.toString()); throw it }
        // Rede/servidor: tenta de novo com espera crescente (WorkManager), mantendo o arquivo parcial.
        if (DownloadRetry.shouldRetry(it.message, runAttemptCount)) return@withLock Result.retry()
        java.io.File(applicationContext.filesDir, "dl/$id").deleteRecursively()
        ErrorLog.w("download", "download falhou", it); Result.failure(workDataOf("err" to NetErrors.friendly(it).take(140))) })
  }
  companion object {
    const val CH = "downloads"; const val TAG = "rise"
    private val lock = Mutex()
    fun enqueue(ctx: Context, data: Data) {
      val req = OneTimeWorkRequestBuilder<DownloadWorker>().addTag(TAG).setInputData(data)
        .setConstraints(Constraints(requiredNetworkType = if (wifiOnly(ctx)) NetworkType.UNMETERED else NetworkType.CONNECTED))
        .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, java.util.concurrent.TimeUnit.SECONDS).build()
      Jobs.remember(ctx, req.id.toString(), data); WorkManager.getInstance(ctx).enqueue(req) }
    fun data(url: String, title: String, art: String, artist: String, mode: Mode, quality: String, format: String, cut: String?, tree: Uri?) =
      workDataOf("url" to url, "title" to title, "art" to art, "artist" to artist, "mode" to mode.name, "quality" to quality, "format" to format, "cut" to cut, "tree" to tree?.toString())
    fun wifiOnly(ctx: Context) = ctx.getSharedPreferences("rise", 0).getBoolean("wifiOnly", false)
    fun cancel(ctx: Context, id: String) { YoutubeDL.getInstance().destroyProcessById(id); java.io.File(ctx.filesDir, "dl/$id").deleteRecursively()
      WorkManager.getInstance(ctx).cancelWorkById(java.util.UUID.fromString(id)); Jobs.forget(ctx, id) }
  }
}

/** Guarda os dados de cada item da fila pra mostrar o título e permitir tentar de novo. */
object Jobs {
  private fun prefs(c: Context) = c.getSharedPreferences("jobs", 0)
  fun remember(c: Context, id: String, d: Data) = prefs(c).edit().putString(id,
    org.json.JSONObject(d.keyValueMap.filterValues { it != null }).toString()).apply()
  fun forget(c: Context, id: String) = prefs(c).edit().remove(id).apply()
  fun get(c: Context, id: String): Data? = prefs(c).getString(id, null)?.let { s ->
    val o = org.json.JSONObject(s); Data.Builder().apply { o.keys().forEach { k -> putString(k, o.getString(k)) } }.build() }
}


object DownloadRetry {
  const val MAX = 5
  private val transient = listOf("timed out", "timeout", "connection", "network", "unable to download", "http error 5", "http error 429",
    "temporary failure", "reset by peer", "unreachable", "ssl", "eof", "incomplete")
  /** Visible for tests. */
  fun shouldRetry(msg: String?, attempt: Int) = attempt < MAX && msg != null && transient.any { msg.contains(it, ignoreCase = true) }
}
