package com.rise.musique.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rise.musique.Eq
import com.rise.musique.EqSettings
import com.rise.musique.EqStore
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun EqSheet(s: UiState, a: Actions) {
  ModalBottomSheet({ a.eqOpen(false) }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = Color(0xFF101012)) {
    EqContent(s, a, Modifier.fillMaxWidth().navigationBarsPadding())
  }
}

/** Glass EQ: live response curve, 10 draggable bands, presets, bass boost, preamp (auto keeps it from clipping). */
@Composable fun EqContent(s: UiState, a: Actions, modifier: Modifier = Modifier) {
  val eq = s.eq
  val haptics = rememberHaptics()
  val white = Color.White; val dim = Color.White.copy(.6f)
  fun set(n: EqSettings) = a.eqChange(n)
  Column(modifier.verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      Column(Modifier.weight(1f)) {
        Text("Equalizador", color = white, style = MaterialTheme.typography.titleLarge)
        Text("Perfil: ${EqStore.label(s.eqProfile)} · troca sozinho com a saída de áudio", color = dim, fontSize = 13.sp)
      }
      Switch(eq.on, { haptics.tick(); set(eq.copy(on = it)) }, colors = SwitchDefaults.colors(checkedTrackColor = white, checkedThumbColor = Color.Black,
        uncheckedTrackColor = Color.White.copy(.12f), uncheckedThumbColor = dim, uncheckedBorderColor = Color.Transparent))
    }
    GlassBox(Modifier.fillMaxWidth().height(132.dp), RoundedCornerShape(20.dp)) { ResponseCurve(eq, Modifier.fillMaxSize().padding(12.dp)) }
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      (Eq.PRESETS.keys + "Personalizado").forEach { name ->
        val on = eq.preset == name
        FilterChip(on, { haptics.tick(); Eq.PRESETS[name]?.let { set(eq.copy(on = true, bands = it, preset = name)) } }, label = { Text(name) },
          colors = FilterChipDefaults.filterChipColors(containerColor = Color.White.copy(.06f), labelColor = dim, selectedContainerColor = white, selectedLabelColor = Color.Black),
          border = null, modifier = Modifier.heightIn(min = 48.dp))
      }
    }
    GlassBox(Modifier.fillMaxWidth(), RoundedCornerShape(20.dp)) {
      Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        eq.bands.forEachIndexed { i, v ->
          BandSlider(v, Eq.LABELS[i], Modifier.weight(1f), eq.on) { nv ->
            if (nv.roundToInt() != v.roundToInt()) haptics.tick()
            set(eq.copy(on = true, bands = eq.bands.toMutableList().also { it[i] = nv }, preset = "Personalizado")) }
        }
      }
    }
    Labeled("Reforço de graves", "+${eq.bass.roundToInt()} dB") {
      Slider(eq.bass, { set(eq.copy(on = true, bass = it.coerceIn(0f, 12f))) }, valueRange = 0f..12f, colors = sliderColors()) }
    Labeled("Pré-amplificador", "%+.1f dB".format(eq.effectivePreamp())) {
      Slider(eq.preamp, { set(eq.copy(preamp = it.coerceIn(-12f, 6f))) }, valueRange = -12f..6f, colors = sliderColors()) }
    Row(verticalAlignment = Alignment.CenterVertically) {
      Column(Modifier.weight(1f)) { Text("Evitar distorção", color = white, fontSize = 16.sp)
        Text("Baixa o pré-amplificador o quanto a curva sobe, e um limitador segura os picos.", color = dim, fontSize = 13.sp) }
      Switch(eq.autoPreamp, { haptics.tick(); set(eq.copy(autoPreamp = it)) }, colors = SwitchDefaults.colors(checkedTrackColor = white, checkedThumbColor = Color.Black,
        uncheckedTrackColor = Color.White.copy(.12f), uncheckedThumbColor = dim, uncheckedBorderColor = Color.Transparent))
    }
    TextButton({ haptics.tick(); set(EqSettings(on = eq.on)) }, Modifier.heightIn(min = 48.dp)) { Text("Restaurar", color = white) }
  }
}

@Composable private fun sliderColors() = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = Color.White, inactiveTrackColor = Color.White.copy(.15f))

@Composable private fun Labeled(title: String, value: String, content: @Composable () -> Unit) = Column {
  Row { Text(title, Modifier.weight(1f), color = Color.White, fontSize = 16.sp); Text(value, color = Color.White.copy(.6f), fontSize = 14.sp) }
  content()
}

/** Vertical band: drag up/down (±12 dB), double-tap resets. Value drawn as a pill fill from the 0 dB line. */
@Composable private fun BandSlider(v: Float, label: String, m: Modifier, active: Boolean, on: (Float) -> Unit) {
  val cur by rememberUpdatedState(v); val cb by rememberUpdatedState(on)
  val alpha = if (active) 1f else .45f
  Column(m, horizontalAlignment = Alignment.CenterHorizontally) {
    Text("%+d".format(v.roundToInt()), color = Color.White.copy(.7f * alpha), fontSize = 11.sp)
    Spacer(Modifier.height(6.dp))
    Canvas(Modifier.width(28.dp).height(180.dp)
      .pointerInput(Unit) { detectTapGestures(onDoubleTap = { cb(0f) }) }
      .pointerInput(Unit) { detectVerticalDragGestures { ch, dy -> ch.consume()
        cb((cur - dy / size.height * 2 * Eq.RANGE).coerceIn(-Eq.RANGE, Eq.RANGE)) } }) {
      val w = 6.dp.toPx(); val x = (size.width - w) / 2; val mid = size.height / 2
      drawRoundRect(Color.White.copy(.12f), Offset(x, 0f), Size(w, size.height), CornerRadius(w / 2))
      val y = mid - (v / Eq.RANGE).coerceIn(-1f, 1f) * mid
      drawRoundRect(Color.White.copy(alpha), Offset(x, minOf(y, mid)), Size(w, kotlin.math.abs(y - mid).coerceAtLeast(1f)), CornerRadius(w / 2))
      drawCircle(Color.White.copy(alpha), 10.dp.toPx(), Offset(size.width / 2, y))
    }
    Spacer(Modifier.height(6.dp))
    Text(label, color = Color.White.copy(.6f), fontSize = 11.sp, fontWeight = FontWeight.Medium)
  }
}

/** Live response: sum of all biquads + preamp over 20 Hz–20 kHz (log axis), ±15 dB. */
@Composable private fun ResponseCurve(eq: EqSettings, m: Modifier) {
  val pts = remember(eq) { (0..120).map { i -> val f = 20.0 * 10.0.pow(i / 120.0 * 3); Eq.responseDb(eq.copy(on = true), f) } }
  val active = eq.on
  Canvas(m) {
    val mid = size.height / 2
    listOf(-12f, -6f, 0f, 6f, 12f).forEach { db -> val y = mid - db / 15f * mid
      drawLine(Color.White.copy(if (db == 0f) .25f else .08f), Offset(0f, y), Offset(size.width, y), 1f) }
    val path = Path(); val fill = Path()
    pts.forEachIndexed { i, db -> val x = i / 120f * size.width; val y = (mid - (db / 15f).coerceIn(-1f, 1f) * mid)
      if (i == 0) { path.moveTo(x, y); fill.moveTo(x, mid); fill.lineTo(x, y) } else { path.lineTo(x, y); fill.lineTo(x, y) } }
    fill.lineTo(size.width, mid); fill.close()
    drawPath(fill, Brush.verticalGradient(listOf(Color.White.copy(if (active) .25f else .08f), Color.Transparent)))
    drawPath(path, Color.White.copy(if (active) 1f else .4f), style = Stroke(2.dp.toPx()))
  }
}
