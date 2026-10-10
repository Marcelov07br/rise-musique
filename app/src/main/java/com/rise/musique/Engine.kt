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

  /** Busca sem baixar: link vira 1 resultado, termo vira até 5. */
  suspend fun search(input: String): List<SearchResult> = withContext(Dispatchers.IO) {
    val target = if (isLink(input)) input.trim() else "ytsearch5:${input.trim()}"
    val req = YoutubeDLRequest(target).apply { addOption("-J"); addOption("--flat-playlist")
      addOption(if (isPlaylist(input)) "--yes-playlist" else "--no-playlist") }
    val root = JSONObject(YoutubeDL.getInstance().execute(req).out)
    val list = root.optJSONArray("entries")?.let { a -> List(a.length()) { a.getJSONObject(it) } } ?: listOf(root)
    list.map { o ->
      val id = o.optString("id")
      val url = o.optString("webpage_url").ifBlank { o.optString("url").ifBlank { "https://www.youtube.com/watch?v=$id" } }
      val thumb = o.optString("thumbnail").ifBlank {
        o.optJSONArray("thumbnails")?.let { t -> if (t.length() > 0) t.getJSONObject(t.length() - 1).optString("url") else null }
          ?.takeIf { it.isNotBlank() } ?: "https://i.ytimg.com/vi/$id/hqdefault.jpg" }
      SearchResult(url, o.optString("title"), o.optString("channel").ifBlank { o.optString("uploader") },
        o.optDouble("duration", 0.0).toInt(), thumb)
    }
  }

  /** URL direta do áudio, pra prévia. */
  suspend fun audioStream(url: String): String = withContext(Dispatchers.IO) {
    YoutubeDL.getInstance().execute(YoutubeDLRequest(url).apply { addOption("-f", "ba"); addOption("-g"); addOption("--no-playlist") })
      .out.lineSequence().first { it.startsWith("http") }
  }

  suspend fun download(ctx: Context, url: String, mode: Mode, quality: String, format: String, cut: String?, tree: Uri?, procId: String,
                       onProgress: (Float, String) -> Unit): HistoryItem = withContext(Dispatchers.IO) {
    val tmp = File(ctx.cacheDir, "dl-$procId").apply { deleteRecursively(); mkdirs() }
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
    YoutubeDL.getInstance().execute(req, procId) { p, _, line -> onProgress(p / 100f, line) }
    val f = tmp.listFiles()?.filter { it.extension in setOf("mp3", "mp4", "m4a", "opus", "flac", "webm", "mkv") }?.maxByOrNull { it.length() }
      ?: error("Nenhum arquivo gerado")
    val saved = save(ctx, f, tree)
    val item = HistoryItem(f.nameWithoutExtension, "${f.extension.uppercase()} · ${if (format == "FLAC" && mode == Mode.Musica) "sem perda" else quality} · ${"%.1f".format(f.length() / 1e6)} MB", saved)
    tmp.deleteRecursively(); item
  }

  private fun save(ctx: Context, f: File, tree: Uri?): String {
    if (tree == null) { val out = File(ctx.getExternalFilesDir(null), f.name); f.copyTo(out, true); return out.absolutePath }
    val dir = DocumentFile.fromTreeUri(ctx, tree)!!
    val doc = dir.createFile(mime(f.extension), f.nameWithoutExtension)!!
    ctx.contentResolver.openOutputStream(doc.uri)!!.use { o -> f.inputStream().use { it.copyTo(o) } }
    return doc.uri.toString()
  }

  fun mime(ext: String) = when (ext.lowercase()) { "mp4" -> "video/mp4"; "m4a" -> "audio/mp4"; "opus" -> "audio/ogg"
    "flac" -> "audio/flac"; "webm" -> "video/webm"; "mkv" -> "video/x-matroska"; else -> "audio/mpeg" }

  suspend fun update(ctx: Context) = withContext(Dispatchers.IO) {
    runCatching { YoutubeDL.getInstance().updateYoutubeDL(ctx, YoutubeDL.UpdateChannel.STABLE) } }
}

object HistoryStore {
  private fun file(ctx: Context) = File(ctx.filesDir, "history.json")
  @Synchronized fun load(ctx: Context): List<HistoryItem> = runCatching {
    val a = JSONArray(file(ctx).readText())
    List(a.length()) { a.getJSONObject(it).run { HistoryItem(getString("t"), getString("m"), optString("u"), optString("a"), optString("r")) } }
  }.getOrDefault(emptyList())
  @Synchronized fun add(ctx: Context, item: HistoryItem) = write(ctx, listOf(item) + load(ctx))
  @Synchronized fun remove(ctx: Context, item: HistoryItem) = write(ctx, load(ctx).filterNot { it.uri == item.uri && it.title == item.title })
  private fun write(ctx: Context, l: List<HistoryItem>) = file(ctx).writeText(JSONArray(l.take(50)
    .map { JSONObject().put("t", it.title).put("m", it.meta).put("u", it.uri).put("a", it.art).put("r", it.artist) }).toString())
}
