package com.rise.musique.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.rise.musique.Station
import com.rise.musique.StationItem
import com.rise.musique.Stations

/** Station detail screen: collapses header, ambient tinted background, lists mixed radio & local tracks. */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable fun StationScreen(
  s: UiState,
  a: Actions,
  r: Route.Station,
  list: LazyListState,
  modifier: Modifier = Modifier,
  onBack: () -> Unit = {},
  dismiss: Animatable<Float, AnimationVector1D> = remember { Animatable(0f) }
) {
  val c = MaterialTheme.colorScheme
  val page = s.stationPages[r.id]
  val loading = r.id in s.stationLoadingSet
  val st = remember(r.id, s.history, s.stats, s.trackInfo) {
    page?.station ?: s.forYou.firstOrNull { it.id == r.id } ?: Stations.build(s.history.filter { it.uri !in s.hidden }, s.stats, s.trackInfo).firstOrNull { it.id == r.id }
      ?: Station(r.id, r.name, r.subtitle, r.kind, emptyList(), r.hue)
  }
  LaunchedEffect(r.id) {
    if (page == null) a.loadStation(st)
  }
  val tracks = page?.items.orEmpty()
  val haptics = rememberHaptics()
  val back = { haptics.tick(); onBack() }
  val collapseState = rememberUpdatedState(collapseOf(list))
  val collapse = collapseState.value
  val totalS = tracks.sumOf { it.duration }
  val meta = listOf("ESTAÇÃO", if (tracks.isNotEmpty()) "${tracks.size} FAIXAS" else "",
    if (totalS > 0) "${(totalS / 60).coerceAtLeast(1)} MIN" else "").filter(String::isNotBlank).joinToString(" · ")

  val bg = MaterialTheme.colorScheme.background
  val hslColor = Color(androidx.core.graphics.ColorUtils.HSLToColor(floatArrayOf(r.hue, .5f, .32f)))

  Box(modifier.dragToDismiss(dismiss, onBack)) {
    // Ambient background with subtle gradient matching station hue
    Box(Modifier.fillMaxSize().background(
      Brush.verticalGradient(
        0f to hslColor.copy(alpha = .42f),
        .35f to hslColor.copy(alpha = .18f),
        .7f to bg.copy(alpha = .85f),
        1f to bg
      )
    ))
    Box(Modifier.fillMaxSize().background(bg.copy(alpha = (collapse * .9f).coerceIn(0f, 1f))))

    LazyColumn(Modifier.fillMaxSize(), state = list, contentPadding = PaddingValues(top = TopBarHeight, bottom = bottomSpace(s))) {
      item(key = "header") {
        Column(Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, bottom = 16.dp).graphicsLayer { alpha = (1f - collapse * 1.2f).coerceIn(0f, 1f) },
          horizontalAlignment = Alignment.CenterHorizontally) {
          val corner = morphCorner(18.dp, 8.dp)?.value ?: 18.dp
          Box(Modifier.size(208.dp).graphicsLayer { val sc = 1f - .15f * collapseState.value.coerceIn(0f, 1f); scaleX = sc; scaleY = sc }
            .sharedCover("cover-st-${if (r.src.isNotBlank()) "${r.src}-" else ""}${r.id}").clip(RoundedCornerShape(corner))) {
            StationArt(st, Modifier.fillMaxSize())
          }
          Spacer(Modifier.height(16.dp))
          Column(Modifier.enterRise(), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(r.name, color = Color.White, fontSize = 26.sp, lineHeight = 30.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(4.dp))
            Text(r.subtitle, color = Color.White.copy(.8f), fontSize = 14.sp, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(4.dp))
            Text(meta, color = Color.White.copy(.7f), fontSize = 12.sp, letterSpacing = 1.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
              GlassIcon(Icons.Rounded.Shuffle, "Aleatório", { haptics.press(); a.playStationTracks(tracks, 0, true) }, enabled = tracks.isNotEmpty())
              GlassBox(Modifier.height(48.dp).width(144.dp), CircleShape) {
                Row(Modifier.matchParentSize().clickable(enabled = tracks.isNotEmpty()) { haptics.press(); a.playStationTracks(tracks, 0, false) },
                  verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                  Icon(Icons.Rounded.PlayArrow, null, tint = Color.White); Spacer(Modifier.width(8.dp))
                  Text("Tocar", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 16.sp) } }
              GlassIcon(Icons.Rounded.PlaylistAdd, "Adicionar tudo à fila", { haptics.tick(); a.queueStationTracks(tracks) }, enabled = tracks.isNotEmpty())
            }
          }
        }
      }

      if (loading && tracks.isEmpty()) item(key = "loading") {
        Row(Modifier.fillMaxWidth().padding(32.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
          CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp, color = c.onSurface)
          Spacer(Modifier.width(12.dp))
          Text("Montando as faixas da estação…", color = c.onSurfaceVariant, fontSize = 14.sp)
        }
      } else if (tracks.isEmpty()) item(key = "empty") {
        Column(Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
          Text("Nenhuma faixa disponível", style = MaterialTheme.typography.titleMedium)
          Spacer(Modifier.height(6.dp))
          Text("Não foi possível carregar as músicas da estação agora. Confira a conexão.", color = c.onSurfaceVariant, fontSize = 13.sp, textAlign = TextAlign.Center)
          Spacer(Modifier.height(12.dp))
          Button({ a.loadStation(st) }, shape = CircleShape) { Text("Tentar novamente") }
        }
      }

      itemsIndexed(tracks, key = { _, t -> t.key }) { i, t ->
        val playing = s.now?.key == t.key
        val src = remember { MutableInteractionSource() }
        var menu by remember { mutableStateOf(false) }
        var pick by remember { mutableStateOf(false) }
        if (menu) t.historyItem?.let { h -> TrackActionSheet(h, s, a, { menu = false }) { pick = true } }
          ?: StreamActionSheet(t.title, t.subtitle, t.thumb, { menu = false }, { a.queueStationItem(t, true) }, { a.queueStationItem(t, false) },
            t.searchResult?.let { r -> { a.radio(r) } }, t.searchResult?.let { r -> { a.downloadItem(r) } })
        if (pick) t.historyItem?.let { h -> PlaylistPicker(s.playlists.keys.sorted(), { pick = false }) { name -> a.addToPlaylist(h, name); pick = false } }
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp).weightedPress(src, 14.dp).clip(RoundedCornerShape(14.dp))
            .combinedClickable(src, null, onLongClick = { haptics.press(); menu = true }) { haptics.tick(); a.playStationTracks(tracks, i, false) }.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
          if (playing) Icon(Icons.Rounded.GraphicEq, null, Modifier.width(36.dp), tint = accentColor())
          else Text("${i + 1}", Modifier.width(36.dp), color = c.onSurfaceVariant, fontSize = 15.sp, textAlign = TextAlign.Center)

          if (t.thumb.isNotBlank()) {
            Box(Modifier.size(44.dp).clip(RoundedCornerShape(8.dp)).background(Color.White.copy(.1f))) {
              AsyncImage(t.thumb, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            }
            Spacer(Modifier.width(12.dp))
          }

          Column(Modifier.weight(1f)) {
            Text(t.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, color = if (playing) accentColor() else c.onSurface)
            Text(t.subtitle, color = c.onSurfaceVariant, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
          }

          if (t.isLocal) {
            Surface(shape = RoundedCornerShape(6.dp), color = c.surfaceVariant.copy(alpha = .6f)) {
              Text("Local", Modifier.padding(horizontal = 6.dp, vertical = 2.dp), fontSize = 11.sp, color = c.onSurfaceVariant)
            }
            Spacer(Modifier.width(8.dp))
          }

          if (t.duration > 0) Text("%d:%02d".format(t.duration / 60, t.duration % 60), color = c.onSurfaceVariant, fontSize = 13.sp)

          if (!t.isLocal && t.searchResult != null) {
            IconButton({ a.downloadItem(t.searchResult) }) { Icon(Icons.Rounded.Download, "Baixar ${t.title}", tint = c.onSurfaceVariant) }
          }
        }
        if (i < tracks.lastIndex) HorizontalDivider(Modifier.padding(start = 56.dp, end = 16.dp), color = c.outlineVariant)
      }
    }
    DetailTopBar(r.name, collapse, back, if (tracks.isEmpty()) null else ({ haptics.press(); a.playStationTracks(tracks, 0, false) }))
  }
}
