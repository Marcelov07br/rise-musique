package com.rise.musique.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.rise.musique.DateFilter
import com.rise.musique.DurationFilter
import com.rise.musique.SearchKind

/** One scrolling row: type chips + a "Filtros" chip that opens duration / upload date in a sheet (keeps the top compact). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable fun SearchFilters(s: UiState, a: Actions) {
  val haptics = rememberHaptics()
  var sheet by remember { mutableStateOf(false) }
  val active = (if (s.durationFilter != DurationFilter.Any) 1 else 0) + (if (s.dateFilter != DateFilter.Any) 1 else 0)
  Row(Modifier.fillMaxWidth().bleed(20.dp).horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
    Chip(if (active > 0) "Filtros · $active" else "Filtros", active > 0, small = true, icon = Icons.Rounded.Tune) { haptics.tick(); sheet = true }
    SearchKind.entries.forEach { k -> Chip(k.label, s.searchKind == k, small = true) { haptics.tick(); a.searchKind(k) } }
  }
  if (sheet) ModalBottomSheet({ sheet = false }) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
      Text("Filtros", style = MaterialTheme.typography.titleLarge)
      Text("Duração", style = MaterialTheme.typography.titleSmall)
      Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(DurationFilter.Any to "Qualquer", DurationFilter.Short to "Curta <4 min", DurationFilter.Medium to "Média", DurationFilter.Long to "Longa >20 min").forEach { (d, l) ->
          Chip(l, s.durationFilter == d, small = true) { haptics.tick(); a.durationFilter(d) } } }
      Text("Data de envio", style = MaterialTheme.typography.titleSmall)
      Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        DateFilter.entries.forEach { d -> Chip(if (d == DateFilter.Any) "Qualquer" else d.label, s.dateFilter == d, small = true) { haptics.tick(); a.dateFilter(d) } } }
      if (active > 0) TextButton({ a.durationFilter(DurationFilter.Any); a.dateFilter(DateFilter.Any) }) { Text("Limpar filtros") }
    }
  }
}

@Composable private fun Chip(label: String, on: Boolean, small: Boolean = false, icon: androidx.compose.ui.graphics.vector.ImageVector? = null, click: () -> Unit) {
  val c = MaterialTheme.colorScheme
  FilterChip(on, click, label = { Text(label, fontSize = if (small) 13.sp else 14.sp) }, shape = CircleShape, border = null,
    leadingIcon = icon?.let { { Icon(it, null, Modifier.size(16.dp)) } },
    colors = FilterChipDefaults.filterChipColors(containerColor = c.surfaceVariant, labelColor = c.onSurfaceVariant, selectedContainerColor = c.onSurface, selectedLabelColor = c.surface),
    modifier = Modifier.heightIn(min = if (small) 36.dp else 40.dp))
}

/** Album / playlist / artist from the search. */
@Composable fun CollectionResult(r: SearchResult, open: () -> Unit) {
  val c = MaterialTheme.colorScheme
  val src = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
  val circle = r.kind == "artist"
  Row(Modifier.fillMaxWidth().weightedPress(src, 18.dp).clip(RoundedCornerShape(18.dp)).clickable(src, null, onClick = open).padding(horizontal = 4.dp, vertical = 6.dp),
    verticalAlignment = Alignment.CenterVertically) {
    AsyncImage(r.thumb, null, Modifier.size(54.dp).clip(if (circle) CircleShape else RoundedCornerShape(10.dp)).background(c.outline), contentScale = ContentScale.Crop)
    Spacer(Modifier.width(12.dp))
    Column(Modifier.weight(1f)) {
      Text(r.title, color = c.onSurface, fontSize = 15.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
      Text(r.subtitle.ifBlank { when (r.kind) { "album" -> "Álbum"; "artist" -> "Artista"; else -> "Playlist" } }, color = c.onSurfaceVariant, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
    Icon(Icons.Rounded.ChevronRight, null, tint = c.onSurfaceVariant)
  }
}

/** Loading placeholder shaped like the result rows; the shimmer is read in the draw phase only. */
@Composable fun SkeletonRow(kind: SearchKind) {
  val c = MaterialTheme.colorScheme
  val t = rememberInfiniteTransition(label = "sk")
  val pulse = t.animateFloat(0f, 1f, infiniteRepeatable(tween(Motion.ExtraLong, easing = Motion.Standard), RepeatMode.Reverse), label = "p")
  val shade = Modifier.graphicsLayer { alpha = .45f + .35f * pulse.value.coerceIn(0f, 1f) }
  Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
    Box(shade.size(width = if (kind == SearchKind.Videos) 112.dp else 63.dp, height = 63.dp)
      .clip(if (kind == SearchKind.Artists) CircleShape else RoundedCornerShape(14.dp)).background(c.surfaceVariant))
    Spacer(Modifier.width(12.dp))
    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
      Box(shade.fillMaxWidth(.8f).height(14.dp).clip(CircleShape).background(c.surfaceVariant))
      Box(shade.fillMaxWidth(.5f).height(12.dp).clip(CircleShape).background(c.surfaceVariant))
    }
  }
}

/** Listening card: mic disc that grows with the voice level, live transcript, cancel. */
@Composable fun VoicePanel(v: VoiceUi, a: Actions) {
  val c = MaterialTheme.colorScheme
  val level by animateFloatAsState(v.level.coerceIn(0f, 1f), Motion.settle(), label = "lvl")
  Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(c.surfaceVariant).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
    Box(Modifier.size(56.dp), contentAlignment = Alignment.Center) {
      Box(Modifier.matchParentSize().graphicsLayer { val k = .7f + .3f * level.coerceIn(0f, 1f); scaleX = k; scaleY = k }.clip(CircleShape)
        .background(c.onSurface.copy(alpha = if (v.listening) .18f else .08f)))
      Icon(if (v.error != null) Icons.Rounded.MicOff else Icons.Rounded.Mic, null, tint = c.onSurface)
    }
    Spacer(Modifier.width(14.dp))
    Column(Modifier.weight(1f)) {
      Text(when { v.error != null -> "Busca por voz"; v.listening -> "Ouvindo…"; else -> "Reconhecendo…" }, style = MaterialTheme.typography.titleMedium)
      Text(v.error ?: v.partial.ifBlank { "Fale o nome da música ou do artista" }, color = c.onSurfaceVariant, fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
    IconButton(a.voiceStop, Modifier.size(48.dp)) { Icon(Icons.Rounded.Close, "Fechar busca por voz") }
  }
}
