package com.rise.musique.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rise.musique.Engine
import com.rise.musique.Play
import java.util.Calendar

/** Plays grouped by day: "Hoje", "Ontem", then the date. Visible for tests. */
object ListeningHistory {
  fun dayLabel(at: Long, now: Long = System.currentTimeMillis()): String {
    fun day(t: Long) = Calendar.getInstance().apply { timeInMillis = t; set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }.timeInMillis
    val diff = ((day(now) - day(at)) / 86_400_000L).toInt()
    return when (diff) { 0 -> "Hoje"; 1 -> "Ontem"
      else -> java.text.SimpleDateFormat(if (diff < 7) "EEEE" else "d 'de' MMMM", java.util.Locale("pt", "BR")).format(java.util.Date(at)).replaceFirstChar { it.uppercase() } }
  }
  /** Newest first; consecutive repeats of the same track collapse into one row. */
  fun groups(plays: List<Play>, now: Long = System.currentTimeMillis()): List<Pair<String, List<Play>>> =
    plays.sortedByDescending { it.at }.fold(mutableListOf<Play>()) { acc, p -> if (acc.lastOrNull()?.key != p.key) acc += p; acc }
      .groupBy { dayLabel(it.at, now) }.toList()
  fun art(p: Play, history: List<HistoryItem>): String = history.firstOrNull { it.uri == p.key }?.art
    ?: Engine.videoId(p.key.removePrefix("stream:"))?.let { "https://i.ytimg.com/vi/$it/hqdefault.jpg" }.orEmpty()
}

@Composable fun HistoryScreen(s: UiState, a: Actions, list: LazyListState, back: () -> Unit, modifier: Modifier) {
  val c = MaterialTheme.colorScheme
  val haptics = rememberHaptics()
  LaunchedEffect(Unit) { a.loadPlays() }
  val groups = remember(s.plays) { ListeningHistory.groups(s.plays) }
  Column(modifier) {
    Row(Modifier.fillMaxWidth().height(TopBarHeight).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
      IconButton(back) { Icon(Icons.Rounded.ArrowBack, "Voltar") }
      Text("Histórico", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
    }
    LazyColumn(Modifier.weight(1f), state = list, contentPadding = PaddingValues(top = 4.dp, bottom = bottomSpace(s))) {
      if (groups.isEmpty()) item(key = "empty") { EmptyState(Icons.Rounded.History, "Nada ouvido ainda", "As músicas que você ouvir aparecem aqui, com Tocar de novo.") }
      groups.forEach { (day, plays) ->
        item(key = "d:$day") { Text(day, Modifier.padding(start = 24.dp, end = 24.dp, top = 18.dp, bottom = 6.dp), style = MaterialTheme.typography.titleMedium) }
        items(plays, key = { "p:${it.key}:${it.at}" }) { p ->
          val src = remember { MutableInteractionSource() }
          Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp).animateItem().weightedPress(src, 14.dp).clip(RoundedCornerShape(14.dp))
              .clickable(src, null) { haptics.tick(); a.playAgain(p) }.padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Cover(HistoryItem(p.title, "", p.key, ListeningHistory.art(p, s.history)), Modifier.size(52.dp), 8.dp); Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
              Text(p.title, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, color = if (s.now?.key == p.key) accentColor() else c.onSurface)
              Text(listOf(p.artist, java.text.SimpleDateFormat("HH:mm", java.util.Locale.ROOT).format(java.util.Date(p.at))).filter(String::isNotBlank).joinToString(" · "),
                color = c.onSurfaceVariant, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Row(Modifier.clip(CircleShape).background(c.surfaceVariant).clickable { haptics.press(); a.playAgain(p) }.padding(horizontal = 12.dp, vertical = 8.dp),
              verticalAlignment = Alignment.CenterVertically) {
              Icon(Icons.Rounded.Replay, null, Modifier.size(16.dp)); Spacer(Modifier.width(6.dp))
              Text("Tocar de novo", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
          }
        }
      }
    }
  }
}
