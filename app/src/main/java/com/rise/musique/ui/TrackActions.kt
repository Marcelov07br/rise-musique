package com.rise.musique.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
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
    Action(Icons.Rounded.QueuePlayNext, "Tocar a seguir", go { a.playNext(h) })
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

@Composable private fun Action(icon: ImageVector, label: String, onClick: () -> Unit, danger: Boolean = false) {
  val color = if (danger) DangerRed else MaterialTheme.colorScheme.onSurface
  Row(Modifier.fillMaxWidth().heightIn(min = 52.dp).clickable(onClick = onClick).padding(horizontal = 24.dp), verticalAlignment = Alignment.CenterVertically) {
    Icon(icon, null, tint = color); Spacer(Modifier.width(20.dp)); Text(label, color = color, fontSize = 16.sp)
  }
}

/** The only accent in the neutral theme: destructive actions. */
val DangerRed = androidx.compose.ui.graphics.Color(0xFFE5484D)
