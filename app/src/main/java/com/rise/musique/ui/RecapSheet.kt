package com.rise.musique.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.IosShare
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rise.musique.Recap

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun RecapSheet(s: UiState, a: Actions) {
  var period by remember { mutableStateOf("Mês") }
  ModalBottomSheet({ a.recap(false) }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(horizontal = 24.dp),
      verticalArrangement = Arrangement.spacedBy(14.dp)) {
      Text("Retrospectiva", style = MaterialTheme.typography.headlineLarge)
      PillSelector(listOf("Mês", "Ano"), period, { it }, { period = it }, Modifier.fillMaxWidth())
      val r = if (period == "Ano") s.recapYear else s.recapMonth
      if (r == null) Text("Carregando…") else {
        key(period) { RecapCard(r, Modifier.fillMaxWidth().aspectRatio(9f / 16f)) }
        PillButton("Compartilhar nos stories", { a.shareRecap(period) }, Modifier.fillMaxWidth(), icon = Icons.Rounded.IosShare, enabled = r.plays > 0)
      }
      Spacer(Modifier.height(8.dp))
    }
  }
}

/** Story-format card: lines rise in one after another and the minutes count up. All progress values clamped to 0..1. */
@Composable fun RecapCard(r: Recap, modifier: Modifier = Modifier) {
  val enter = remember { Animatable(if (StaticPreview.on) 1f else 0f) }
  LaunchedEffect(r) { enter.animateTo(1f, tween(1600, easing = FastOutSlowInEasing)) }
  fun stage(i: Int) = ((enter.value * 6f - i) ).coerceIn(0f, 1f)
  Box(modifier.clip(RoundedCornerShape(28.dp))) {
    LivingArtwork("", Modifier.matchParentSize(), dim = 0f)
    Column(Modifier.fillMaxSize().padding(26.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
      @Composable fun Line(i: Int, content: @Composable () -> Unit) = Box(Modifier.graphicsLayer {
        val p = stage(i); alpha = p; translationY = (1f - p) * 40f }) { content() }
      Line(0) { Text("rise", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold) }
      Spacer(Modifier.weight(.6f))
      Line(1) { Column { Text(r.title, color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.Bold, lineHeight = 38.sp)
        Text(r.period, color = Color.White.copy(.65f), fontSize = 14.sp) } }
      Spacer(Modifier.weight(.4f))
      Line(2) { Column { Text("${(r.minutes * stage(2)).toLong()}", color = Color.White, fontSize = 64.sp, fontWeight = FontWeight.Bold, lineHeight = 66.sp)
        Text("minutos ouvindo · ${r.plays} reproduções", color = Color.White.copy(.7f), fontSize = 13.sp) } }
      Spacer(Modifier.weight(.4f))
      r.topArtist?.let { t -> Line(3) { Column { Label("ARTISTA DO PERÍODO"); Big(t.name) } } }
      if (r.topTracks.isNotEmpty()) Line(4) { Column(verticalArrangement = Arrangement.spacedBy(4.dp)) { Label("MAIS TOCADAS")
        r.topTracks.forEachIndexed { i, t -> Text("${i + 1}  ${t.name}", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold,
          maxLines = 1, overflow = TextOverflow.Ellipsis) } } }
      if (r.plays == 0) Line(3) { Text("Ainda sem reproduções neste período.", color = Color.White.copy(.7f)) }
      Spacer(Modifier.weight(1f))
    }
  }
}
@Composable private fun Label(t: String) = Text(t, color = Color.White.copy(.6f), fontSize = 11.sp, letterSpacing = 1.sp)
@Composable private fun Big(t: String) = Text(t, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
