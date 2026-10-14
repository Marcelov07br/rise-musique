package com.rise.musique.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Long-press menu shared by every track list: cover header + the same actions everywhere. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable fun TrackActionSheet(h: HistoryItem, s: UiState, a: Actions, close: () -> Unit, pickPlaylist: () -> Unit) {
  ModalBottomSheet(close, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
    TrackActionContent(h, s, a, close, pickPlaylist)
  }
}

@Composable fun TrackActionContent(h: HistoryItem, s: UiState, a: Actions, close: () -> Unit, pickPlaylist: () -> Unit) {
  val c = MaterialTheme.colorScheme
  val haptics = rememberHaptics()
  val inList = s.historyView.takeIf { it in s.playlists }
  fun go(f: () -> Unit) = { haptics.tick(); close(); f() }
  Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(bottom = 8.dp)) {
    Row(Modifier.padding(horizontal = 24.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
      Cover(h, Modifier.size(56.dp), 8.dp); Spacer(Modifier.width(16.dp))
      Column(Modifier.weight(1f)) {
        Text(h.title, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(listOf(h.artist, h.album).filter(String::isNotBlank).joinToString(" · ").ifBlank { h.meta }, color = c.onSurfaceVariant, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
      }
    }
    HorizontalDivider(Modifier.padding(vertical = 8.dp), color = c.outlineVariant)
    Action(Icons.Rounded.QueuePlayNext, "Tocar em seguida", go { a.playNext(h) })
    Action(Icons.Rounded.AddToQueue, "Adicionar à fila", go { a.addQueue(h) })
    Action(Icons.Rounded.PlaylistAdd, "Adicionar à playlist", go(pickPlaylist))
    if (inList != null) Action(Icons.Rounded.PlaylistRemove, "Tirar de $inList", go { a.removeFromList(h, inList) })
    Action(if (h.uri in s.favorites) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, if (h.uri in s.favorites) "Remover dos favoritos" else "Favoritar", go { a.favorite(h) })
    if (!h.video) Action(Icons.Rounded.Radio, "Rádio desta música", go { a.radioFor(h) })
    if (h.artist.isNotBlank()) Action(Icons.Rounded.Person, "Ir para artista", go { a.openArtist(h.artist) })
    if (h.album.isNotBlank()) Action(Icons.Rounded.Album, "Ir para álbum", go { a.openAlbum(h.album) })
    Action(Icons.Rounded.Share, "Compartilhar", go { a.share(h) })
    Action(Icons.Rounded.DeleteOutline, if (h.imported) "Remover da biblioteca" else "Apagar arquivo", go { if (h.imported) a.removeLocal(h) else a.delete(h) }, danger = true)
  }
}

@Composable internal fun Action(icon: ImageVector, label: String, onClick: () -> Unit, danger: Boolean = false) {
  val color = if (danger) DangerRed else MaterialTheme.colorScheme.onSurface
  Row(Modifier.fillMaxWidth().heightIn(min = 52.dp).clickable(onClick = onClick).padding(horizontal = 24.dp), verticalAlignment = Alignment.CenterVertically) {
    Icon(icon, null, tint = color); Spacer(Modifier.width(20.dp)); Text(label, color = color, fontSize = 16.sp)
  }
}

/** The only accent in the neutral theme: destructive actions. */
val DangerRed = androidx.compose.ui.graphics.Color(0xFFE5484D)

/** Long-press menu for online tracks (search, artist, album, station, Spotify): same look and queue actions as local ones. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable fun StreamActionSheet(title: String, subtitle: String, art: String, close: () -> Unit, playNext: () -> Unit, addQueue: () -> Unit,
                                  radio: (() -> Unit)? = null, download: (() -> Unit)? = null, like: (() -> Unit)? = null, liked: Boolean = false,
                                  addTo: (() -> Unit)? = null) {
  ModalBottomSheet(close, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
    StreamActionContent(title, subtitle, art, close, playNext, addQueue, radio, download, like, liked, addTo)
  }
}

@Composable fun StreamActionContent(title: String, subtitle: String, art: String, close: () -> Unit, playNext: () -> Unit, addQueue: () -> Unit,
                                    radio: (() -> Unit)? = null, download: (() -> Unit)? = null, like: (() -> Unit)? = null, liked: Boolean = false,
                                    addTo: (() -> Unit)? = null) {
  val haptics = rememberHaptics()
  fun go(f: () -> Unit) = { haptics.tick(); close(); f() }
  Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = 8.dp)) {
    Row(Modifier.padding(horizontal = 24.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
      Cover(HistoryItem(title, "", art = art), Modifier.size(56.dp), 8.dp); Spacer(Modifier.width(16.dp))
      Column(Modifier.weight(1f)) {
        Text(title, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        if (subtitle.isNotBlank()) Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
      }
    }
    HorizontalDivider(Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant)
    Action(Icons.Rounded.QueuePlayNext, "Tocar em seguida", go(playNext))
    Action(Icons.Rounded.AddToQueue, "Adicionar à fila", go(addQueue))
    like?.let { Action(if (liked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, if (liked) "Remover das curtidas" else "Curtir", go(it)) }
    addTo?.let { Action(Icons.Rounded.PlaylistAdd, "Adicionar à playlist…", go(it)) }
    radio?.let { Action(Icons.Rounded.Radio, "Rádio desta música", go(it)) }
    download?.let { Action(Icons.Rounded.Download, "Baixar…", go(it)) }
  }
}

/** Online track menu; with [s] it also offers Curtir and Adicionar à playlist (streams live in the library by video id). */
@Composable fun StreamActionSheet(r: SearchResult, a: Actions, s: UiState? = null, close: () -> Unit) {
  var pick by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
  val key = com.rise.musique.Engine.videoId(r.url)?.let { "rise-stream://$it" }
  StreamActionSheet(r.title, r.channel, r.thumb, close, { a.playNextStream(r) }, { a.addQueueStream(r) }, { a.radio(r) }, { a.downloadItem(r) },
    like = if (s != null && key != null) ({ a.likeStream(r) }) else null, liked = key != null && s?.favorites?.contains(key) == true,
    addTo = if (s != null && key != null) ({ pick = true }) else null)
  if (pick && s != null) PlaylistPicker(s.playlists.keys.toList(), { pick = false }) { name -> a.addStreamToPlaylist(r, name); pick = false }
}
