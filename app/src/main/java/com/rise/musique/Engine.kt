package com.rise.musique
import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.rise.musique.ui.*
import com.yausername.youtubedl_android.*
import kotlinx.coroutines.*
import org.json.*
import java.io.File

object Engine {
  fun isLink(s: String) = s.trim().startsWith("http")
  fun isPlaylist(s: String) = isLink(s) && ("list=" in s || "/playlist" in s || "/album" in s)

  /** More results request a larger bounded search, then take the next page. */
  suspend fun search(input: String, offset: Int = 0, count: Int = 10): List<SearchResult> = withContext(Dispatchers.IO) {
    val target = if (isLink(input)) input.trim() else "ytsearch${(offset + count).coerceIn(1, 100)}:${input.trim()}"
    val req = YoutubeDLRequest(target).apply { addOption("-J"); addOption("--flat-playlist")
      addOption(if (isPlaylist(input)) "--yes-playlist" else "--no-playlist") }
    val root = JSONObject(runInterruptible { YoutubeDL.getInstance().execute(req).out })
    val list = root.optJSONArray("entries")?.let { a -> (0 until a.length()).mapNotNull { a.optJSONObject(it) } } ?: listOf(root)
    list.map { o ->
      val id = o.optString("id")
      val url = o.optString("webpage_url").ifBlank { o.optString("url").ifBlank { "https://www.youtube.com/watch?v=$id" } }
      val thumb = o.optString("thumbnail").ifBlank {
        o.optJSONArray("thumbnails")?.let { t -> if (t.length() > 0) t.getJSONObject(t.length() - 1).optString("url") else null }
          ?.takeIf { it.isNotBlank() } ?: "https://i.ytimg.com/vi/$id/hqdefault.jpg" }
      SearchResult(url, o.optString("title"), o.optString("channel").ifBlank { o.optString("uploader") },
        o.optDouble("duration", 0.0).toInt(), thumb)
    }.distinctBy { it.url }.let { if (isLink(input)) it else it.drop(offset).take(count) }
  }

  /** URL direta do áudio, pra prévia. */
  suspend fun audioStream(url: String): String = withContext(Dispatchers.IO) {
    runInterruptible { YoutubeDL.getInstance().execute(YoutubeDLRequest(url).apply { addOption("-f", "ba"); addOption("-g"); addOption("--no-playlist") }) }
      .out.lineSequence().first { it.startsWith("http") }
  }

  suspend fun download(ctx: Context, url: String, mode: Mode, quality: String, format: String, cut: String?, tree: Uri?, procId: String,
                       onProgress: (Float, String) -> Unit): HistoryItem = withContext(Dispatchers.IO) {
    val tmp = File(ctx.cacheDir, "dl-$procId").apply { deleteRecursively(); mkdirs() }
    try {
    val req = YoutubeDLRequest(url).apply {
      addOption("--no-playlist"); addOption("-o", "${tmp.absolutePath}/%(title)s.%(ext)s")
      addOption("--embed-metadata")
      if (cut != null) { addOption("--download-sections", "*$cut"); addOption("--force-keyframes-at-cuts") }
      if (mode == Mode.Musica) {
        addOption("-x"); addOption("--audio-format", format.lowercase())
        if (format != "FLAC") addOption("--audio-quality", quality.removeSuffix("k") + "K")
        addOption("--parse-metadata", "%(artist,creator,uploader)s:%(meta_artist)s")
        addOption("--parse-metadata", "%(track,title)s:%(meta_title)s")
        addOption("--parse-metadata", "%(album,title)s:%(meta_album)s")
        if (format != "Opus") { addOption("--embed-thumbnail"); addOption("--convert-thumbnails", "jpg") }
        addOption("--ppa", "ThumbnailsConvertor+FFmpeg_o:-c:v mjpeg -vf crop=\"'if(gt(ih,iw),iw,ih)':'if(gt(iw,ih),ih,iw)'\"")
      } else {
        val h = quality.removeSuffix("p").toIntOrNull()
        addOption("-f", if (h == null) "bv*+ba/b" else "bv*[height<=$h]+ba/b[height<=$h]")
        addOption("--merge-output-format", "mp4"); addOption("--embed-thumbnail")
      }
    }
    val job = currentCoroutineContext()
    try { runInterruptible { YoutubeDL.getInstance().execute(req, procId) { p, _, line -> job.ensureActive(); onProgress(p / 100f, line) } } }
    finally { YoutubeDL.getInstance().destroyProcessById(procId) }
    val f = tmp.listFiles()?.filter { it.extension in setOf("mp3", "mp4", "m4a", "opus", "flac", "webm", "mkv") }?.maxByOrNull { it.length() }
      ?: error("Nenhum arquivo gerado")
    onProgress(1f, "[RiseSave] Salvando")
    val saved = save(ctx, f, tree)
    val item = HistoryItem(f.nameWithoutExtension, "${f.extension.uppercase()} · ${if (format == "FLAC" && mode == Mode.Musica) "FLAC" else quality} · ${"%.1f".format(f.length() / 1e6)} MB", saved, video = mode == Mode.Video)
    LocalMedia.metadata(ctx, if (saved.startsWith("content://")) Uri.parse(saved) else Uri.fromFile(File(saved)), item)
    } finally { tmp.deleteRecursively() }
  }

  private fun save(ctx: Context, f: File, tree: Uri?): String {
    if (tree == null) {
      val dir = ctx.getExternalFilesDir(null) ?: error("Armazenamento indisponível")
      var out = File(dir, f.name); var index = 1
      while (out.exists()) { out = File(dir, "${f.nameWithoutExtension} (${index++}).${f.extension}") }
      try { f.copyTo(out, false); return out.absolutePath } catch (e: Exception) { out.delete(); throw e }
    }
    val dir = DocumentFile.fromTreeUri(ctx, tree)!!
    val doc = dir.createFile(mime(f.extension), f.nameWithoutExtension)!!
    try { ctx.contentResolver.openOutputStream(doc.uri)!!.use { o -> f.inputStream().use { it.copyTo(o) } } }
    catch (e: Exception) { doc.delete(); throw e }
    return doc.uri.toString()
  }

  fun mime(ext: String) = when (ext.lowercase()) { "mp4" -> "video/mp4"; "m4a" -> "audio/mp4"; "opus" -> "audio/ogg"
    "flac" -> "audio/flac"; "webm" -> "video/webm"; "mkv" -> "video/x-matroska"; "wav" -> "audio/wav"; "ogg" -> "audio/ogg"; "aac" -> "audio/aac"; else -> "audio/mpeg" }

  suspend fun update(ctx: Context) = withContext(Dispatchers.IO) {
    runCatching { YoutubeDL.getInstance().updateYoutubeDL(ctx, YoutubeDL.UpdateChannel.STABLE) } }
}

object HistoryStore {
  private fun file(ctx: Context) = File(ctx.filesDir, "history.json")
  @Synchronized fun json(ctx: Context): JSONArray = JSONArray(AtomicJson.read(file(ctx)) ?: "[]")
  @Synchronized fun merge(ctx: Context, incoming: JSONArray): Int {
    val (merged, added) = BackupMerge.history(json(ctx), incoming)
    AtomicJson.write(file(ctx), merged.toString())
    return added
  }
  @Synchronized fun load(ctx: Context): List<HistoryItem> = runCatching {
    val a = JSONArray(AtomicJson.read(file(ctx)) ?: "[]")
    List(a.length()) { TrackJson.decode(a.getJSONObject(it)) }
  }.getOrDefault(emptyList())
  @Synchronized fun add(ctx: Context, item: HistoryItem) {
    // Refuse to overwrite an unreadable library with an empty list.
    json(ctx)
    write(ctx, (listOf(item) + load(ctx)).distinctBy { it.uri })
  }
  @Synchronized fun addAll(ctx: Context, items: List<HistoryItem>) {
    json(ctx); write(ctx, (items + load(ctx)).distinctBy { it.uri })
  }
  @Synchronized fun replace(ctx: Context, item: HistoryItem) {
    json(ctx); write(ctx, load(ctx).map { if (it.uri == item.uri) item else it })
  }
  @Synchronized fun remove(ctx: Context, item: HistoryItem) = write(ctx, load(ctx).filterNot { it.uri == item.uri && it.title == item.title })
  private fun write(ctx: Context, l: List<HistoryItem>) = AtomicJson.write(file(ctx), JSONArray(l.map(TrackJson::encode)).toString())
}

