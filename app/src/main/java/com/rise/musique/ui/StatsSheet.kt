package com.rise.musique.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rise.musique.PlayStats
import com.rise.musique.Ranked
import com.rise.musique.StatsSummary

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun StatsSheet(s: UiState, a: Actions) {
  ModalBottomSheet({ a.stats(false) }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).navigationBarsPadding()) {
      StatsContent(s.stats, Modifier.fillMaxWidth())
      PillButton("Sua retrospectiva", { a.recap(true) }, Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp))
    }
  }
}

@Composable fun StatsContent(stats: StatsSummary?, modifier: Modifier = Modifier) {
  val c = MaterialTheme.colorScheme
  Column(modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
    Text("Estatísticas", style = MaterialTheme.typography.headlineLarge)
    if (stats == null) { Text("Carregando…", color = c.onSurfaceVariant); return@Column }
    if (stats.totalPlays == 0) { Text("Ainda não há reproduções. Uma faixa conta depois de 30 segundos ou metade da duração.", color = c.onSurfaceVariant); return@Column }
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
      Tile("Tempo ouvindo", PlayStats.duration(stats.totalMs), Modifier.weight(1f))
      Tile("Reproduções", stats.totalPlays.toString(), Modifier.weight(1f))
    }
    Text("Por mês", style = MaterialTheme.typography.titleMedium)
    MonthBars(stats)
    Text("Faixas mais tocadas", style = MaterialTheme.typography.titleMedium)
    stats.topTracks.forEachIndexed { i, r -> RankRow(i + 1, r) }
    if (stats.topArtists.isNotEmpty()) {
      Text("Artistas mais ouvidos", style = MaterialTheme.typography.titleMedium)
      stats.topArtists.forEachIndexed { i, r -> RankRow(i + 1, r) }
    }
  }
}

@Composable private fun Tile(label: String, value: String, modifier: Modifier) {
  val c = MaterialTheme.colorScheme
  Column(modifier.clip(RoundedCornerShape(24.dp)).background(c.surfaceVariant).padding(18.dp)) {
    Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, maxLines = 1)
    Text(label, color = c.onSurfaceVariant, fontSize = 12.sp)
  }
}

@Composable private fun MonthBars(stats: StatsSummary) {
  val c = MaterialTheme.colorScheme
  val max = stats.months.maxOfOrNull { it.ms }?.coerceAtLeast(1) ?: 1
  var shown by remember { mutableStateOf(StaticPreview.on) }
  LaunchedEffect(Unit) { shown = true }
  Row(Modifier.fillMaxWidth().height(150.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
    stats.months.forEach { m ->
      val target = (m.ms.toFloat() / max).coerceIn(0f, 1f)
      val grow by animateFloatAsState(if (shown) target else 0f, Motion.settle(220f), label = "bar")
      Column(Modifier.weight(1f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Bottom) {
        Text(if (m.plays > 0) m.plays.toString() else "", fontSize = 11.sp, color = c.onSurfaceVariant)
        Box(Modifier.fillMaxWidth().height(4.dp + 86.dp * grow.coerceIn(0f, 1f))
          .clip(RoundedCornerShape(12.dp)).background(if (m == stats.months.last()) c.onSurface else c.outline))
        Spacer(Modifier.height(6.dp))
        Text(m.label, fontSize = 11.sp, color = c.onSurfaceVariant, maxLines = 1)
      }
    }
  }
}

@Composable private fun RankRow(n: Int, r: Ranked) {
  val c = MaterialTheme.colorScheme
  Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
    Box(Modifier.size(34.dp).clip(CircleShape).background(c.surfaceVariant), contentAlignment = Alignment.Center) {
      Text("$n", fontWeight = FontWeight.Bold, fontSize = 13.sp) }
    Spacer(Modifier.width(12.dp))
    Column(Modifier.weight(1f)) {
      Text(r.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
      if (r.sub.isNotBlank()) Text(r.sub, color = c.onSurfaceVariant, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
    Text("${r.plays}× · ${PlayStats.duration(r.ms)}", color = c.onSurfaceVariant, fontSize = 12.sp)
  }
}
