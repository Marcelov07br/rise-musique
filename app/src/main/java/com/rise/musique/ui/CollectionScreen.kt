package com.rise.musique.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Album or playlist: hero cover over the living artwork, title, meta line, glass actions and a numbered track list. */
@Composable fun CollectionScreen(s: UiState, a: Actions, modifier: Modifier = Modifier) {
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
  LazyColumn(modifier, contentPadding = PaddingValues(bottom = if (s.now != null) 100.dp else 20.dp)) {
    item {
      Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp))) {
        LivingArtwork(hero?.art.orEmpty(), Modifier.matchParentSize(), dim = .3f)
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(top = 8.dp, bottom = 22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
          Row(Modifier.fillMaxWidth()) { GlassIcon(Icons.Rounded.ArrowBack, "Voltar", back) }
          Spacer(Modifier.height(8.dp))
          Box(Modifier.size(232.dp).shadow(28.dp, RoundedCornerShape(20.dp))) {
            if (hero != null) Cover(hero, Modifier.fillMaxSize())
            else Box(Modifier.fillMaxSize().clip(RoundedCornerShape(20.dp)).background(Color.White.copy(.1f)), contentAlignment = Alignment.Center) {
              Icon(if (isAlbum) Icons.Rounded.Album else Icons.Rounded.QueueMusic, null, tint = Color.White.copy(.7f), modifier = Modifier.size(72.dp)) }
          }
          Spacer(Modifier.height(18.dp))
          Text(title, color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
          Spacer(Modifier.height(4.dp))
          Text(meta, color = Color.White.copy(.65f), fontSize = 11.sp, letterSpacing = 1.sp, textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis)
          Spacer(Modifier.height(18.dp))
          Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            GlassIcon(Icons.Rounded.Shuffle, "Aleatório", { haptics.press(); tracks.randomOrNull()?.let(a.playShuffled) }, enabled = tracks.isNotEmpty())
            GlassBox(Modifier.height(52.dp).width(150.dp), CircleShape) {
              Row(Modifier.matchParentSize().clickable(enabled = tracks.isNotEmpty()) { haptics.press(); tracks.firstOrNull()?.let(a.play) },
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                Icon(Icons.Rounded.PlayArrow, null, tint = Color.White); Spacer(Modifier.width(6.dp))
                Text("Tocar", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 16.sp) }
            }
            GlassIcon(Icons.Rounded.PlaylistAdd, "Adicionar tudo à fila", { haptics.tick(); a.queueAll(tracks) }, enabled = tracks.isNotEmpty())
          }
        }
      }
      Spacer(Modifier.height(10.dp))
    }
    if (tracks.isEmpty()) item { Text("Nada por aqui ainda", Modifier.padding(24.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) }
    itemsIndexed(tracks, key = { _, h -> h.uri }) { i, h ->
      Box(Modifier.padding(horizontal = 16.dp)) { TrackRow(h, s, a, number = i + 1) }
      if (i < tracks.lastIndex) HorizontalDivider(Modifier.padding(start = 66.dp, end = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
    }
  }
}

@Composable private fun GlassIcon(icon: ImageVector, desc: String, onClick: () -> Unit, enabled: Boolean = true) {
  GlassBox(Modifier.size(52.dp), CircleShape) {
    Box(Modifier.matchParentSize().clickable(enabled = enabled, onClick = onClick), contentAlignment = Alignment.Center) {
      Icon(icon, desc, tint = Color.White.copy(if (enabled) 1f else .4f)) }
  }
}
