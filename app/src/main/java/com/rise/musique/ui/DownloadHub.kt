package com.rise.musique.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*

@Composable fun DownloadHub(s: UiState, a: Actions, modifier: Modifier) {
  val c = MaterialTheme.colorScheme
  LazyColumn(modifier.padding(horizontal = 24.dp), verticalArrangement = Arrangement.spacedBy(14.dp), contentPadding = PaddingValues(top = 16.dp, bottom = if (s.now != null) 104.dp else 24.dp)) {
    item { Text("Downloads", style = MaterialTheme.typography.headlineLarge) }
    if (s.queue.isEmpty()) item {
      Text("Tudo pronto por aqui", style = MaterialTheme.typography.titleLarge)
      Text("Escolha uma música ou vídeo em Buscar. Os arquivos concluídos ficam na biblioteca.", color = c.onSurfaceVariant)
      Spacer(Modifier.height(16.dp)); PillButton("Buscar uma mídia", { a.page("Buscar") }, icon = Icons.Rounded.Search)
    }
    listOf("Em andamento", "Precisam de atenção", "Concluídos").forEach { section ->
      val items = s.queue.filter { when (section) { "Em andamento" -> !it.completed && it.error == null; "Precisam de atenção" -> it.error != null; else -> it.completed } }.take(30)
      if (items.isNotEmpty()) item { Text(section, style = MaterialTheme.typography.titleMedium) }
      items(items, key = { it.id }) { q ->
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(c.surface).border(1.dp, c.outlineVariant, RoundedCornerShape(22.dp)).padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
          Column(Modifier.weight(1f)) {
            Text(q.title, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(q.error ?: if (q.stage == "Baixando") "Baixando · ${(q.progress.coerceIn(0f, 1f) * 100).toInt()}%" else q.stage,
              color = c.onSurfaceVariant, fontSize = 12.sp)
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
}
