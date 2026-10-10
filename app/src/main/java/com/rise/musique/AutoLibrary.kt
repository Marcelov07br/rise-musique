package com.rise.musique

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.rise.musique.ui.HistoryItem
import java.io.File

/** Browse tree for Android Auto / Bluetooth / other MediaBrowser clients. Local files only. */
object AutoLibrary {
  const val ROOT = "root"; const val FAV = "fav"; const val PL = "pl"; const val LIB = "lib"; const val RECENT = "recent"
  const val PL_PREFIX = "pl:"
  private const val GEARHEAD = "com.google.android.projection.gearhead"

  sealed interface Node { val id: String; val title: String }
  data class Folder(override val id: String, override val title: String) : Node
  data class Track(val item: HistoryItem) : Node { override val id get() = item.uri; override val title get() = item.title }

  fun isLocal(h: HistoryItem) = h.uri.isNotBlank() && !h.uri.startsWith("http") && !h.uri.startsWith("preview:") && !h.video

  /** Pure tree: which nodes live under [parent]. */
  fun children(parent: String, history: List<HistoryItem>, favorites: Set<String>, playlists: Map<String, List<String>>): List<Node> {
    val local = history.filter(::isLocal)
    val byUri = local.associateBy { it.uri }
    return when {
      parent == ROOT -> listOf(Folder(FAV, "Favoritos"), Folder(PL, "Playlists"), Folder(LIB, "Biblioteca"), Folder(RECENT, "Recentes"))
      parent == FAV -> local.filter { it.uri in favorites }.map(::Track)
      parent == PL -> playlists.keys.map { Folder(PL_PREFIX + it, it) }
      parent.startsWith(PL_PREFIX) -> playlists[parent.removePrefix(PL_PREFIX)].orEmpty().mapNotNull(byUri::get).map(::Track)
      parent == LIB -> local.sortedBy { it.title.lowercase() }.map(::Track)
      parent == RECENT -> local.take(30).map(::Track)
      else -> emptyList()
    }
  }

  fun search(local: List<HistoryItem>, query: String): List<HistoryItem> {
    val q = query.trim().lowercase(); if (q.isEmpty()) return local
    return local.filter { listOf(it.title, it.artist, it.album).any { f -> f.lowercase().contains(q) } }
  }

  /** Content URI that the player can open for a stored track (content:// as-is, files via FileProvider). */
  fun playUri(c: Context, uri: String): Uri? = runCatching { when {
    uri.startsWith("content://") -> Uri.parse(uri)
    uri.isNotBlank() && File(uri).exists() -> FileProvider.getUriForFile(c, "${c.packageName}.files", File(uri))
    else -> null } }.getOrNull()

  /** Artwork URI readable by the car / system UI: app files go through FileProvider and are granted to Android Auto. */
  fun artUri(c: Context, art: String): Uri? = runCatching {
    if (art.isBlank()) return null
    val u = Uri.parse(art)
    val f = when { u.scheme == "file" -> File(u.path!!); art.startsWith("/") -> File(art); else -> null }
    if (f == null) return u
    if (!f.exists()) return null
    FileProvider.getUriForFile(c, "${c.packageName}.files", f).also {
      runCatching { c.grantUriPermission(GEARHEAD, it, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) } }
  }.getOrNull()

  fun folderItem(f: Folder): MediaItem = MediaItem.Builder().setMediaId(f.id).setMediaMetadata(MediaMetadata.Builder()
    .setTitle(f.title).setIsBrowsable(true).setIsPlayable(false)
    .setMediaType(if (f.id.startsWith(PL_PREFIX)) MediaMetadata.MEDIA_TYPE_PLAYLIST else MediaMetadata.MEDIA_TYPE_FOLDER_MIXED).build()).build()

  fun trackItem(c: Context, h: HistoryItem, withUri: Boolean): MediaItem = MediaItem.Builder().setMediaId(h.uri)
    .apply { if (withUri) playUri(c, h.uri)?.let(::setUri) }
    .setMediaMetadata(MediaMetadata.Builder().setTitle(h.title).setArtist(h.artist.ifBlank { null })
      .setAlbumTitle(h.album.ifBlank { null }).setArtworkUri(artUri(c, h.art))
      .setIsBrowsable(false).setIsPlayable(true).setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC).build()).build()
}
