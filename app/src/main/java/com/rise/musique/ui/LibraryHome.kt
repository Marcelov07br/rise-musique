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
                            rows: Pair<androidx.compose.foundation.lazy.LazyListState, androidx.compose.foundation.lazy.LazyListState>? = null,
                            homeTab: Boolean = false, artistRow: androidx.compose.foundation.lazy.LazyListState = androidx.compose.foundation.lazy.rememberLazyListState()) {
  val c = MaterialTheme.colorScheme
  val shown = visibleHistory(s)
  var importMenu by remember { mutableStateOf(false) }
  var sortMenu by remember { mutableStateOf(false) }
  var rename by remember { mutableStateOf<String?>(null) }
  var deletePlaylist by remember { mutableStateOf<String?>(null) }
  val albumRow = rows?.first ?: androidx.compose.foundation.lazy.rememberLazyListState()
  val smartRow = androidx.compose.foundation.lazy.rememberLazyListState()
  val playlistRow = rows?.second ?: androidx.compose.foundation.lazy.rememberLazyListState()
  val titleCollapse = rememberTitleCollapse(list)
  val heroRow = androidx.compose.foundation.lazy.rememberLazyListState()
  val recentRow = androidx.compose.foundation.lazy.rememberLazyListState()
  val stationRow = androidx.compose.foundation.lazy.rememberLazyListState()
  val stations = remember(s.history, s.stats, s.trackInfo, s.hidden) { com.rise.musique.Stations.build(s.history.filter { it.uri !in s.hidden }, s.stats, s.trackInfo) }
  LazyColumn(modifier.padding(horizontal = 24.dp), state = list, verticalArrangement = Arrangement.spacedBy(18.dp), contentPadding = PaddingValues(top = 16.dp, bottom = bottomSpace(s))) {
    item { Row(verticalAlignment = Alignment.CenterVertically) {
      Column(Modifier.weight(1f)) {
        LargeTitle(if (homeTab) "Ouvir agora" else if (s.historyView == "Favoritos") "Favoritos" else "Biblioteca", titleCollapse)
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
    if (!homeTab) item { OutlinedTextField(s.historyFilter, a.historyFilter, Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(20.dp),
      placeholder = { Text("Buscar na sua coleção") }, leadingIcon = { Icon(Icons.Rounded.Search, null) },
      trailingIcon = { if (s.historyFilter.isNotBlank()) IconButton({ a.historyFilter("") }) { Icon(Icons.Rounded.Close, "Limpar busca") } }) }
    if (!homeTab) item { LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      items(listOf("Tudo", "Favoritos") + s.playlists.keys.sorted()) { name ->
        var menu by remember { mutableStateOf(false) }
        Box {
          FilterChip(s.historyView == name, { if (name in s.playlists) a.openPlaylist(name) else a.historyView(name) }, label = { Text(name) },
            trailingIcon = { if (name in s.playlists) Icon(Icons.Rounded.MoreHoriz, "Opções da playlist", Modifier.size(20.dp).clickable { menu = true }) })
          DropdownMenu(menu, { menu = false }) {
            DropdownMenuItem({ Text("Renomear") }, { menu = false; rename = name })
            DropdownMenuItem({ Text("Apagar playlist") }, { menu = false; deletePlaylist = name })
          }
        }
      }
    } }
    if (!homeTab) item { Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
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
        val filtered = s.historyFilter.isNotBlank() || s.albumFilter.isNotBlank() || s.mediaFilter != "Tudo"
        Text(when { filtered -> "Nada com esse filtro"; s.historyView == "Favoritos" -> "Suas preferidas ficam aqui"; else -> "Sua coleção começa aqui" }, style = MaterialTheme.typography.titleLarge)
        Text(when { filtered -> "Tente outro termo ou limpe os filtros."
          s.historyView == "Favoritos" -> "Toque e segure uma faixa e escolha Favoritar."
          else -> "Importe músicas do celular ou encontre algo em Buscar." }, color = c.onSurfaceVariant)
        if (filtered) PillButton("Limpar filtros", { a.historyFilter(""); a.mediaFilter("Tudo") }, filled = false)
        else if (s.historyView == "Favoritos") PillButton("Ver tudo", { a.historyView("Tudo") }, filled = false)
        else { PillButton("Importar músicas", a.importFiles, icon = Icons.Rounded.Add, enabled = !s.importing)
          TextButton({ a.page("Buscar") }) { Text("Encontrar uma música") } }
      }
    }
    val home = homeTab
    val browse = home || (s.historyView == "Tudo" && s.historyFilter.isBlank() && s.mediaFilter == "Tudo")
    val recent = if (home) emptyList() else shown
    if (home && shown.isNotEmpty()) {
      item(key = "hero") { HeroRow(heroItems(s, shown, stations, a), heroRow, s, a) }
      item(key = "recent-title") { SectionTitle("Reproduções recentes") }
      item(key = "recent") { Carousel(recentRow) { items(shown.take(12), key = { "r:" + it.uri }) { h ->
        CoverCard(h, h.title, h.artist.ifBlank { h.meta }, "cover-tr-${h.uri}") { if (h.video) a.playLocalVideo(h) else a.play(h) } } } }
      if (stations.isNotEmpty()) {
        item(key = "stations-title") { SectionTitle("Estações feitas pra você") }
        item(key = "stations") { Carousel(stationRow) { items(stations, key = { it.id }) { st -> StationCard(st, s.stationLoading == st.id) { a.playStation(st) } } } }
      }
    }
    if (shown.isNotEmpty() && !home && !browse) item { Text("${shown.size} arquivos", style = MaterialTheme.typography.titleLarge) }
    if (!home) { // Biblioteca: álbuns e artistas no topo, depois todas as faixas
      AlbumAndArtistRows(shown.takeIf { browse }.orEmpty(), a, albumRow, artistRow, if (browse) smartFolders(s) else emptyList(), smartRow)
    }
    items(recent, key = { it.uri }) { h -> TrackRow(h, s, a) }
    if (home && shown.isNotEmpty()) item(key = "recap") { Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
      EntryCard("Sua retrospectiva", "O mês e o ano em música", Icons.Rounded.AutoAwesome, Modifier.weight(1f)) { a.recap(true) }
      EntryCard("Estatísticas", "O que você mais ouve", Icons.Rounded.BarChart, Modifier.weight(1f)) { a.stats(true) }
    } }
    val albums = if (home) shown.filter { !it.video && it.album.isNotBlank() }.groupBy { it.album to it.artist }.entries.take(20) else emptyList()
    if (albums.isNotEmpty()) {
      item(key = "albums-title") { SectionTitle("Álbuns e singles") }
      item(key = "albums") { Carousel(albumRow) {
        items(albums, key = { it.key.first + "|" + it.key.second }) { entry -> val h = entry.value.first()
          CoverCard(h, h.album, h.artist.ifBlank { "${entry.value.size} faixas" }, "cover-al-${h.album}") { a.openAlbum(h.album) } }
      } }
    }
    val lists = if (home) s.playlists.entries.sortedBy { it.key }.map { (name, uris) -> Triple(name, uris.size, uris.firstNotNullOfOrNull { u -> s.history.firstOrNull { it.uri == u && it.art.isNotBlank() } }) } else emptyList()
    if (lists.isNotEmpty()) {
      item(key = "lists-title") { SectionTitle("Suas playlists") }
      item(key = "lists") { Carousel(playlistRow) {
        items(lists, key = { it.first }) { (name, n, cover) ->
          CoverCard(cover ?: HistoryItem(name, "", "pl:$name"), name, "$n ${if (n == 1) "faixa" else "faixas"}", "cover-pl-$name") { a.openPlaylist(name) } }
      } }
    }
    if (home) {
      val artists = artistsOf(shown)
      if (artists.isNotEmpty()) {
        item(key = "artists-title") { SectionTitle("Artistas") }
        item(key = "artists") { Carousel(artistRow) { items(artists, key = { it.first }) { (name, h, n) ->
          CoverCard(h, name, "$n ${if (n == 1) "faixa" else "faixas"}", "cover-ar-$name", circle = true) { a.openArtist(name) } } } }
      }
      if (shown.size > 6) item(key = "all") { PillButton("Ver a biblioteca inteira", { a.page("Biblioteca") }, Modifier.fillMaxWidth(), filled = false) }
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
  Box {
    val press = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    Row(Modifier.fillMaxWidth().weightedPress(press, 16.dp).clip(RoundedCornerShape(16.dp)).combinedClickable(press, null, onClick = { if (h.video) a.playLocalVideo(h) else a.play(h) }, onLongClick = { menu = true }).padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
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
  }
  if (menu) TrackActionSheet(h, s, a, { menu = false }, { pick = true })
  if (pick) PlaylistPicker(s.playlists.keys.sorted(), { pick = false }) { name -> a.addToPlaylist(h, name); pick = false }
}

/** Horizontal row that bleeds to the screen edges and snaps cards to the left margin with natural deceleration. */
@Composable internal fun Carousel(state: androidx.compose.foundation.lazy.LazyListState, content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit) {
  val bleed = 24.dp
  LazyRow(Modifier.layout { m, cs -> val p = bleed.roundToPx()
      val pl = m.measure(cs.copy(minWidth = cs.maxWidth + 2 * p, maxWidth = cs.maxWidth + 2 * p)); layout(cs.maxWidth, pl.height) { pl.place(-p, 0) } },
    state = state, contentPadding = PaddingValues(horizontal = bleed), horizontalArrangement = Arrangement.spacedBy(16.dp),
    flingBehavior = androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior(state, androidx.compose.foundation.gestures.snapping.SnapPosition.Start),
    content = content)
}

/** Flat cover card with weighted press; its cover is the shared element that becomes the collection header. */
@Composable internal fun CoverCard(h: HistoryItem, title: String, subtitle: String, sharedKey: String, circle: Boolean = false, onClick: () -> Unit) {
  val c = MaterialTheme.colorScheme
  val src = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
  val haptics = rememberHaptics()
  val r = morphCorner(8.dp, 14.dp)
  // Soft per-cover tint (or neutral) as the tile behind the text; artists stay plain circles.
  val tile = if (circle) androidx.compose.ui.graphics.Color.Transparent else tileColor(h.art, sharedKey)
  val ink = if (circle) c.onSurface else contentFor(tile)
  Column(Modifier.width(156.dp).weightedPress(src, 16.dp).clip(RoundedCornerShape(16.dp)).background(tile).clickable(src, null) { haptics.tick(); onClick() }
      .padding(bottom = if (circle) 0.dp else 10.dp)) {
    Cover(h, Modifier.size(156.dp).sharedCover(sharedKey), if (circle) 78.dp else r?.value ?: 14.dp); Spacer(Modifier.height(8.dp))
    Column(Modifier.padding(horizontal = if (circle) 0.dp else 10.dp)) {
      Text(title, Modifier.fillMaxWidth(), textAlign = if (circle) androidx.compose.ui.text.style.TextAlign.Center else null, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleMedium, color = ink)
      Text(subtitle, Modifier.fillMaxWidth(), textAlign = if (circle) androidx.compose.ui.text.style.TextAlign.Center else null, color = ink.copy(alpha = .68f), maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 13.sp)
    }
  }
}

/** Artists from local tracks: (name, track with the best cover, count), most tracks first. */
fun artistsOf(list: List<HistoryItem>): List<Triple<String, HistoryItem, Int>> =
  list.filter { !it.video && it.artist.isNotBlank() }.groupBy { it.artist }.map { (n, t) -> Triple(n, t.firstOrNull { it.art.isNotBlank() } ?: t.first(), t.size) }
    .sortedByDescending { it.third }.take(20)

private fun androidx.compose.foundation.lazy.LazyListScope.AlbumAndArtistRows(shown: List<HistoryItem>, a: Actions,
    albumRow: androidx.compose.foundation.lazy.LazyListState, artistRow: androidx.compose.foundation.lazy.LazyListState,
    smart: List<SmartFolder> = emptyList(), smartRow: androidx.compose.foundation.lazy.LazyListState? = null) {
  if (smart.isNotEmpty() && smartRow != null) {
    item(key = "lib-smart-title") { Text("Coleções inteligentes", style = MaterialTheme.typography.titleLarge) }
    item(key = "lib-smart") { Carousel(smartRow) { items(smart, key = { it.key }) { f ->
      CoverCard(f.tracks.firstOrNull { it.art.isNotBlank() } ?: f.tracks.first(), f.value, "${f.kind} · ${f.tracks.size} faixas", f.key) { a.openSmart(f.kind, f.value) } } } }
  }
  val albums = shown.filter { !it.video && it.album.isNotBlank() }.groupBy { it.album to it.artist }.entries.take(20)
  if (albums.isNotEmpty()) {
    item(key = "lib-albums-title") { Text("Álbuns", style = MaterialTheme.typography.titleLarge) }
    item(key = "lib-albums") { Carousel(albumRow) { items(albums, key = { it.key.first + "|" + it.key.second }) { e -> val h = e.value.first()
      CoverCard(h, h.album, h.artist.ifBlank { "${e.value.size} faixas" }, "cover-al-${h.album}") { a.openAlbum(h.album) } } } }
  }
  val artists = artistsOf(shown)
  if (artists.isNotEmpty()) {
    item(key = "lib-artists-title") { Text("Artistas", style = MaterialTheme.typography.titleLarge) }
    item(key = "lib-artists") { Carousel(artistRow) { items(artists, key = { it.first }) { (name, h, n) ->
      CoverCard(h, name, "$n ${if (n == 1) "faixa" else "faixas"}", "cover-ar-$name", circle = true) { a.openArtist(name) } } } }
  }
  if (shown.isNotEmpty()) item(key = "lib-tracks-title") { Text("Faixas", style = MaterialTheme.typography.titleLarge) }
}

@Composable private fun EntryCard(title: String, sub: String, icon: androidx.compose.ui.graphics.vector.ImageVector, m: Modifier, onClick: () -> Unit) {
  val c = MaterialTheme.colorScheme
  val src = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
  Column(m.weightedPress(src, 20.dp).clip(RoundedCornerShape(20.dp)).background(c.surfaceVariant).clickable(src, null, onClick = onClick).padding(16.dp),
    verticalArrangement = Arrangement.spacedBy(4.dp)) {
    Icon(icon, null, tint = c.onSurface); Spacer(Modifier.height(4.dp))
    Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
    Text(sub, color = c.onSurfaceVariant, fontSize = 12.sp, maxLines = 2)
  }
}
