package com.rise.musique.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rise.musique.CutVisual
import com.rise.musique.Storyboard

private fun parseTime(t: String): Int? = t.trim().takeIf { it.isNotEmpty() }?.split(":")?.map { it.toIntOrNull() ?: return null }?.fold(0) { acc, n -> acc * 60 + n }
private fun fmtTime(s: Int) = "%d:%02d".format(s / 60, s % 60)

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun CutSheet(s: UiState, a: Actions) {
  ModalBottomSheet({ a.openCut(false) }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
    CutContent(s, a, Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 24.dp).padding(bottom = 16.dp))
  }
}

/** Range picker: waveform (music) or storyboard strip (video), two handles, time fields, range preview, confirm/clear. */
@Composable fun CutContent(s: UiState, a: Actions, modifier: Modifier = Modifier) {
  val c = MaterialTheme.colorScheme
  val r = s.results.firstOrNull { it.url in s.selected } ?: return
  val total = r.duration.coerceAtLeast(1)
  var range by remember(r.url) { mutableStateOf(
    ((parseTime(s.cutStart) ?: 0).coerceIn(0, total).toFloat() / total)..((parseTime(s.cutEnd) ?: total).coerceIn(0, total).toFloat() / total)) }
  val startS = (range.start * total).toInt(); val endS = (range.endInclusive * total).toInt()
  var startText by remember(startS) { mutableStateOf(fmtTime(startS)) }
  var endText by remember(endS) { mutableStateOf(fmtTime(endS)) }
  val haptics = rememberHaptics()
  val previewing = s.previewing == r.url && s.now?.isPreview == true
  Column(modifier, verticalArrangement = Arrangement.spacedBy(16.dp)) {
    Text("Cortar trecho", style = MaterialTheme.typography.titleLarge)
    Text(r.title, color = c.onSurfaceVariant, fontSize = 13.sp, maxLines = 1)
    Box(Modifier.fillMaxWidth().height(88.dp).clip(RoundedCornerShape(16.dp)).background(c.surfaceVariant), contentAlignment = Alignment.Center) {
      val v = s.cutVisual
      when {
        v == null || v.loading -> Row(verticalAlignment = Alignment.CenterVertically) {
          CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = c.onSurface); Spacer(Modifier.width(12.dp))
          Text(if (s.mode == Mode.Video) "Carregando quadros…" else "Lendo a forma de onda…", color = c.onSurfaceVariant, fontSize = 13.sp) }
        v.peaks != null -> Waveform(v.peaks, range, Modifier.fillMaxSize().padding(horizontal = 8.dp, vertical = 12.dp))
        v.storyboard != null -> StoryStrip(v.storyboard, total, range, Modifier.fillMaxSize())
        else -> Text(v.error ?: "Prévia visual indisponível", color = c.onSurfaceVariant, fontSize = 13.sp)
      }
    }
    RangeSlider(range, { nv -> if ((nv.start * total).toInt() != startS || (nv.endInclusive * total).toInt() != endS) haptics.tick()
        range = nv.start.coerceIn(0f, 1f)..nv.endInclusive.coerceIn(0f, 1f) },
      colors = SliderDefaults.colors(thumbColor = c.onSurface, activeTrackColor = c.onSurface, inactiveTrackColor = c.outline))
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
      TimeField("Início", startText, "0:00", Modifier.weight(1f)) { t -> startText = t
        parseTime(t)?.takeIf { it in 0 until endS }?.let { range = (it.toFloat() / total)..range.endInclusive } }
      TimeField("Fim", endText, fmtTime(total), Modifier.weight(1f)) { t -> endText = t
        parseTime(t)?.takeIf { it in (startS + 1)..total }?.let { range = range.start..(it.toFloat() / total) } }
    }
    Text("Trecho de ${fmtTime((endS - startS).coerceAtLeast(0))}", color = c.onSurfaceVariant, fontSize = 13.sp)
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
      PillButton(if (previewing) "Parar" else "Ouvir trecho", { haptics.press()
        if (previewing) a.preview(r) else a.previewRange(startS * 1000L, endS * 1000L) }, Modifier.weight(1f),
        icon = if (previewing) Icons.Rounded.Stop else Icons.Rounded.PlayArrow, filled = false)
      PillButton("Limpar", { haptics.tick(); a.cut(false, "", ""); a.openCut(false) }, Modifier.weight(1f), filled = false)
    }
    PillButton("Usar este trecho", { haptics.press()
      a.cut(true, if (startS > 0) fmtTime(startS) else "", if (endS < total) fmtTime(endS) else ""); a.openCut(false) },
      Modifier.fillMaxWidth(), enabled = endS > startS)
  }
}

@Composable private fun Waveform(peaks: List<Float>, range: ClosedFloatingPointRange<Float>, m: Modifier) {
  val on = MaterialTheme.colorScheme.onSurface; val off = MaterialTheme.colorScheme.outline
  Canvas(m) {
    val n = peaks.size; val w = size.width / n
    peaks.forEachIndexed { i, p -> val f = (i + .5f) / n; val h = (size.height * (.08f + .92f * p.coerceIn(0f, 1f)))
      drawRoundRect(if (f in range) on else off, Offset(i * w + w * .2f, (size.height - h) / 2), Size(w * .6f, h), CornerRadius(w * .3f)) }
  }
}

@Composable private fun StoryStrip(board: Storyboard, totalSec: Int, range: ClosedFloatingPointRange<Float>, m: Modifier) {
  val ctx = LocalContext.current
  val sheets = remember(board) { mutableStateMapOf<Int, ImageBitmap>() }
  val frames = 8
  LaunchedEffect(board) {
    val needed = (0 until frames).map { board.frameAt(totalSec * (it + .5) / frames).first }.distinct()
    needed.forEach { i -> runCatching {
      val res = coil.ImageLoader(ctx).execute(coil.request.ImageRequest.Builder(ctx).data(board.sheets[i].first).allowHardware(false).build())
      (res.drawable as? android.graphics.drawable.BitmapDrawable)?.bitmap?.let { sheets[i] = it.asImageBitmap() } } }
  }
  val dim = MaterialTheme.colorScheme.background.copy(alpha = .6f)
  Canvas(m) {
    val w = size.width / frames
    for (k in 0 until frames) {
      val (sheet, col, row) = board.frameAt(totalSec * (k + .5) / frames)
      val img = sheets[sheet] ?: continue
      val fw = img.width / board.cols; val fh = img.height / board.rows
      drawImage(img, IntOffset(col * fw, row * fh), IntSize(fw, fh), IntOffset((k * w).toInt(), 0), IntSize(w.toInt() + 1, size.height.toInt()))
    }
    drawRect(dim, Offset.Zero, Size(size.width * range.start, size.height))
    drawRect(dim, Offset(size.width * range.endInclusive, 0f), Size(size.width * (1 - range.endInclusive), size.height))
  }
}
