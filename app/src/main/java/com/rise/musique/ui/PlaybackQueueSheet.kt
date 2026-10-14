package com.rise.musique.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Queue motion: lift/settle springs with a clamped bounce (ζ ≥ 0.6, overshoot only in graphicsLayer). */
object QueueMotion {
  /** Hold time before a row lifts for dragging (~1 s, so scrolling and swiping never lift by accident). */
  const val LiftMs = 1000L
  val RowHeight = 68.dp
  fun <T> lift() = Motion.spring<T>(520f, Motion.PopBounce)
  fun <T> settle() = Motion.spring<T>(420f)
  val place = Motion.place
  /** Index the dragged row lands on, given its start index and the finger's total travel. */
  fun target(start: Int, travelPx: Float, rowPx: Float, size: Int) =
    (start + kotlin.math.round(travelPx / rowPx).toInt()).coerceIn(0, (size - 1).coerceAtLeast(0))
  /** Auto-scroll speed (px per frame) while a lifted row sits within [edge] of the viewport edge; ramps up to [max] (0.24). */
  fun edgeSpeed(top: Float, bottom: Float, viewStart: Float, viewEnd: Float, edge: Float, max: Float = 22f): Float = when {
    edge <= 0f -> 0f
    bottom > viewEnd - edge -> max * ((bottom - (viewEnd - edge)) / edge).coerceIn(0f, 1f)
    top < viewStart + edge -> -max * (((viewStart + edge) - top) / edge).coerceIn(0f, 1f)
    else -> 0f }
}

/** One row of "A seguir" with a key stable across moves (same track twice gets #1, #2). */
data class QueueEntry(val key: String, val track: HistoryItem)
fun queueEntries(upcoming: List<HistoryItem>): List<QueueEntry> { val seen = HashMap<String, Int>()
  return upcoming.map { h -> val n = (seen[h.uri] ?: 0) + 1; seen[h.uri] = n; QueueEntry("${h.uri}#$n", h) } }

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun PlaybackQueueSheet(state: UiState, a: Actions) {
  ModalBottomSheet(onDismissRequest = { a.showQueue(false) }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
    // The sheet is its own window: it carries its own Desfazer bar.
    Box { QueueContent(state, a); UndoBar(state, a, Modifier.align(Alignment.BottomCenter), bottom = 12.dp) }
  }
}

@Composable fun QueueContent(s: UiState, a: Actions) {
  val c = MaterialTheme.colorScheme
  val haptics = rememberHaptics()
  val cur = s.now?.index ?: 0
  val current = s.playbackQueue.getOrNull(cur)
  val entries = remember(s.playbackQueue, cur) { queueEntries(s.playbackQueue.drop(cur + 1)) }
  // While a row is lifted the list order is local; the player is told once, on drop.
  var dragList by remember { mutableStateOf<List<QueueEntry>?>(null) }
  var dragKey by remember { mutableStateOf<String?>(null) }
  var dragStart by remember { mutableIntStateOf(0) }
  var travel by remember { mutableFloatStateOf(0f) }
  val settle = remember { Animatable(0f) }
  var settlingKey by remember { mutableStateOf<String?>(null) }
  val shown = dragList ?: entries
  val rowPx = with(LocalDensity.current) { QueueMotion.RowHeight.toPx() }
  val scope = rememberCoroutineScope()
  val openedAt = remember { System.currentTimeMillis() }
  val latest by rememberUpdatedState(shown)
  val listState = rememberLazyListState()
  val edgePx = with(LocalDensity.current) { 56.dp.toPx() }
  fun reorder(key: String) { val list = dragList ?: return
    val from = list.indexOfFirst { it.key == key }; val to = QueueMotion.target(dragStart, travel, rowPx, list.size)
    if (from >= 0 && to != from) { dragList = list.toMutableList().apply { add(to, removeAt(from)) }; haptics.tick() } }
  // 0.24: dragging a lifted row near the top/bottom edge scrolls the list; travel absorbs the scroll so the row stays under the finger.
  LaunchedEffect(dragKey) { val key = dragKey ?: return@LaunchedEffect
    while (dragKey == key) { androidx.compose.runtime.withFrameNanos { }
      val info = listState.layoutInfo; val item = info.visibleItemsInfo.firstOrNull { it.key == key } ?: continue
      val idx = (dragList ?: break).indexOfFirst { it.key == key }.takeIf { it >= 0 } ?: break
      val top = item.offset + travel - (idx - dragStart) * rowPx
      val v = QueueMotion.edgeSpeed(top, top + item.size, info.viewportStartOffset.toFloat(), (info.viewportEndOffset - info.afterContentPadding).toFloat(), edgePx)
      if (v != 0f) { val used = listState.scrollBy(v); if (used != 0f) { travel += used; reorder(key) } } } }
  Column(Modifier.fillMaxWidth().navigationBarsPadding()) {
    Text("Fila de reprodução", Modifier.padding(horizontal = 24.dp), style = MaterialTheme.typography.titleLarge)
    if (current != null) Row(Modifier.padding(horizontal = 24.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
      Cover(current, Modifier.size(56.dp), 10.dp); Spacer(Modifier.width(14.dp))
      Column(Modifier.weight(1f)) {
        Text("TOCANDO AGORA", color = c.onSurfaceVariant, fontSize = 11.sp, letterSpacing = 1.sp)
        Text(current.title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (current.artist.isNotBlank()) Text(current.artist, color = c.onSurfaceVariant, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
      }
    }
    Row(Modifier.padding(start = 24.dp, end = 12.dp), verticalAlignment = Alignment.CenterVertically) {
      Text("A seguir", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
      if (s.playbackQueue.isNotEmpty()) TextButton({ haptics.tick(); a.saveQueue() }, Modifier.heightIn(min = 48.dp)) {
        Icon(Icons.Rounded.PlaylistAdd, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Salvar como playlist") }
      if (entries.isNotEmpty()) TextButton({ a.clearQueue() }, Modifier.heightIn(min = 48.dp)) { Text("Limpar") }
    }
    if (entries.isEmpty()) EmptyState(Icons.Rounded.QueueMusic, "Nada na fila depois desta",
      if (s.smartQueue) "Quando acabar, a fila inteligente continua com músicas parecidas." else "Toque e segure uma faixa e escolha Tocar em seguida ou Adicionar à fila.")
    else Text("Segure 1 s para arrastar · deslize → tocar em seguida, ← remover", Modifier.padding(horizontal = 24.dp),
      fontSize = 12.sp, color = c.onSurfaceVariant)
    Spacer(Modifier.height(6.dp))
    LazyColumn(Modifier.fillMaxWidth().heightIn(max = 520.dp), listState, contentPadding = PaddingValues(bottom = 72.dp)) {
      itemsIndexed(shown, key = { _, e -> e.key }) { i, e ->
        val lifted = e.key == dragKey
        val elevated = lifted || e.key == settlingKey
        val queueIndex = cur + 1 + entries.indexOfFirst { it.key == e.key }.coerceAtLeast(0)
        val appear = remember { Animatable(if (!StaticPreview.on && System.currentTimeMillis() - openedAt < 400) 0f else 1f) }
        LaunchedEffect(Unit) { if (appear.value < 1f) { delay(minOf(i, 8) * 35L); appear.animateTo(1f, QueueMotion.settle()) } }
        val dismiss = rememberSwipeToDismissBoxState(confirmValueChange = { v ->
          when (v) {
            SwipeToDismissBoxValue.EndToStart -> { haptics.press(); a.removeQueueUndo(queueIndex); true }
            SwipeToDismissBoxValue.StartToEnd -> { haptics.press(); a.playNextFromQueue(queueIndex); false }
            else -> false }
        })
        Box(Modifier.zIndex(if (elevated) 1f else 0f).animateItem(fadeInSpec = tween(260), placementSpec = if (elevated) null else QueueMotion.place, fadeOutSpec = tween(180))
            .graphicsLayer { alpha = appear.value.coerceIn(0f, 1f); translationY = (1f - appear.value) * 24.dp.toPx() +
              when { lifted -> travel - (i - dragStart) * rowPx; e.key == settlingKey -> settle.value; else -> 0f } }) {
          SwipeToDismissBox(dismiss, backgroundContent = { SwipeHint(dismiss) },
              enableDismissFromStartToEnd = dragKey == null, enableDismissFromEndToStart = dragKey == null) {
            QueueRow(e.track, lifted, Modifier.semantics {
                customActions = listOf(CustomAccessibilityAction("Tocar em seguida") { a.playNextFromQueue(queueIndex); true },
                  CustomAccessibilityAction("Remover da fila") { a.removeQueueUndo(queueIndex); true }) }
              .pointerInput(e.key) {
                awaitEachGesture {
                  val down = awaitFirstDown(requireUnconsumed = false)
                  // Hold still for LiftMs → lift; move (scroll/swipe) or release earlier → not a drag.
                  val early = withTimeoutOrNull(QueueMotion.LiftMs) {
                    while (true) { val ch = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: return@withTimeoutOrNull "gone"
                      if (!ch.pressed) return@withTimeoutOrNull if ((ch.position - down.position).getDistance() < viewConfiguration.touchSlop) "tap" else "gone"
                      if (ch.isConsumed || (ch.position - down.position).getDistance() > viewConfiguration.touchSlop) return@withTimeoutOrNull "gone" }
                    @Suppress("UNREACHABLE_CODE") "gone"
                  }
                  if (early == "tap") { haptics.tick(); a.playQueue(queueIndex); return@awaitEachGesture }
                  if (early != null) return@awaitEachGesture
                  val start = latest.indexOfFirst { it.key == e.key }.takeIf { it >= 0 } ?: return@awaitEachGesture
                  haptics.press(); dragList = latest; dragStart = start; travel = 0f; dragKey = e.key
                  drag(down.id) { ch ->
                    ch.consume(); travel += ch.positionChange().y; reorder(e.key)
                  }
                  val list = dragList.orEmpty(); val to = list.indexOfFirst { it.key == e.key }
                  val rest = travel - (to - dragStart) * rowPx
                  settlingKey = e.key; dragKey = null
                  if (to >= 0 && to != dragStart) a.moveQueue(cur + 1 + dragStart, cur + 1 + to)
                  dragList = null
                  scope.launch { settle.snapTo(rest); settle.animateTo(0f, QueueMotion.settle()); if (settlingKey == e.key) settlingKey = null }
                }
              })
          }
        }
      }
    }
  }
}

@Composable private fun QueueRow(h: HistoryItem, lifted: Boolean, modifier: Modifier) {
  val c = MaterialTheme.colorScheme
  val lift by animateFloatAsState(if (lifted) 1f else 0f, QueueMotion.lift(), label = "lift")
  Row(modifier.fillMaxWidth().height(QueueMotion.RowHeight)
      .graphicsLayer { val l = lift; scaleX = 1f + .035f * l; scaleY = scaleX
        shadowElevation = 18.dp.toPx() * l.coerceIn(0f, 1f); shape = RoundedCornerShape(16.dp); clip = l > 0.01f }
      .background(c.surfaceContainerLow).padding(horizontal = 24.dp), verticalAlignment = Alignment.CenterVertically) {
    Cover(h, Modifier.size(48.dp), 8.dp); Spacer(Modifier.width(14.dp))
    Column(Modifier.weight(1f)) {
      Text(h.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 15.sp)
      if (h.artist.isNotBlank()) Text(h.artist, color = c.onSurfaceVariant, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
    Icon(Icons.Rounded.DragHandle, null, tint = c.onSurfaceVariant.copy(alpha = .6f))
  }
}

/** Behind a swiped row: → tocar em seguida (neutral), ← remover (red). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun SwipeHint(st: SwipeToDismissBoxState) {
  val dir = st.dismissDirection
  val next = dir == SwipeToDismissBoxValue.StartToEnd
  val bg = when (dir) { SwipeToDismissBoxValue.EndToStart -> DangerRed; SwipeToDismissBoxValue.StartToEnd -> MaterialTheme.colorScheme.inverseSurface; else -> Color.Transparent }
  val ink = if (next) MaterialTheme.colorScheme.inverseOnSurface else Color.White
  Row(Modifier.fillMaxSize().clip(RoundedCornerShape(16.dp)).background(bg).padding(horizontal = 24.dp),
    horizontalArrangement = if (next) Arrangement.Start else Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
    if (dir != SwipeToDismissBoxValue.Settled) {
      Icon(if (next) Icons.Rounded.QueuePlayNext else Icons.Rounded.DeleteOutline, null, tint = ink); Spacer(Modifier.width(10.dp))
      Text(if (next) "Tocar em seguida" else "Remover", color = ink, fontWeight = FontWeight.SemiBold)
    }
  }
}
