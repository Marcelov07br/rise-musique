package com.rise.musique.ui
import androidx.compose.ui.layout.layout

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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import coil.compose.AsyncImage

fun durationLabel(ms: Long): String = if (ms <= 0) "" else (ms / 1000).let { "%d:%02d".format(it / 60, it % 60) }

@Composable fun Cover(h: HistoryItem, modifier: Modifier, radius: androidx.compose.ui.unit.Dp = 14.dp) {
  Box(modifier.clip(RoundedCornerShape(radius)).background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
    if (h.art.isNotBlank()) AsyncImage(h.art, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
    else Icon(if (h.video) Icons.Rounded.Movie else Icons.Rounded.MusicNote, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
  }
}

@Composable fun LibraryHome(s: UiState, a: Actions, modifier: Modifier, list: androidx.compose.foundation.lazy.LazyListState = androidx.compose.foundation.lazy.rememberLazyListState(),
                            rows: Pair<androidx.compose.foundation.lazy.LazyListState, androidx.compose.foundation.lazy.LazyListState>? = null) {
  val c = MaterialTheme.colorScheme
  val shown = visibleHistory(s)
  var importMenu by remember { mutableStateOf(false) }
  var sortMenu by remember { mutableStateOf(false) }
  var rename by remember { mutableStateOf<String?>(null) }
  var deletePlaylist by remember { mutableStateOf<String?>(null) }
  val albumRow = rows?.first ?: androidx.compose.foundation.lazy.rememberLazyListState()
  val playlistRow = rows?.second ?: androidx.compose.foundation.lazy.rememberLazyListState()
  LazyColumn(modifier.padding(horizontal = 24.dp), state = list, verticalArrangement = Arrangement.spacedBy(18.dp), contentPadding = PaddingValues(top = 16.dp, bottom = if (s.now != null) 104.dp else 24.dp)) {
    item { Row(verticalAlignment = Alignment.CenterVertically) {
      Column(Modifier.weight(1f)) {
        Text(when (s.historyView) { "Tudo" -> "Ouvir agora"; else -> s.historyView }, style = MaterialTheme.typography.headlineLarge)
        Text("${s.history.size} arquivos · sua coleção", color = c.onSurfaceVariant, fontSize = 13.sp)
      }
      Box {
        if (s.importing) CircularProgressIndicator(Modifier.size(44.dp).padding(10.dp), strokeWidth = 2.dp)
        else RoundIconButton(Icons.Rounded.Add, "Importar arquivos locais", { importMenu = true })
        DropdownMenu(importMenu, { importMenu = false }) {
          DropdownMenuItem({ Text("Escolher arquivos") }, { importMenu = false; a.importFiles() }, leadingIcon = { Icon(Icons.Rounded.AudioFile, null) })
          DropdownMenuItem({ Text("Escolher uma pasta") }, { importMenu = false; a.importFolder() }, leadingIcon = { Icon(Icons.Rounded.FolderOpen, null) })
        }
      }
    } }
    item { OutlinedTextField(s.historyFilter, a.historyFilter, Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(20.dp),
      placeholder = { Text("Buscar na sua coleção") }, leadingIcon = { Icon(Icons.Rounded.Search, null) },
      trailingIcon = { if (s.historyFilter.isNotBlank()) IconButton({ a.historyFilter("") }) { Icon(Icons.Rounded.Close, "Limpar busca") } }) }
    item { LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      items(listOf("Tudo", "Favoritos") + s.playlists.keys.sorted()) { name ->
        var menu by remember { mutableStateOf(false) }
        Box {
          FilterChip(s.historyView == name, { a.historyView(name) }, label = { Text(name) },
            trailingIcon = { if (name in s.playlists) Icon(Icons.Rounded.MoreHoriz, "Opções da playlist", Modifier.size(20.dp).clickable { menu = true }) })
          DropdownMenu(menu, { menu = false }) {
            DropdownMenuItem({ Text("Renomear") }, { menu = false; rename = name })
            DropdownMenuItem({ Text("Apagar playlist") }, { menu = false; deletePlaylist = name })
          }
        }
      }
    } }
    item { Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
      listOf("Tudo", "Músicas", "Vídeos").forEach { filter ->
        TextButton({ a.mediaFilter(filter) }, contentPadding = PaddingValues(horizontal = 10.dp)) {
          Text(filter, color = if (s.mediaFilter == filter) c.onSurface else c.onSurfaceVariant, fontSize = 13.sp)
        }
      }
      Spacer(Modifier.weight(1f))
      Box {
        IconButton({ sortMenu = true }, enabled = s.historyView !in s.playlists) { Icon(Icons.Rounded.Sort, "Ordenar: ${s.sort}") }
        DropdownMenu(sortMenu, { sortMenu = false }) {
          listOf("Recentes", "Título", "Artista").forEach { order -> DropdownMenuItem({ Text(order) }, { a.sort(order); sortMenu = false }) }
        }
      }
    } }
    if (s.albumFilter.isNotBlank()) item { InputChip(true, { a.albumFilter("") }, label = { Text(s.albumFilter) }, trailingIcon = { Icon(Icons.Rounded.Close, "Limpar álbum") }) }
    if (shown.isEmpty()) item {
      Column(Modifier.fillMaxWidth().padding(vertical = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(Icons.Rounded.LibraryMusic, null, Modifier.size(48.dp), tint = c.onSurfaceVariant)
        Text(when { s.historyFilter.isNotBlank() || s.albumFilter.isNotBlank() -> "Nenhum arquivo encontrado"; s.historyView == "Favoritos" -> "Suas preferidas ficam aqui"; s.historyView in s.playlists -> "Esta playlist está vazia"; else -> "Sua coleção começa aqui" }, style = MaterialTheme.typography.titleLarge)
        Text("Importe músicas do celular ou encontre algo em Buscar. O coração guarda suas favoritas.", color = c.onSurfaceVariant)
        PillButton("Importar músicas", a.importFiles, icon = Icons.Rounded.Add, enabled = !s.importing)
        TextButton({ a.page("Buscar") }) { Text("Encontrar uma música") }
      }
    }
    val home = s.historyView == "Tudo" && s.historyFilter.isBlank() && s.albumFilter.isBlank() && s.sort == "Recentes"
    val recent = if (home) shown.take(6) else shown
    if (shown.isNotEmpty()) item { Text(if (home) "Recentes" else "${shown.size} arquivos", style = MaterialTheme.typography.titleLarge) }
    items(recent, key = { it.uri }) { h -> TrackRow(h, s, a) }
    val albums = if (home) shown.filter { !it.video && it.album.isNotBlank() }.groupBy { it.album to it.artist }.entries.take(20) else emptyList()
    if (albums.isNotEmpty()) {
      item(key = "albums-title") { Text("Álbuns e singles", style = MaterialTheme.typography.titleLarge) }
      item(key = "albums") { Carousel(albumRow) {
        items(albums, key = { it.key.first + "|" + it.key.second }) { entry -> val h = entry.value.first()
          CoverCard(h, h.album, h.artist.ifBlank { "${entry.value.size} faixas" }, "cover-al-${h.album}") { a.albumFilter(h.album) } }
      } }
    }
    val lists = if (home) s.playlists.entries.sortedBy { it.key }.map { (name, uris) -> Triple(name, uris.size, uris.firstNotNullOfOrNull { u -> s.history.firstOrNull { it.uri == u && it.art.isNotBlank() } }) } else emptyList()
    if (lists.isNotEmpty()) {
      item(key = "lists-title") { Text("Suas playlists", style = MaterialTheme.typography.titleLarge) }
      item(key = "lists") { Carousel(playlistRow) {
        items(lists, key = { it.first }) { (name, n, cover) ->
          CoverCard(cover ?: HistoryItem(name, "", "pl:$name"), name, "$n ${if (n == 1) "faixa" else "faixas"}", "cover-pl-$name") { a.historyView(name) } }
      } }
    }
    if (home && shown.size > 6) {
      item { Text("Sua biblioteca", style = MaterialTheme.typography.titleLarge) }
      items(shown.drop(6), key = { it.uri }) { h -> TrackRow(h, s, a) }
    }
  }
  rename?.let { old -> var name by remember(old) { mutableStateOf(old) }
    AlertDialog({ rename = null }, title = { Text("Renomear playlist") }, text = { OutlinedTextField(name, { name = it.take(80) }, singleLine = true) },
      confirmButton = { TextButton({ a.renamePlaylist(old, name); rename = null }, enabled = name.isNotBlank()) { Text("Salvar") } },
      dismissButton = { TextButton({ rename = null }) { Text("Cancelar") } })
  }
  deletePlaylist?.let { name -> AlertDialog({ deletePlaylist = null }, title = { Text("Apagar $name?") }, text = { Text("Os arquivos de música serão preservados.") },
    confirmButton = { TextButton({ a.deletePlaylist(name); deletePlaylist = null }) { Text("Apagar playlist") } }, dismissButton = { TextButton({ deletePlaylist = null }) { Text("Cancelar") } }) }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable internal fun TrackRow(h: HistoryItem, s: UiState, a: Actions, number: Int? = null) {
  val c = MaterialTheme.colorScheme
  var menu by remember(h.uri) { mutableStateOf(false) }
  var pick by remember(h.uri) { mutableStateOf(false) }
  var delete by remember(h.uri) { mutableStateOf(false) }
  Box {
    val press = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    Row(Modifier.fillMaxWidth().weightedPress(press, 16.dp).clip(RoundedCornerShape(16.dp)).combinedClickable(press, null, onClick = { a.play(h) }, onLongClick = { menu = true }).padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
      if (number != null) Text("$number", Modifier.width(36.dp), color = c.onSurfaceVariant, fontSize = 15.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
      else Cover(h, Modifier.size(60.dp))
      Spacer(Modifier.width(14.dp))
      Column(Modifier.weight(1f)) {
        Text(h.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
        val second = h.artist.ifBlank { h.meta.substringBefore(" ·") }
        Text(second, color = c.onSurfaceVariant, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        val detail = listOf(durationLabel(h.duration), h.meta.substringBefore(" ·")).filter { it.isNotBlank() && it != second }.distinct().joinToString(" · ")
        if (detail.isNotBlank()) Text(detail, color = c.onSurfaceVariant, fontSize = 11.sp)
      }
      if (h.uri in s.favorites) Icon(Icons.Rounded.Favorite, null, Modifier.size(14.dp), tint = c.onSurfaceVariant)
      if (s.historyView in s.playlists) {
        val order = s.playlists[s.historyView].orEmpty(); val index = order.indexOf(h.uri)
        Column {
          IconButton({ a.movePlaylist(s.historyView, index, index - 1) }, Modifier.size(48.dp), enabled = index > 0) { Icon(Icons.Rounded.KeyboardArrowUp, "Subir na playlist") }
          IconButton({ a.movePlaylist(s.historyView, index, index + 1) }, Modifier.size(48.dp), enabled = index in 0 until order.lastIndex) { Icon(Icons.Rounded.KeyboardArrowDown, "Descer na playlist") }
        }
      }
      IconButton({ menu = true }) { Icon(Icons.Rounded.MoreVert, "Opções de ${h.title}", tint = c.onSurfaceVariant) }
    }
    DropdownMenu(menu, { menu = false }) {
      DropdownMenuItem({ Text(if (h.uri in s.favorites) "Remover dos favoritos" else "Favoritar") }, { menu = false; a.favorite(h) }, leadingIcon = { Icon(Icons.Rounded.FavoriteBorder, null) })
      DropdownMenuItem({ Text("Tocar a seguir") }, { menu = false; a.playNext(h) }, leadingIcon = { Icon(Icons.Rounded.QueuePlayNext, null) })
      DropdownMenuItem({ Text("Adicionar à fila") }, { menu = false; a.addQueue(h) })
      DropdownMenuItem({ Text("Adicionar à playlist") }, { menu = false; pick = true })
      if (!h.video) DropdownMenuItem({ Text("Rádio desta música") }, { menu = false; a.radioFor(h) })
      if (s.historyView in s.playlists) DropdownMenuItem({ Text("Tirar desta playlist") }, { menu = false; a.removeFromPlaylist(h) })
      DropdownMenuItem({ Text("Compartilhar") }, { menu = false; a.share(h) })
      DropdownMenuItem({ Text("Abrir em outro app") }, { menu = false; a.open(h) })
      DropdownMenuItem({ Text(if (h.imported) "Remover da biblioteca" else "Apagar arquivo") }, { menu = false; delete = true })
    }
  }
  if (pick) PlaylistPicker(s.playlists.keys.sorted(), { pick = false }) { name -> a.addToPlaylist(h, name); pick = false }
  if (delete) AlertDialog({ delete = false }, title = { Text(if (h.imported) "Remover da biblioteca?" else "Apagar este arquivo?") },
    text = { Text(if (h.imported) "O arquivo original permanece no celular." else "O arquivo baixado será apagado do armazenamento.") },
    confirmButton = { TextButton({ delete = false; if (h.imported) a.removeLocal(h) else a.delete(h) }) { Text(if (h.imported) "Remover" else "Apagar") } },
    dismissButton = { TextButton({ delete = false }) { Text("Cancelar") } })
}

/** Horizontal row that bleeds to the screen edges and snaps cards to the left margin with natural deceleration. */
@Composable private fun Carousel(state: androidx.compose.foundation.lazy.LazyListState, content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit) {
  val bleed = 24.dp
  LazyRow(Modifier.layout { m, cs -> val p = bleed.roundToPx()
      val pl = m.measure(cs.copy(minWidth = cs.maxWidth + 2 * p, maxWidth = cs.maxWidth + 2 * p)); layout(cs.maxWidth, pl.height) { pl.place(-p, 0) } },
    state = state, contentPadding = PaddingValues(horizontal = bleed), horizontalArrangement = Arrangement.spacedBy(16.dp),
    flingBehavior = androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior(state, androidx.compose.foundation.gestures.snapping.SnapPosition.Start),
    content = content)
}

/** Flat cover card with weighted press; its cover is the shared element that becomes the collection header. */
@Composable private fun CoverCard(h: HistoryItem, title: String, subtitle: String, sharedKey: String, onClick: () -> Unit) {
  val c = MaterialTheme.colorScheme
  val src = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
  val haptics = rememberHaptics()
  val r = morphCorner(8.dp, 14.dp)
  Column(Modifier.width(156.dp).weightedPress(src, 14.dp).clickable(src, null) { haptics.tick(); onClick() }) {
    Cover(h, Modifier.size(156.dp).sharedCover(sharedKey), r?.value ?: 14.dp); Spacer(Modifier.height(8.dp))
    Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleMedium)
    Text(subtitle, color = c.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 13.sp)
  }
}
