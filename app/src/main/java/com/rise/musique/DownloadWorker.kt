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
    runCatching { setForeground(info(title, 0f)) }
    setProgress(workDataOf("p" to 0f, "running" to true))
    var last = 0L
    runCatching {
      Engine.download(applicationContext, d.getString("url")!!, Mode.valueOf(d.getString("mode")!!), d.getString("quality")!!,
        d.getString("format") ?: "MP3", d.getString("cut"), d.getString("tree")?.let(Uri::parse), id.toString()) { p, line ->
        val now = System.currentTimeMillis()
        if (now - last > 500) { last = now
          setProgressAsync(workDataOf("p" to p, "line" to line, "running" to true)); nm.notify(id.hashCode(), notif(title, p)) }
      }
    }.fold({ HistoryStore.add(applicationContext, it.copy(art = d.getString("art").orEmpty(), artist = d.getString("artist").orEmpty())); Jobs.forget(applicationContext, id.toString()); Result.success() },
      { if (isStopped) Result.failure() else Result.failure(workDataOf("err" to (it.message?.lineSequence()?.lastOrNull { l -> l.isNotBlank() }?.take(140) ?: "erro"))) })
  }
  companion object {
    const val CH = "downloads"; const val TAG = "rise"
    private val lock = Mutex()
    fun enqueue(ctx: Context, data: Data) {
      val req = OneTimeWorkRequestBuilder<DownloadWorker>().addTag(TAG).setInputData(data)
        .setConstraints(Constraints(requiredNetworkType = NetworkType.CONNECTED)).build()
      Jobs.remember(ctx, req.id.toString(), data); WorkManager.getInstance(ctx).enqueue(req) }
    fun data(url: String, title: String, art: String, artist: String, mode: Mode, quality: String, format: String, cut: String?, tree: Uri?) =
      workDataOf("url" to url, "title" to title, "art" to art, "artist" to artist, "mode" to mode.name, "quality" to quality, "format" to format, "cut" to cut, "tree" to tree?.toString())
    fun cancel(ctx: Context, id: String) { YoutubeDL.getInstance().destroyProcessById(id)
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
