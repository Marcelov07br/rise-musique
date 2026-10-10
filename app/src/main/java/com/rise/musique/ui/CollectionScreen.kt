package com.rise.musique.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val HeaderHeight = 420.dp
private val TopBarHeight = 64.dp

/** 0 = header fully visible, 1 = scrolled past the header (collapsed top bar). Always clamped. */
@Composable fun collapseOf(list: LazyListState): Float {
  val px = with(LocalDensity.current) { (HeaderHeight - TopBarHeight).toPx() }
  return if (list.firstVisibleItemIndex > 0) 1f else (list.firstVisibleItemScrollOffset / px).coerceIn(0f, 1f)
}

/** Fixed full-screen backdrop: living artwork fading into the base color downwards, and fully as the list scrolls. */
@Composable fun CollectionBackdrop(art: String, collapse: Float, modifier: Modifier) {
  val bg = MaterialTheme.colorScheme.background
  Box(modifier) {
    LivingArtwork(art, Modifier.fillMaxSize(), dim = .25f)
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to Color.Transparent, .30f to Color.Transparent,
      .55f to bg.copy(alpha = .85f), .75f to bg)))
    Box(Modifier.fillMaxSize().background(bg.copy(alpha = (collapse * .9f).coerceIn(0f, 1f))))
  }
}

/** Album or playlist: collapsing hero header, glass actions and a numbered list, over [CollectionBackdrop]. */
@Composable fun CollectionScreen(s: UiState, a: Actions, list: LazyListState, modifier: Modifier = Modifier) {
  val c = MaterialTheme.colorScheme
  val tracks = visibleHistory(s)
  val isAlbum = s.albumFilter.isNotBlank()
  val title = if (isAlbum) s.albumFilter else s.historyView
  val hero = tracks.firstOrNull { it.art.isNotBlank() }
  val artist = if (isAlbum) tracks.map { it.artist }.filter(String::isNotBlank).distinct().singleOrNull().orEmpty() else ""
  val totalMs = tracks.sumOf { it.duration }
  val meta = listOf(if (isAlbum) "ÁLBUM" else "PLAYLIST", artist.uppercase(), "${tracks.size} ${if (tracks.size == 1) "FAIXA" else "FAIXAS"}",
    if (totalMs > 0) "${(totalMs / 60_000).coerceAtLeast(1)} MIN" else "").filter(String::isNotBlank).joinToString(" · ")
  val haptics = rememberHaptics()
  val back = { haptics.tick(); if (isAlbum) a.albumFilter("") else a.historyView("Tudo") }
  androidx.activity.compose.BackHandler(onBack = back)
  val collapse = collapseOf(list)
  Box(modifier) {
    LazyColumn(Modifier.fillMaxSize(), state = list, contentPadding = PaddingValues(top = TopBarHeight, bottom = if (s.now != null) 104.dp else 24.dp)) {
      item(key = "header") {
        Column(Modifier.fillMaxWidth().height(HeaderHeight - TopBarHeight).padding(horizontal = 24.dp)
          .graphicsLayer { alpha = (1f - collapse * 1.2f).coerceIn(0f, 1f) }, horizontalAlignment = Alignment.CenterHorizontally) {
          Box(Modifier.size(208.dp).graphicsLayer { val sc = 1f - .15f * collapse.coerceIn(0f, 1f); scaleX = sc; scaleY = sc }
            .shadow(24.dp, RoundedCornerShape(16.dp))) {
            if (hero != null) Cover(hero, Modifier.fillMaxSize())
            else Box(Modifier.fillMaxSize().clip(RoundedCornerShape(16.dp)).background(Color.White.copy(.12f)), contentAlignment = Alignment.Center) {
              Icon(if (isAlbum) Icons.Rounded.Album else Icons.Rounded.QueueMusic, null, tint = Color.White.copy(.75f), modifier = Modifier.size(64.dp)) }
          }
          Spacer(Modifier.height(16.dp))
          Text(title, color = Color.White, fontSize = 24.sp, lineHeight = 28.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
          Spacer(Modifier.height(4.dp))
          Text(meta, color = Color.White.copy(.7f), fontSize = 12.sp, letterSpacing = 1.sp, textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis)
          Spacer(Modifier.height(16.dp))
          Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            GlassIcon(Icons.Rounded.Shuffle, "Aleatório", { haptics.press(); tracks.randomOrNull()?.let(a.playShuffled) }, enabled = tracks.isNotEmpty())
            GlassBox(Modifier.height(48.dp).width(144.dp), CircleShape) {
              Row(Modifier.matchParentSize().clickable(enabled = tracks.isNotEmpty()) { haptics.press(); tracks.firstOrNull()?.let(a.play) },
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                Icon(Icons.Rounded.PlayArrow, null, tint = Color.White); Spacer(Modifier.width(8.dp))
                Text("Tocar", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 16.sp) }
            }
            GlassIcon(Icons.Rounded.PlaylistAdd, "Adicionar tudo à fila", { haptics.tick(); a.queueAll(tracks) }, enabled = tracks.isNotEmpty())
          }
        }
      }
      if (tracks.isEmpty()) item(key = "empty") {
        Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
          Text(if (isAlbum) "Nenhuma faixa deste álbum" else "Esta playlist está vazia", style = MaterialTheme.typography.titleMedium)
          Text("Toque e segure uma faixa na biblioteca para adicionar.", color = c.onSurfaceVariant, fontSize = 13.sp, textAlign = TextAlign.Center)
        }
      }
      itemsIndexed(tracks, key = { _, h -> h.uri }) { i, h ->
        Box(Modifier.padding(horizontal = 16.dp)) { TrackRow(h, s, a, number = i + 1) }
        if (i < tracks.lastIndex) HorizontalDivider(Modifier.padding(start = 64.dp, end = 16.dp), color = c.outlineVariant)
      }
    }
    // Barra superior: voltar sempre visível; título aparece quando o cabeçalho sai da tela.
    val barT = ((collapse - .6f) / .4f).coerceIn(0f, 1f)
    Row(Modifier.fillMaxWidth().height(TopBarHeight).background(c.background.copy(alpha = (barT * .92f).coerceIn(0f, 1f))).padding(horizontal = 8.dp),
      verticalAlignment = Alignment.CenterVertically) {
      GlassIcon(Icons.Rounded.ArrowBack, "Voltar", back, light = barT > .5f)
      Text(title, Modifier.weight(1f).padding(horizontal = 12.dp).graphicsLayer { alpha = barT }, style = MaterialTheme.typography.titleMedium,
        color = c.onBackground, maxLines = 1, overflow = TextOverflow.Ellipsis)
      if (barT > 0f) Box(Modifier.size(48.dp).graphicsLayer { alpha = barT }.clip(CircleShape).clickable(enabled = barT > .5f && tracks.isNotEmpty()) {
          haptics.press(); tracks.firstOrNull()?.let(a.play) }, contentAlignment = Alignment.Center) {
        Icon(Icons.Rounded.PlayCircle, "Tocar", tint = c.onBackground, modifier = Modifier.size(32.dp)) }
    }
  }
}

@Composable private fun GlassIcon(icon: ImageVector, desc: String, onClick: () -> Unit, enabled: Boolean = true, light: Boolean = false) {
  val tint = if (light) MaterialTheme.colorScheme.onBackground else Color.White
  GlassBox(Modifier.size(48.dp), CircleShape, dark = !light || MaterialTheme.colorScheme.background.red < .5f) {
    Box(Modifier.matchParentSize().clickable(enabled = enabled, onClick = onClick), contentAlignment = Alignment.Center) {
      Icon(icon, desc, tint = tint.copy(alpha = if (enabled) 1f else .4f)) }
  }
}
