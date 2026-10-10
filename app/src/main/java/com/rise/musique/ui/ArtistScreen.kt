package com.rise.musique.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
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

/** Local tracks credited to [name] (any list view / filter ignored). */
fun artistTracks(s: UiState, name: String) =
  s.history.filter { it.uri !in s.hidden && it.artist.equals(name, ignoreCase = true) }

/** Hero image: the largest-looking cover among their tracks, else the first YouTube result's thumbnail. */
fun artistArt(s: UiState, name: String): String =
  artistTracks(s, name).firstOrNull { it.art.isNotBlank() }?.art ?: s.popular[name]?.firstOrNull { it.thumb.isNotBlank() }?.thumb.orEmpty()

@Composable fun ArtistScreen(s: UiState, a: Actions, name: String, list: LazyListState, modifier: Modifier = Modifier, onBack: () -> Unit = {},
                             dismiss: androidx.compose.animation.core.Animatable<Float, androidx.compose.animation.core.AnimationVector1D> = remember { androidx.compose.animation.core.Animatable(0f) }) {
  val c = MaterialTheme.colorScheme
  val tracks = artistTracks(s, name)
  val albums = tracks.filter { !it.video && it.album.isNotBlank() }.groupBy { it.album }.entries.toList()
  val popular = s.popular[name]
  val loading = name in s.popularLoading || (popular == null && !androidx.compose.ui.platform.LocalInspectionMode.current)
  LaunchedEffect(name) { a.artistPopular(name) }
  val art = artistArt(s, name)
  val haptics = rememberHaptics()
  val back = { haptics.tick(); onBack() }
  val collapse = collapseOf(list)
  val albumRow = remember(name) { LazyListState() }
  Box(modifier.dragToDismiss(dismiss, onBack)) {
    LazyColumn(Modifier.fillMaxSize(), state = list, contentPadding = PaddingValues(top = TopBarHeight, bottom = if (s.now != null) 104.dp else 24.dp)) {
      item(key = "header") {
        Column(Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, bottom = 24.dp)
          .graphicsLayer { alpha = (1f - collapse * 1.2f).coerceIn(0f, 1f) }, horizontalAlignment = Alignment.CenterHorizontally) {
          Box(Modifier.size(208.dp).graphicsLayer { val sc = 1f - .15f * collapse; scaleX = sc; scaleY = sc }.sharedCover("cover-ar-$name")
            .clip(CircleShape).background(Color.White.copy(.12f)), contentAlignment = Alignment.Center) {
            if (art.isNotBlank()) AsyncImage(art, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            else Icon(Icons.Rounded.Person, null, tint = Color.White.copy(.75f), modifier = Modifier.size(72.dp))
          }
          Spacer(Modifier.height(16.dp))
          Column(Modifier.enterRise(), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(name, color = Color.White, fontSize = 28.sp, lineHeight = 32.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(4.dp))
            Text(listOf("ARTISTA", if (tracks.isNotEmpty()) "${tracks.size} ${if (tracks.size == 1) "FAIXA" else "FAIXAS"} NA BIBLIOTECA" else "").filter(String::isNotBlank).joinToString(" · "),
              color = Color.White.copy(.7f), fontSize = 12.sp, letterSpacing = 1.sp, maxLines = 1)
            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
              GlassIcon(Icons.Rounded.Shuffle, "Aleatório", { haptics.press(); tracks.randomOrNull()?.let(a.playShuffled) }, enabled = tracks.isNotEmpty())
              GlassBox(Modifier.height(48.dp).width(144.dp), CircleShape) {
                Row(Modifier.matchParentSize().clickable(enabled = tracks.isNotEmpty()) { haptics.press(); tracks.firstOrNull()?.let(a.play) },
                  verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                  Icon(Icons.Rounded.PlayArrow, null, tint = Color.White); Spacer(Modifier.width(8.dp))
                  Text("Tocar", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 16.sp) }
              }
              GlassIcon(Icons.Rounded.Radio, "Rádio do artista", { haptics.tick(); tracks.firstOrNull { !it.video }?.let(a.radioFor) }, enabled = tracks.any { !it.video })
            }
            Spacer(Modifier.height(12.dp))
            val following = name in s.followed
            GlassBox(Modifier.height(48.dp), CircleShape) {
              Row(Modifier.clickable { haptics.press(); a.follow(name) }.padding(horizontal = 20.dp).fillMaxHeight(), verticalAlignment = Alignment.CenterVertically) {
                Icon(if (following) Icons.Rounded.NotificationsActive else Icons.Rounded.NotificationAdd, null, tint = Color.White, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(if (following) "Seguindo · avisa lançamentos" else "Seguir artista", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp) }
            }
          }
        }
      }
      if (tracks.isNotEmpty()) {
        item(key = "local-title") { Section("Na sua biblioteca") }
        itemsIndexed(tracks.take(10), key = { _, h -> "t:" + h.uri }) { i, h -> Box(Modifier.padding(horizontal = 16.dp).enterRise()) { TrackRow(h, s, a) } }
      }
      if (albums.isNotEmpty()) {
        item(key = "albums-title") { Section("Álbuns") }
        item(key = "albums") { Box(Modifier.padding(horizontal = 24.dp)) { Carousel(albumRow) {
          items(albums, key = { it.key }) { e -> val h = e.value.first()
            CoverCard(h, e.key, "${e.value.size} ${if (e.value.size == 1) "faixa" else "faixas"}", "cover-al-${e.key}") { a.openAlbum(e.key) } } } } }
      }
      item(key = "popular-title") { Section("Populares no YouTube") }
      when {
        loading && popular.isNullOrEmpty() -> item(key = "popular-loading") { Row(Modifier.padding(24.dp), verticalAlignment = Alignment.CenterVertically) {
          CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = c.onSurface); Spacer(Modifier.width(12.dp))
          Text("Buscando as mais tocadas…", color = c.onSurfaceVariant, fontSize = 14.sp) } }
        popular.isNullOrEmpty() -> item(key = "popular-empty") {
          EmptyState(Icons.Rounded.CloudOff, "Nada do YouTube agora", "Confira a conexão e abra o artista de novo.") }
        else -> itemsIndexed(popular, key = { _, r -> "p:" + r.url }) { i, r ->
          PopularRow(i + 1, r, s.previewing == r.url, s.previewing == r.url && s.previewLoading, { a.preview(r) }, { a.prepareDownload(r) }) }
      }
    }
    DetailTopBar(name, collapse, back, if (tracks.isEmpty()) null else ({ haptics.press(); a.play(tracks.first()) }))
  }
}

@Composable private fun Section(t: String) =
  Text(t, Modifier.padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 8.dp), style = MaterialTheme.typography.titleLarge)

@Composable private fun PopularRow(n: Int, r: SearchResult, playing: Boolean, loading: Boolean, preview: () -> Unit, download: () -> Unit) {
  val c = MaterialTheme.colorScheme
  val src = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
  Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp).weightedPress(src, 16.dp).clip(RoundedCornerShape(16.dp)).clickable(src, null, onClick = preview)
      .padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
    Text("$n", Modifier.width(36.dp), color = c.onSurfaceVariant, fontSize = 15.sp, textAlign = TextAlign.Center)
    AsyncImage(r.thumb, null, Modifier.size(56.dp).clip(RoundedCornerShape(8.dp)).background(c.surfaceVariant), contentScale = ContentScale.Crop)
    Spacer(Modifier.width(12.dp))
    Column(Modifier.weight(1f)) {
      Text(r.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
      Text(listOf(r.channel, if (r.duration > 0) "%d:%02d".format(r.duration / 60, r.duration % 60) else "").filter(String::isNotBlank).joinToString(" · "),
        color = c.onSurfaceVariant, fontSize = 13.sp, maxLines = 1)
    }
    IconButton(preview) { if (loading) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = c.onSurface)
      else Icon(if (playing) Icons.Rounded.Stop else Icons.Rounded.PlayArrow, if (playing) "Parar prévia" else "Ouvir prévia") }
    IconButton(download) { Icon(Icons.Rounded.Download, "Baixar") }
  }
}

/** Guided empty state used by every list. */
@Composable fun EmptyState(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, body: String, action: String? = null, onAction: () -> Unit = {}) {
  val c = MaterialTheme.colorScheme
  Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
    Icon(icon, null, Modifier.size(40.dp), tint = c.onSurfaceVariant)
    Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
    Text(body, color = c.onSurfaceVariant, fontSize = 14.sp, textAlign = TextAlign.Center)
    if (action != null) PillButton(action, onAction, filled = false)
  }
}
