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
    val target = when { isLink(input) -> input.trim(); input.startsWith("ytsearch") -> input.trim()
      else -> "ytsearch${(offset + count).coerceIn(1, 100)}:${input.trim()}" }
    val req = YoutubeDLRequest(target).apply { addOption("-J"); addOption("--flat-playlist")
      addOption(if (isPlaylist(input)) "--yes-playlist" else "--no-playlist") }
    parseEntries(runInterruptible { YoutubeDL.getInstance().execute(req).out })
      .let { if (isLink(input) || input.startsWith("ytsearch")) it else it.drop(offset).take(count) }
  }

  /** Parses yt-dlp -J (flat) output into results. Visible for tests. */
  fun parseEntries(json: String): List<SearchResult> {
    val root = JSONObject(json)
    val list = root.optJSONArray("entries")?.let { a -> (0 until a.length()).mapNotNull { a.optJSONObject(it) } } ?: listOf(root)
    return list.map { o ->
      val id = o.optString("id")
      val url = o.optString("webpage_url").ifBlank { o.optString("url").ifBlank { "https://www.youtube.com/watch?v=$id" } }
      val thumb = o.optString("thumbnail").ifBlank {
        o.optJSONArray("thumbnails")?.let { t -> if (t.length() > 0) t.getJSONObject(t.length() - 1).optString("url") else null }
          ?.takeIf { it.isNotBlank() } ?: "https://i.ytimg.com/vi/$id/hqdefault.jpg" }
      SearchResult(url, o.optString("title"), o.optString("channel").ifBlank { o.optString("uploader") },
        o.optDouble("duration", 0.0).toInt(), thumb)
    }.distinctBy { it.url }
  }

  /** URL direta do áudio, pra prévia. */
  /** UA yt-dlp uses for the web client; ExoPlayer must send the same headers or googlevideo may answer 403. */
  const val DEFAULT_UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/146.0.0.0 Safari/537.36"
  /** Stream URL -> HTTP headers yt-dlp reported for it (read by PlaybackService's data source, same process). */
  val streamHeaders = java.util.concurrent.ConcurrentHashMap<String, Map<String, String>>()
  private val updateMutex = kotlinx.coroutines.sync.Mutex()
  @Volatile private var updated = false

  data class Stream(val url: String, val headers: Map<String, String>, val formatId: String = "", val ext: String = "m4a")

  /** Parses the `-j` output of yt-dlp into the direct URL and headers. Visible for tests. */
  fun parseStream(json: String): Stream {
    val o = JSONObject(json.lineSequence().first { it.trimStart().startsWith("{") })
    val url = o.optString("url").ifBlank {
      o.optJSONArray("requested_formats")?.optJSONObject(0)?.optString("url").orEmpty() }
    require(url.startsWith("http")) { "sem URL de áudio" }
    val h = o.optJSONObject("http_headers")
    val headers = buildMap { h?.keys()?.forEach { k -> put(k, h.getString(k)) } }
    return Stream(url, headers, o.optString("format_id"), o.optString("ext", "m4a"))
  }

  /** YouTube video id from a watch/short/music link. */
  fun videoId(url: String): String? = Regex("(?:v=|youtu\\.be/|/shorts/)([A-Za-z0-9_-]{11})").find(url)?.groupValues?.get(1)
  /** Song radio: YouTube Music's automatic mix for a track (list=RDAMVM<id>), without the seed itself. */
  fun radioUrl(id: String) = "https://www.youtube.com/watch?v=$id&list=RDAMVM$id"
  suspend fun radio(url: String, count: Int = 30): List<SearchResult> = withContext(Dispatchers.IO) {
    val id = videoId(url) ?: error("Link sem ID de vídeo")
    val req = YoutubeDLRequest(radioUrl(id)).apply { addOption("-J"); addOption("--flat-playlist"); addOption("--yes-playlist")
      addOption("--playlist-end", (count + 1).toString()) }
    parseEntries(runInterruptible { YoutubeDL.getInstance().execute(req).out }).filter { videoId(it.url) != id }.take(count)
  }

  /** One yt-dlp call for the cut sheet: audio stream (url + headers) and the storyboard. */
  suspend fun cutInfo(url: String): Pair<Stream, Storyboard?> = withContext(Dispatchers.IO) {
    val out = runInterruptible { YoutubeDL.getInstance().execute(YoutubeDLRequest(url).apply {
      addOption("-f", "ba[ext=m4a]/ba[acodec^=mp4a]/ba/b"); addOption("-j"); addOption("--no-playlist"); addOption("--no-warnings") }) }.out
    val stream = parseStream(out).also { streamHeaders[it.url] = it.headers }
    stream to CutInfo.storyboard(JSONObject(out.lineSequence().first { it.trimStart().startsWith("{") }))
  }

  suspend fun audioStream(url: String): Stream = withContext(Dispatchers.IO) {
    if (!updated) updateMutex.withLockSafe { }  // wait for an in-flight startup update
    val out = runInterruptible { YoutubeDL.getInstance().execute(YoutubeDLRequest(url).apply {
      addOption("-f", "ba[ext=m4a]/ba[acodec^=mp4a]/ba/b"); addOption("-j"); addOption("--no-playlist")
      addOption("--no-warnings")
    }) }.out
    parseStream(out).also { streamHeaders[it.url] = it.headers }
  }

  /** Video playback: every separate video-only https format (by height) + best m4a audio + subtitles (vtt). */
  data class VideoFormat(val height: Int, val url: String, val codec: String, val ext: String)
  data class VideoInfo(val title: String, val channel: String, val thumb: String, val formats: List<VideoFormat>, val audio: Stream?,
                       val muxed: VideoFormat?, val subtitles: Map<String, String>, val durationSec: Int)
  suspend fun videoInfo(url: String): VideoInfo = withContext(Dispatchers.IO) {
    if (!updated) updateMutex.withLockSafe { }
    val out = runInterruptible { YoutubeDL.getInstance().execute(YoutubeDLRequest(url).apply {
      addOption("-j"); addOption("--no-playlist"); addOption("--no-warnings") }) }.out
    parseVideo(out)
  }
  /** Visible for tests. Prefers H.264 per height (widest hardware support), then VP9, then AV1. */
  fun parseVideo(json: String): VideoInfo {
    val o = JSONObject(json.lineSequence().first { it.trimStart().startsWith("{") })
    val fs = o.optJSONArray("formats")?.let { a -> (0 until a.length()).map { a.getJSONObject(it) } }.orEmpty().filter { it.optString("protocol") == "https" }
    fun headers(f: JSONObject) = (f.optJSONObject("http_headers") ?: o.optJSONObject("http_headers"))?.let { h -> buildMap { h.keys().forEach { k -> put(k, h.getString(k)) } } }.orEmpty()
    fun rank(c: String) = when { c.startsWith("avc1") -> 0; c.startsWith("vp9") || c.startsWith("vp09") -> 1; else -> 2 }
    val video = fs.filter { it.optString("vcodec", "none") != "none" && it.optString("acodec", "none") == "none" && it.optInt("height") > 0 }
      .groupBy { it.optInt("height") }.map { (h, l) -> l.minBy { rank(it.optString("vcodec")) }.let { f ->
        streamHeaders[f.getString("url")] = headers(f); VideoFormat(h, f.getString("url"), f.optString("vcodec"), f.optString("ext")) } }.sortedBy { it.height }
    val audio = fs.filter { it.optString("vcodec", "none") == "none" && it.optString("acodec", "none") != "none" }
      .maxByOrNull { (if (it.optString("ext") == "m4a") 10_000 else 0) + it.optDouble("abr", 0.0) }
      ?.let { f -> Stream(f.getString("url"), headers(f), f.optString("format_id"), f.optString("ext")).also { streamHeaders[it.url] = it.headers } }
    val muxed = fs.filter { it.optString("vcodec", "none") != "none" && it.optString("acodec", "none") != "none" }.maxByOrNull { it.optInt("height") }
      ?.let { f -> streamHeaders[f.getString("url")] = headers(f); VideoFormat(f.optInt("height"), f.getString("url"), f.optString("vcodec"), f.optString("ext")) }
    val subs = LinkedHashMap<String, String>()
    listOf("subtitles", "automatic_captions").forEach { key -> o.optJSONObject(key)?.let { m ->
      listOf("pt-BR", "pt", "en").forEach { lang -> m.optJSONArray(lang)?.let { a -> (0 until a.length()).map { a.getJSONObject(it) }
        .firstOrNull { it.optString("ext") == "vtt" }?.let { if (lang !in subs) subs[if (key == "subtitles") lang else "$lang (auto)"] = it.getString("url") } } } } }
    return VideoInfo(o.optString("title"), o.optString("channel").ifBlank { o.optString("uploader") }, o.optString("thumbnail"), video, audio, muxed, subs, o.optInt("duration"))
  }

  private suspend inline fun kotlinx.coroutines.sync.Mutex.withLockSafe(block: () -> Unit) { lock(); try { block() } finally { unlock() } }

  suspend fun download(ctx: Context, url: String, mode: Mode, quality: String, format: String, cut: String?, tree: Uri?, procId: String,
                       prefill: File? = null, formatId: String? = null, title: String? = null,
                       onProgress: (Float, String) -> Unit): HistoryItem = withContext(Dispatchers.IO) {
    // Pasta persistente por trabalho: se o app morrer ou a rede cair, a nova tentativa continua o .part de onde parou.
    val tmp = File(ctx.filesDir, "dl/$procId").apply { mkdirs() }
    try {
    val req = YoutubeDLRequest(url).apply {
      addOption("--no-playlist"); addOption("--continue"); addOption("--retries", "10"); addOption("--fragment-retries", "10")
      // "Guardar": the streamed bytes are already the file yt-dlp would fetch (same format id); placed at its output name,
      // yt-dlp sees it as downloaded and only runs the tagging/cover/convert steps.
      val pre = prefill?.takeIf { it.exists() && formatId != null && mode == Mode.Musica && cut == null }
      if (pre != null) { val id = videoId(url) ?: "rise"; pre.copyTo(File(tmp, "$id.${pre.extension}"), true); pre.delete()
        addOption("-f", formatId!!); addOption("-o", "${tmp.absolutePath}/%(id)s.%(ext)s") }
      else addOption("-o", "${tmp.absolutePath}/%(title)s.%(ext)s")
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
    val f0 = tmp.listFiles()?.filter { it.extension in setOf("mp3", "mp4", "m4a", "opus", "flac", "webm", "mkv") }?.maxByOrNull { it.length() }
      ?: error("Nenhum arquivo gerado")
    val f = if (prefill != null && title != null && f0.nameWithoutExtension == videoId(url)) File(tmp, PrefillName.of(title) + "." + f0.extension).also { f0.renameTo(it) } else f0
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
    val dir = DocumentFile.fromTreeUri(ctx, tree)?.takeIf { it.canWrite() } ?: error("Sem acesso à pasta escolhida. Escolha a pasta de novo em Downloads.")
    val doc = dir.createFile(mime(f.extension), f.nameWithoutExtension) ?: error("Não foi possível criar o arquivo na pasta escolhida")
    try { (ctx.contentResolver.openOutputStream(doc.uri) ?: error("Pasta indisponível")).use { o -> f.inputStream().use { it.copyTo(o) } } }
    catch (e: Exception) { doc.delete(); throw e }
    return doc.uri.toString()
  }

  fun mime(ext: String) = when (ext.lowercase()) { "mp4" -> "video/mp4"; "m4a" -> "audio/mp4"; "opus" -> "audio/ogg"
    "flac" -> "audio/flac"; "webm" -> "video/webm"; "mkv" -> "video/x-matroska"; "wav" -> "audio/wav"; "ogg" -> "audio/ogg"; "aac" -> "audio/aac"; else -> "audio/mpeg" }

  suspend fun update(ctx: Context, force: Boolean = false) = withContext(Dispatchers.IO) {
    updateMutex.withLockSafe {
      if (!updated || force) runCatching { YoutubeDL.getInstance().updateYoutubeDL(ctx, YoutubeDL.UpdateChannel.STABLE) }
      updated = true
    } }
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


object PrefillName { /** File-system-safe title. Visible for tests. */
  fun of(t: String) = t.replace(Regex("[\\\\/:*?\"<>|\\p{Cntrl}]"), "_").trim().take(120).ifBlank { "Faixa" } }
