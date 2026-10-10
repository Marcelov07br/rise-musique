package com.rise.musique.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import coil.compose.AsyncImage

/**
 * Download page: everything about saving files lives here (Search is only for finding and playing).
 * Selected item(s) → type + cut → format/quality → folder → Baixar; then the queue and recent downloads.
 */
@Composable fun DownloadPage(s: UiState, a: Actions, list: LazyListState, back: () -> Unit, modifier: Modifier) {
  val c = MaterialTheme.colorScheme
  val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
  val keyboard = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
  Column(modifier) {
    Row(Modifier.fillMaxWidth().height(TopBarHeight).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
      IconButton(back) { Icon(Icons.Rounded.ArrowBack, "Voltar") }
      Text("Downloads", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
      val active = s.queue.count { it.error == null && !it.completed }
      if (active > 0) Text("$active na fila", color = c.onSurfaceVariant, fontSize = 13.sp, modifier = Modifier.padding(end = 12.dp))
    }
    LazyColumn(Modifier.weight(1f).padding(horizontal = 20.dp), state = list, verticalArrangement = Arrangement.spacedBy(12.dp),
      contentPadding = PaddingValues(top = 4.dp, bottom = bottomSpace(s))) {
      item(key = "link") {
        Row(Modifier.fillMaxWidth().height(52.dp).clip(CircleShape).background(c.surfaceVariant).padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
          Icon(Icons.Rounded.Link, null, tint = c.onSurfaceVariant)
          TextField(s.dlQuery, a.dlQuery, Modifier.weight(1f), singleLine = true, placeholder = { Text("Cole um link do YouTube", color = c.onSurfaceVariant, maxLines = 1) },
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Go),
            keyboardActions = androidx.compose.foundation.text.KeyboardActions(onGo = { keyboard?.hide(); a.dlLink(s.dlQuery) }),
            colors = TextFieldDefaults.colors(focusedContainerColor = c.surfaceVariant, unfocusedContainerColor = c.surfaceVariant, focusedIndicatorColor = c.surfaceVariant, unfocusedIndicatorColor = c.surfaceVariant))
          if (s.dlLoading) CircularProgressIndicator(Modifier.size(44.dp).padding(12.dp), strokeWidth = 2.dp, color = c.onSurface)
          else if (s.dlQuery.isBlank()) TextButton({ clipboard.getText()?.text?.let { a.dlQuery(it); a.dlLink(it) } }) { Text("Colar") }
          else IconButton({ keyboard?.hide(); a.dlLink(s.dlQuery) }) { Icon(Icons.Rounded.ArrowForward, "Abrir link") }
        }
      }
      if (s.dlItems.isEmpty()) item(key = "empty") {
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(c.surfaceVariant.copy(alpha = .5f)).padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
          Text("Nada selecionado", style = MaterialTheme.typography.titleMedium)
          Text("Cole um link acima, ou toque no botão de download de um resultado em Buscar.", color = c.onSurfaceVariant, fontSize = 13.sp)
          TextButton({ a.page("Buscar") }, contentPadding = PaddingValues(0.dp)) { Text("Ir para Buscar") }
        }
      } else {
        items(s.dlItems.take(4), key = { "sel:" + it.url }) { r -> SelectedCard(r, s.dlItems.size > 1) { a.dlRemove(r) } }
        if (s.dlItems.size > 4) item(key = "more") { Text("+ ${s.dlItems.size - 4} itens desta playlist", color = c.onSurfaceVariant, fontSize = 13.sp) }
        item(key = "opts") { DownloadOptions(s, a, capped = false) }
        item(key = "go") { PillButton(if (s.dlItems.size > 1) "Baixar ${s.dlItems.size} itens" else "Baixar", a.download, Modifier.fillMaxWidth(), Icons.Rounded.Download,
          enabled = s.choices.ready(s.mode, s.format)) }
      }
      item(key = "wifi") { Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).clickable { a.wifiOnly(!s.wifiOnly) }.padding(vertical = 4.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Rounded.Wifi, null, tint = c.onSurfaceVariant); Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) { Text("Só no Wi-Fi"); Text("Downloads esperam uma rede Wi-Fi", color = c.onSurfaceVariant, fontSize = 12.sp) }
        Switch(s.wifiOnly, a.wifiOnly)
      } }
      queueItems(s, a)
      val recent = s.history.filter { !it.uri.startsWith("pl:") }.take(5)
      if (recent.isNotEmpty()) {
        item(key = "recent-title") { Text("Baixados recentemente", fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 6.dp)) }
        items(recent, key = { "rd:" + it.uri }) { h -> TrackRow(h, s, a) }
      }
    }
  }
}

@Composable private fun SelectedCard(r: SearchResult, removable: Boolean, remove: () -> Unit) {
  val c = MaterialTheme.colorScheme
  val tile = tileColor(r.thumb, "dl:" + r.url)
  val ink = contentFor(tile)
  Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(tile).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
    AsyncImage(r.thumb, null, Modifier.size(72.dp).clip(RoundedCornerShape(14.dp)).background(c.outline), contentScale = ContentScale.Crop)
    Spacer(Modifier.width(14.dp))
    Column(Modifier.weight(1f)) {
      Text(r.title, color = ink, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
      Text(listOf(r.channel, if (r.duration > 0) "%d:%02d".format(r.duration / 60, r.duration % 60) else "").filter { it.isNotBlank() }.joinToString(" · "),
        color = ink.copy(alpha = .7f), fontSize = 13.sp, maxLines = 1)
    }
    if (removable) IconButton(remove) { Icon(Icons.Rounded.Close, "Tirar ${r.title}", tint = ink) }
  }
}

/** The download queue (progress, cancel, retry) grouped by state. */
fun LazyListScope.queueItems(s: UiState, a: Actions) {
  listOf("Em andamento", "Precisam de atenção", "Concluídos").forEach { section ->
    val items = s.queue.filter { when (section) { "Em andamento" -> !it.completed && it.error == null; "Precisam de atenção" -> it.error != null; else -> it.completed } }.take(30)
    if (items.isNotEmpty()) item(key = "q-$section") { Text(section, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 6.dp)) }
    items(items, key = { it.id }) { q -> val c = MaterialTheme.colorScheme
      Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(c.surface).border(1.dp, c.outlineVariant, RoundedCornerShape(20.dp)).padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
          Text(q.title, maxLines = 2, overflow = TextOverflow.Ellipsis)
          Text(q.error ?: if (q.stage == "Baixando") "Baixando · ${(q.progress.coerceIn(0f, 1f) * 100).toInt()}%" else q.stage, color = c.onSurfaceVariant, fontSize = 12.sp)
          if (!q.completed && q.error == null) { Spacer(Modifier.height(10.dp))
            if (q.stage == "Baixando") LinearProgressIndicator({ q.progress.coerceIn(0f, 1f) }, Modifier.fillMaxWidth().clip(CircleShape))
            else if (q.running) LinearProgressIndicator(Modifier.fillMaxWidth().clip(CircleShape))
          }
        }
        if (q.error != null) IconButton({ a.retry(q) }) { Icon(Icons.Rounded.Refresh, "Tentar novamente") }
        IconButton({ if (q.completed || q.error != null) a.dismiss(q) else a.cancel(q) }) { Icon(if (q.completed) Icons.Rounded.Check else Icons.Rounded.Close, if (q.completed || q.error != null) "Limpar registro" else "Cancelar download") }
      }
    }
  }
}
