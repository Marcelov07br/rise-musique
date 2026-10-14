package com.rise.musique

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.*
import com.rise.musique.ui.Mode
import com.rise.musique.ui.SearchResult
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/** Followed artists and the uploads already seen for each (so only new ones notify). */
object FollowStore {
  private fun prefs(c: Context) = c.getSharedPreferences("follow", 0)
  /** Followed artists live in the unified [Library] since 0.23; only the "seen" uploads stay here. */
  fun all(c: Context): Set<String> = Library.followed(c)
  fun toggle(c: Context, name: String, art: String = ""): Boolean { val on = Library.toggleArtist(c, name, art); if (on) FollowWorker.schedule(c); return on }
  fun seen(c: Context, name: String): Set<String> = prefs(c).getString("seen:$name", null)?.let { s -> JSONArray(s).let { a -> (0 until a.length()).map(a::getString).toSet() } } ?: emptySet()
  fun markSeen(c: Context, name: String, urls: Collection<String>) = prefs(c).edit().putString("seen:$name", JSONArray((seen(c, name) + urls).toList().takeLast(200)).toString()).apply()
  /** New = not seen before; the first check only records (no flood of notifications). Visible for tests. */
  fun newOnes(seen: Set<String>, latest: List<SearchResult>, firstCheck: Boolean) = if (firstCheck) emptyList() else latest.filter { it.url !in seen }
}

/** Every ~12 h: newest uploads per followed artist via yt-dlp; notifies with a "Baixar" action. */
class FollowWorker(ctx: Context, p: WorkerParameters) : CoroutineWorker(ctx, p) {
  override suspend fun doWork(): Result {
    val c = applicationContext
    val nm = c.getSystemService(NotificationManager::class.java)
    if (Build.VERSION.SDK_INT >= 26) nm.createNotificationChannel(NotificationChannel(CH, "Artistas seguidos", NotificationManager.IMPORTANCE_DEFAULT))
    for (name in FollowStore.all(c)) {
      val latest = runCatching { Engine.search("ytsearchdate8:$name") }.getOrNull() ?: continue
      val seen = FollowStore.seen(c, name)
      val fresh = FollowStore.newOnes(seen, latest.filter { it.channel.contains(name, true) || it.title.contains(name, true) }, seen.isEmpty())
      FollowStore.markSeen(c, name, latest.map { it.url })
      fresh.take(3).forEach { r ->
        val code = (r.url).hashCode()
        val dl = PendingIntent.getBroadcast(c, code, Intent(c, FollowDownloadReceiver::class.java).putExtra("r", JSONObject()
          .put("url", r.url).put("title", r.title).put("thumb", r.thumb).put("channel", r.channel).toString()),
          PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val open = PendingIntent.getActivity(c, code, Intent(c, MainActivity::class.java).setAction(Intent.ACTION_SEND).setType("text/plain")
          .putExtra(Intent.EXTRA_TEXT, r.url), PendingIntent.FLAG_IMMUTABLE)
        nm.notify(code, NotificationCompat.Builder(c, CH).setSmallIcon(android.R.drawable.stat_notify_more)
          .setContentTitle("Novo de $name").setContentText(r.title).setContentIntent(open).setAutoCancel(true)
          .addAction(android.R.drawable.stat_sys_download, "Baixar", dl).build())
      }
    }
    return Result.success()
  }
  companion object {
    const val CH = "follow"
    fun schedule(c: Context) = WorkManager.getInstance(c).enqueueUniquePeriodicWork("follow", ExistingPeriodicWorkPolicy.KEEP,
      PeriodicWorkRequestBuilder<FollowWorker>(12, TimeUnit.HOURS).setConstraints(Constraints(requiredNetworkType = NetworkType.CONNECTED))
        .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.MINUTES).build())
  }
}

/** "Baixar" from a follow notification: queues the download with the user's saved audio settings. */
class FollowDownloadReceiver : BroadcastReceiver() {
  override fun onReceive(c: Context, i: Intent) {
    val o = runCatching { JSONObject(i.getStringExtra("r")!!) }.getOrNull() ?: return
    val prefs = c.getSharedPreferences("rise", 0)
    val format = prefs.getString("format", "MP3")!!; val q = prefs.getString("quality_${Mode.Musica.name}", Mode.Musica.qualities.last())!!
    val tree = prefs.getString("tree", null)?.let(android.net.Uri::parse)
    DownloadWorker.enqueue(c, DownloadWorker.data(o.getString("url"), o.getString("title"), o.optString("thumb"), o.optString("channel"),
      Mode.Musica, q, format, null, tree))
    c.getSystemService(NotificationManager::class.java).cancel(o.getString("url").hashCode())
  }
}
