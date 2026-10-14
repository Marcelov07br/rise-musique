package com.rise.musique.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage

/** Streamed album (YouTube Music): same header/motion as local albums; tracklist streams, "Baixar tudo" goes to Downloads. */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable fun RemoteAlbumScreen(s: UiState, a: Actions, r: Route.RemoteAlbum, list: LazyListState, modifier: Modifier = Modifier, onBack: () -> Unit = {},
                                  dismiss: androidx.compose.animation.core.Animatable<Float, androidx.compose.animation.core.AnimationVector1D> = remember { androidx.compose.animation.core.Animatable(0f) }) {
  val c = MaterialTheme.colorScheme
  val page = s.albumPages[r.id]
  LaunchedEffect(r.id) { a.albumPage(r.id) }
  val tracks = page?.tracks.orEmpty()
  val art = page?.art?.ifBlank { null } ?: r.art
  val artist = page?.artist?.ifBlank { null } ?: r.artist
  val haptics = rememberHaptics()
  val back = { haptics.tick(); onBack() }
  val collapseState = rememberUpdatedState(collapseOf(list)); val collapse = collapseState.value
  val totalS = tracks.sumOf { it.duration }
  val meta = listOf("ÁLBUM", artist.uppercase(), page?.year.orEmpty(), if (tracks.isNotEmpty()) "${tracks.size} FAIXAS" else "",
    if (totalS > 0) "${(totalS / 60).coerceAtLeast(1)} MIN" else "").filter(String::isNotBlank).joinToString(" · ")
  Box(modifier.dragToDismiss(dismiss, onBack)) {
    LazyColumn(Modifier.fillMaxSize(), state = list, contentPadding = PaddingValues(top = TopBarHeight, bottom = bottomSpace(s))) {
      item(key = "header") {
        Column(Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, bottom = 16.dp).graphicsLayer { alpha = (1f - collapse * 1.2f).coerceIn(0f, 1f) },
          horizontalAlignment = Alignment.CenterHorizontally) {
          val corner = morphCorner(14.dp, 8.dp)?.value ?: 8.dp
          Box(Modifier.size(208.dp).graphicsLayer { val sc = 1f - .15f * collapseState.value.coerceIn(0f, 1f); scaleX = sc; scaleY = sc }
            .sharedCover("cover-ra-${if (r.src.isNotBlank()) "${r.src}-" else ""}${r.id}").clip(RoundedCornerShape(corner)).background(Color.White.copy(.12f))) {
            if (art.isNotBlank()) AsyncImage(art, null, Modifier.fillMaxSize().kenBurns(true), contentScale = ContentScale.Crop) }
          Spacer(Modifier.height(16.dp))
          Column(Modifier.enterRise(), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(page?.title?.ifBlank { null } ?: r.title, color = Color.White, fontSize = 26.sp, lineHeight = 30.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(4.dp))
            Text(meta, color = Color.White.copy(.7f), fontSize = 12.sp, letterSpacing = 1.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
              GlassIcon(Icons.Rounded.Shuffle, "Aleatório", { haptics.press(); a.playStreams(tracks, 0, true) }, enabled = tracks.isNotEmpty())
              GlassBox(Modifier.height(48.dp).width(144.dp), CircleShape) {
                Row(Modifier.matchParentSize().clickable(enabled = tracks.isNotEmpty()) { haptics.press(); a.playStreams(tracks, 0, false) },
                  verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                  Icon(Icons.Rounded.PlayArrow, null, tint = Color.White); Spacer(Modifier.width(8.dp))
                  Text("Tocar", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 16.sp) } }
              GlassIcon(Icons.Rounded.Download, "Baixar tudo", { haptics.press(); a.downloadAll(tracks) }, enabled = tracks.isNotEmpty())
              val saved = s.library.albums.any { it.id == r.id }
              GlassIcon(if (saved) Icons.Rounded.LibraryAddCheck else Icons.Rounded.LibraryAdd, if (saved) "Remover dos álbuns salvos" else "Salvar álbum", { haptics.tick()
                a.toggleAlbum(com.rise.musique.LibAlbum(r.id, page?.title?.ifBlank { null } ?: r.title, artist, art, page?.year.orEmpty(), com.rise.musique.LibSource.YOUTUBE, mapOf("ytmAlbum" to r.id))) })
            }
          }
        }
      }
      if (page == null) item(key = "loading") { Row(Modifier.fillMaxWidth().padding(24.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        if (r.id in s.albumLoading || androidx.compose.ui.platform.LocalInspectionMode.current.not()) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = c.onSurface)
        Spacer(Modifier.width(12.dp)); Text("Carregando as faixas…", color = c.onSurfaceVariant, fontSize = 14.sp) } }
      itemsIndexed(tracks, key = { _, t -> "t:" + t.url }) { i, t ->
        val playing = s.now?.key == "stream:" + t.url
        val src = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
        var menu by remember { mutableStateOf(false) }
        if (menu) StreamActionSheet(t.copy(thumb = t.thumb.ifBlank { r.art }), a, s) { menu = false }
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp).weightedPress(src, 14.dp).clip(RoundedCornerShape(14.dp))
            .combinedClickable(src, null, onLongClick = { haptics.press(); menu = true }) { haptics.tick(); a.playStreams(tracks, i, false) }.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
          if (playing) Icon(Icons.Rounded.GraphicEq, null, Modifier.width(36.dp), tint = accentColor())
          else Text("${i + 1}", Modifier.width(36.dp), color = c.onSurfaceVariant, fontSize = 15.sp, textAlign = TextAlign.Center)
          Column(Modifier.weight(1f)) {
            Text(t.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, color = if (playing) accentColor() else c.onSurface)
            if (t.channel.isNotBlank() && t.channel != artist) Text(t.channel, color = c.onSurfaceVariant, fontSize = 13.sp, maxLines = 1) }
          if (t.duration > 0) Text("%d:%02d".format(t.duration / 60, t.duration % 60), color = c.onSurfaceVariant, fontSize = 13.sp)
          IconButton({ a.downloadItem(t) }) { Icon(Icons.Rounded.Download, "Baixar ${t.title}", tint = c.onSurfaceVariant) }
        }
      }
    }
    DetailTopBar(page?.title?.ifBlank { null } ?: r.title, collapse, back, if (tracks.isEmpty()) null else ({ haptics.press(); a.playStreams(tracks, 0, false) }))
  }
}
