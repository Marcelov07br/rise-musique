package com.rise.musique.ui

import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun PlaybackQueueSheet(state: UiState, a: Actions) {
  val latest by rememberUpdatedState(state)
  val actions by rememberUpdatedState(a)
  val rowHeight = with(LocalDensity.current) { 88.dp.toPx() }
  ModalBottomSheet(onDismissRequest = { a.showQueue(false) }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).navigationBarsPadding()) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Text("Fila de reprodução", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
        if (state.playbackQueue.size > (state.now?.index ?: 0) + 1) TextButton({ a.clearQueue() }, Modifier.heightIn(min = 48.dp)) { Text("Limpar") }
      }
      if (state.playbackQueue.size <= 1) EmptyState(Icons.Rounded.QueueMusic, "Nada na fila depois desta", "Toque e segure uma faixa e escolha Tocar a seguir ou Adicionar à fila.")
      Text("Segure a alça e arraste. As setas também mudam a ordem.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
      LazyColumn(Modifier.fillMaxWidth().heightIn(max = 440.dp)) {
        itemsIndexed(state.playbackQueue) { index, h ->
          Row(Modifier.fillMaxWidth().height(88.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.DragHandle, "Segurar para reordenar ${h.title}", Modifier.size(44.dp).pointerInput(Unit) {
              var moving = index
              var distance = 0f
              detectDragGesturesAfterLongPress(onDragStart = { moving = index; distance = 0f }, onDrag = { change, amount ->
                change.consume(); distance += amount.y
                while (kotlin.math.abs(distance) >= rowHeight) {
                  val direction = if (distance > 0) 1 else -1
                  val target = moving + direction
                  if (target !in latest.playbackQueue.indices) { distance = 0f; break }
                  actions.moveQueue(moving, target); moving = target; distance -= direction * rowHeight
                }
              })
            })
            TextButton({ a.playQueue(index) }, Modifier.weight(1f)) {
              Column(Modifier.fillMaxWidth()) {
                Text(h.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(if (index == state.now?.index) "Tocando agora" else h.artist, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
              }
            }
            Column {
              IconButton({ a.moveQueue(index, index - 1) }, Modifier.size(40.dp), enabled = index > 0) { Icon(Icons.Rounded.KeyboardArrowUp, "Mover para cima") }
              IconButton({ a.moveQueue(index, index + 1) }, Modifier.size(40.dp), enabled = index < state.playbackQueue.lastIndex) { Icon(Icons.Rounded.KeyboardArrowDown, "Mover para baixo") }
            }
            IconButton({ a.removeQueue(index) }) { Icon(Icons.Rounded.Close, "Remover da fila") }
          }
        }
      }
      Spacer(Modifier.height(16.dp))
    }
  }
}
