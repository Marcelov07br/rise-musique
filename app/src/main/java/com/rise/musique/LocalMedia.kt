package com.rise.musique

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import androidx.documentfile.provider.DocumentFile
import com.rise.musique.ui.HistoryItem
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.File
import java.security.MessageDigest

object LocalMedia {
  private val audio = setOf("mp3", "m4a", "aac", "ogg", "opus", "flac", "wav", "wma", "aiff", "alac", "aif", "aifc", "dsf", "dff", "ape", "wv", "tak")
  private val video = setOf("mp4", "mkv", "webm", "mov", "m4v", "3gp")
  data class Report(val added: Int, val repeated: Int, val failed: Int, val limited: Boolean = false)

  fun metadata(c: Context, uri: Uri, fallback: HistoryItem): HistoryItem {
    val reader = MediaMetadataRetriever()
    return try {
      if (uri.scheme == "file") reader.setDataSource(uri.path) else reader.setDataSource(c, uri)
      val cover = reader.embeddedPicture?.takeIf { it.size <= 8 * 1024 * 1024 }?.let { bytes ->
        val hash = MessageDigest.getInstance("SHA-256").digest(uri.toString().toByteArray()).joinToString("") { "%02x".format(it) }
        File(c.filesDir, "art/$hash.jpg").apply { parentFile?.mkdirs(); writeBytes(bytes) }.toURI().toString()
      }
      fallback.copy(title = reader.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)?.takeIf(String::isNotBlank) ?: fallback.title,
        artist = reader.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)?.takeIf(String::isNotBlank) ?: fallback.artist,
        album = reader.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM).orEmpty(),
        duration = reader.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()?.coerceAtLeast(0) ?: fallback.duration,
        art = cover ?: fallback.art,
        video = when {
          reader.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO) != null -> reader.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO) == "yes"
          reader.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO) == "yes" -> false
          else -> fallback.video
        })
    } catch (_: Exception) { fallback } finally { reader.release() }
  }

  /** DSF/DFF/AIFF: MediaMetadataRetriever can't read them; parse header + embedded ID3 ourselves. */
  fun nativeTags(c: Context, uri: Uri, fallback: HistoryItem): HistoryItem {
    val t = runCatching { Lossless.tags { c.contentResolver.openInputStream(uri) } }.getOrNull() ?: return fallback
    val cover = t.cover?.takeIf { it.size <= 8 * 1024 * 1024 }?.let { bytes ->
      val hash = MessageDigest.getInstance("SHA-256").digest(uri.toString().toByteArray()).joinToString("") { "%02x".format(it) }
      File(c.filesDir, "art/$hash.jpg").apply { parentFile?.mkdirs(); writeBytes(bytes) }.toURI().toString() }
    return fallback.copy(title = t.title.ifBlank { fallback.title }, artist = t.artist.ifBlank { fallback.artist }, album = t.album,
      duration = t.durationMs.takeIf { it > 0 } ?: fallback.duration, art = cover ?: fallback.art, video = false,
      meta = if (t.quality.isNotBlank()) "${t.quality} · Local" else fallback.meta)
  }

  suspend fun import(c: Context, uris: List<Uri>, limited: Boolean = false): Report {
    var added = 0; var repeated = 0; var failed = 0
    val known = HistoryStore.load(c).map { it.uri }.toMutableSet()
    val tracks = mutableListOf<HistoryItem>()
    for (uri in uris.distinct()) {
      currentCoroutineContext().ensureActive()
      if (uri.toString() in known) { repeated++; continue }
      try {
        var name = ""; var size = 0L
        c.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { cursor ->
          if (cursor.moveToFirst()) { name = cursor.getString(0).orEmpty(); size = cursor.getLong(1) }
        }
        val ext = name.substringAfterLast('.', "").lowercase()
        val mime = c.contentResolver.getType(uri).orEmpty()
        require(ext in audio || ext in video || mime.startsWith("audio/") || mime.startsWith("video/")) { "Tipo de arquivo não suportado" }
        // Verify access now; never register a URI that the picker did not grant.
        c.contentResolver.openFileDescriptor(uri, "r")?.use { } ?: error("Arquivo indisponível")
        val meta = "${ext.uppercase().ifBlank { mime.substringAfter('/') }} · Local · ${"%.1f".format(size / 1e6)} MB"
        val fallback = HistoryItem(name.substringBeforeLast('.').ifBlank { "Arquivo local" }, meta, uri.toString(), imported = true,
          video = ext in video || mime.startsWith("video/"))
        require(ext != "tak") { "TAK ainda não é suportado" }
        val item = when (ext) {
          in Lossless.transcode -> { val f = Lossless.toFlac(c, uri, name)
            metadata(c, Uri.fromFile(f), fallback.copy(uri = Uri.fromFile(f).toString(), meta = "${ext.uppercase()} → FLAC · Local · ${"%.1f".format(f.length() / 1e6)} MB")) }
          in Lossless.native -> nativeTags(c, uri, fallback)
          else -> metadata(c, uri, fallback) }
        tracks.add(item); known.add(uri.toString()); known.add(item.uri); added++
      } catch (_: Exception) { failed++ }
    }
    if (tracks.isNotEmpty()) HistoryStore.addAll(c, tracks)
    return Report(added, repeated, failed, limited)
  }

  suspend fun scan(c: Context, tree: Uri): Pair<List<Uri>, Boolean> {
    val root = DocumentFile.fromTreeUri(c, tree) ?: error("Pasta indisponível")
    val found = mutableListOf<Uri>(); var visited = 0; var limited = false
    val job = currentCoroutineContext()
    fun walk(dir: DocumentFile, depth: Int) {
      job.ensureActive()
      if (depth > 12 || visited >= 10_000 || found.size >= 5_000) { limited = true; return }
      dir.listFiles().forEach { doc ->
        job.ensureActive()
        if (visited++ >= 10_000 || found.size >= 5_000) { limited = true; return@forEach }
        if (doc.isDirectory) walk(doc, depth + 1)
        else if (doc.name?.substringAfterLast('.', "")?.lowercase() in audio + video || doc.type.orEmpty().let { it.startsWith("audio/") || it.startsWith("video/") }) found.add(doc.uri)
      }
    }
    currentCoroutineContext().ensureActive(); walk(root, 0); currentCoroutineContext().ensureActive()
    return found to limited
  }
}
