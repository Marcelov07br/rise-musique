package com.rise.musique.ui

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val TabTitles = mapOf("Início" to "Ouvir agora", "Buscar" to "Buscar", "Biblioteca" to "Biblioteca")

/** App accent: a muted rose, used sparingly (active tab, small highlights). Fixed — not dynamic color. */
@Composable fun accentColor(): Color = if (MaterialTheme.colorScheme.background.red < .5f) Color(0xFFFF6B81) else Color(0xFFD93A55)

/** 0 = large title fully shown, 1 = scrolled past it (derived, so only crossing changes recompose readers). */
@Composable fun rememberTitleCollapse(list: LazyListState): State<Float> {
  val px = with(LocalDensity.current) { 56.dp.toPx() }
  return remember(list) { derivedStateOf { if (list.firstVisibleItemIndex > 0) 1f else (list.firstVisibleItemScrollOffset / px).coerceIn(0f, 1f) } }
}

/** Big bold tab title (34sp) that fades and shrinks toward its start as the list scrolls; read only in the draw phase. */
@Composable fun LargeTitle(text: String, collapse: State<Float>, modifier: Modifier = Modifier) =
  Text(text, modifier.graphicsLayer { val v = collapse.value.coerceIn(0f, 1f); alpha = 1f - v; val s = 1f - .12f * v; scaleX = s; scaleY = s
    transformOrigin = TransformOrigin(0f, .5f) }, fontSize = 34.sp, lineHeight = 40.sp, fontWeight = FontWeight.Bold, letterSpacing = (-.5).sp)

/** For pinned headers (Buscar): the row's height follows the collapse in the layout phase only — no recomposition per frame. */
fun Modifier.collapsingHeight(collapse: State<Float>) = layout { m, c ->
  val p = m.measure(c); val h = (p.height * (1f - collapse.value.coerceIn(0f, 1f))).toInt()
  layout(p.width, h) { p.placeRelative(0, h - p.height) } }.graphicsLayer { clip = true }

/** Space under lists so the floating dock (and the mini player above it) never cover content. */
@Composable fun bottomSpace(s: UiState) = if (s.now != null) 176.dp else 104.dp

/** Lets a horizontally scrolling row run to the screen edges past its parent's [by] padding (content still starts aligned). */
fun Modifier.bleed(by: androidx.compose.ui.unit.Dp) = layout { m, c ->
  val extra = (by * 2).roundToPx()
  val p = m.measure(c.copy(minWidth = (c.minWidth + extra).coerceAtLeast(0), maxWidth = if (c.hasBoundedWidth) c.maxWidth + extra else c.maxWidth))
  layout(c.maxWidth.takeIf { c.hasBoundedWidth } ?: p.width, p.height) { p.placeRelative(-by.roundToPx(), 0) } }
