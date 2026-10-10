package com.rise.musique.ui
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import coil.compose.AsyncImage
import androidx.compose.ui.platform.LocalDensity
import kotlinx.coroutines.launch

enum class Mode(val label: String, val qualities: List<String>) {
  Musica("Música", listOf("128k", "192k", "320k")), Video("Vídeo", listOf("480p", "720p", "1080p", "Máx")) }
data class HistoryItem(val title: String, val meta: String, val uri: String = "", val art: String = "", val artist: String = "")
data class NowPlaying(val key: String, val title: String, val artist: String, val art: String, val playing: Boolean = false,
  val loading: Boolean = false, val position: Long = 0, val duration: Long = 0, val isPreview: Boolean = false,
  val hasNext: Boolean = false, val hasPrev: Boolean = false, val shuffle: Boolean = false, val index: Int = 0, val count: Int = 1,
  val lyrics: List<com.rise.musique.Lyrics.Line>? = null, val lyricsState: String = "")
data class SearchResult(val url: String, val title: String, val channel: String, val duration: Int, val thumb: String)
data class FlyEvent(val id: Long, val art: String)
class FlyTargets { var history by mutableStateOf(androidx.compose.ui.geometry.Offset.Zero); var source by mutableStateOf(androidx.compose.ui.geometry.Offset.Zero)
  val bounce = androidx.compose.animation.core.Animatable(1f) }
data class QueueItem(val id: String, val title: String, val progress: Float, val running: Boolean, val error: String? = null)
data class UiState(val query: String = "", val mode: Mode = Mode.Musica, val quality: String = "320k",
  val folder: android.net.Uri? = null, val status: String = "", val history: List<HistoryItem> = emptyList(),
  val searching: Boolean = false, val results: List<SearchResult> = emptyList(), val selected: Set<String> = emptySet(),
  val isPlaylist: Boolean = false, val format: String = "MP3", val cutOn: Boolean = false, val cutStart: String = "", val cutEnd: String = "",
  val previewing: String? = null, val previewLoading: Boolean = false, val now: NowPlaying? = null, val fly: FlyEvent? = null,
  val favorites: Set<String> = emptySet(), val playlists: Map<String, List<String>> = emptyMap(), val historyFilter: String = "",
  val historyView: String = "Tudo", val sleepAt: Long = 0, val queue: List<QueueItem> = emptyList())
class Actions(val query: (String) -> Unit = {}, val mode: (Mode) -> Unit = {}, val quality: (String) -> Unit = {},
  val pickFolder: () -> Unit = {}, val download: () -> Unit = {}, val toggleTheme: () -> Unit = {},
  val search: () -> Unit = {}, val select: (SearchResult) -> Unit = {}, val preview: (SearchResult) -> Unit = {},
  val selectAll: () -> Unit = {}, val format: (String) -> Unit = {}, val cut: (Boolean, String, String) -> Unit = { _, _, _ -> },
  val cancel: (QueueItem) -> Unit = {}, val retry: (QueueItem) -> Unit = {}, val dismiss: (QueueItem) -> Unit = {},
  val play: (HistoryItem) -> Unit = {}, val playPause: () -> Unit = {}, val seek: (Long) -> Unit = {}, val closePlayer: () -> Unit = {},
  val next: () -> Unit = {}, val prev: () -> Unit = {}, val shuffle: () -> Unit = {},
  val favorite: (HistoryItem) -> Unit = {}, val addToPlaylist: (HistoryItem, String) -> Unit = { _, _ -> }, val removeFromPlaylist: (HistoryItem) -> Unit = {},
  val deletePlaylist: (String) -> Unit = {}, val historyFilter: (String) -> Unit = {}, val historyView: (String) -> Unit = {},
  val exportBackup: () -> Unit = {}, val importBackup: () -> Unit = {}, val sleep: (Int) -> Unit = {},
  val open: (HistoryItem) -> Unit = {}, val share: (HistoryItem) -> Unit = {}, val delete: (HistoryItem) -> Unit = {}, val updates: () -> Unit = {})
val audioFormats = listOf("MP3", "M4A", "Opus", "FLAC")

val sampleHistory = listOf(HistoryItem("Faixa de exemplo", "MP3 · 320k · 8,4 MB"),
  HistoryItem("Vídeo de exemplo", "MP4 · 1080p · 142 MB"), HistoryItem("Outra faixa", "M4A · 256k · 6,1 MB"))
val sampleResults = listOf(SearchResult("a", "Música de exemplo (Clipe Oficial)", "Artista Exemplo", 214, ""),
  SearchResult("b", "Música de exemplo (Ao Vivo)", "Canal Exemplo", 251, ""),
  SearchResult("c", "Música de exemplo · versão acústica", "Outro Canal", 189, ""))

@Composable fun RiseApp(state: UiState, actions: Actions, initialHistoryOpen: Boolean = false, initialExpanded: Boolean = false, initialLyrics: Boolean = false) {
  val targets = remember { FlyTargets() }
  Box(Modifier.fillMaxSize()) {
    RiseContent(state, actions, initialHistoryOpen, targets)
    state.fly?.let { FlyOverlay(it, targets) }
    state.now?.let { CompositionLocalProvider(LocalSleep provides state.sleepAt) { PlayerSheet(it, actions, initialExpanded, initialLyrics) } }
  }
}

@Composable private fun RiseContent(state: UiState, actions: Actions, initialHistoryOpen: Boolean, targets: FlyTargets) {
  BoxWithConstraints(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).safeDrawingPadding()) {
    if (maxWidth >= 600.dp) Row(Modifier.fillMaxSize()) {
      HistoryPanel(state, actions, Modifier.width(320.dp).fillMaxHeight().background(MaterialTheme.colorScheme.surface))
      MainPane(state, actions, targets, null, Modifier.weight(1f).widthIn(max = 680.dp))
    } else {
      val drawer = rememberDrawerState(if (initialHistoryOpen) DrawerValue.Open else DrawerValue.Closed)
      val scope = rememberCoroutineScope()
      ModalNavigationDrawer(drawerState = drawer, drawerContent = {
        ModalDrawerSheet(drawerContainerColor = MaterialTheme.colorScheme.surface) { HistoryPanel(state, actions, Modifier) } }) {
        MainPane(state, actions, targets, { scope.launch { drawer.open() } }, Modifier.fillMaxSize())
      }
    }
  }
}

@Composable private fun MainPane(state: UiState, a: Actions, targets: FlyTargets, onHistory: (() -> Unit)?, modifier: Modifier) {
  val c = MaterialTheme.colorScheme
  Column(modifier.background(c.background).padding(horizontal = 24.dp, vertical = 20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      if (onHistory != null) { BadgedBox({ if (state.queue.isNotEmpty()) Badge { AnimatedContent(state.queue.size, label = "n",
            transitionSpec = { (slideInVertically { it } + fadeIn()) togetherWith (slideOutVertically { -it } + fadeOut()) }) { Text("$it") } } },
          Modifier.graphicsLayer { scaleX = targets.bounce.value; scaleY = targets.bounce.value }
            .onGloballyPositioned { targets.history = it.positionInRoot() + androidx.compose.ui.geometry.Offset(it.size.width / 2f, it.size.height / 2f) }) {
        RoundIconButton(Icons.Rounded.History, "Histórico", onHistory) }; Spacer(Modifier.width(14.dp)) }
      Text("RISE MUSIQUE", Modifier.weight(1f), color = c.onSurface, fontWeight = FontWeight.Light, letterSpacing = 4.sp, fontSize = 18.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
      RoundIconButton(Icons.Rounded.Contrast, "Tema", a.toggleTheme)
      RoundIconButton(Icons.Rounded.SystemUpdate, "Verificar atualizações", a.updates)
    }
    Row(Modifier.fillMaxWidth().clip(CircleShape).background(c.surfaceVariant).padding(start = 18.dp, end = 6.dp, top = 4.dp, bottom = 4.dp),
      verticalAlignment = Alignment.CenterVertically) {
      Icon(Icons.Rounded.Search, null, tint = c.onSurfaceVariant)
      TextField(state.query, a.query, Modifier.weight(1f), singleLine = true,
        placeholder = { Text("Link ou nome da música", color = c.onSurfaceVariant) },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search), keyboardActions = KeyboardActions(onSearch = { a.search() }),
        colors = TextFieldDefaults.colors(focusedContainerColor = c.surfaceVariant, unfocusedContainerColor = c.surfaceVariant,
          focusedIndicatorColor = c.surfaceVariant, unfocusedIndicatorColor = c.surfaceVariant))
      if (state.searching) CircularProgressIndicator(Modifier.size(44.dp).padding(10.dp), strokeWidth = 2.dp, color = c.onSurface)
      else RoundIconButton(Icons.Rounded.ArrowForward, "Buscar", a.search, filled = state.query.isNotBlank())
    }
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
      PillSelector(Mode.entries, state.mode, { it.label }, a.mode, Modifier.weight(1f))
      PillButton(state.folder?.lastPathSegment?.substringAfter(':')?.ifBlank { "Raiz" } ?: "Pasta", a.pickFolder,
        Modifier.weight(1f), Icons.Rounded.FolderOpen, filled = false)
    }
    if (state.mode == Mode.Musica) PillSelector(audioFormats, state.format, { it }, a.format, Modifier.fillMaxWidth())
    if (state.mode == Mode.Musica && state.format == "FLAC")
      Text("FLAC preserva o áudio obtido; não recupera qualidade perdida na fonte.", color = c.onSurfaceVariant, fontSize = 12.sp, modifier = Modifier.padding(start = 8.dp))
    else PillSelector(state.mode.qualities, state.quality, { it }, a.quality, Modifier.fillMaxWidth())
    if (state.results.size > 1 && state.isPlaylist) Row(verticalAlignment = Alignment.CenterVertically) {
      Text("${state.results.size} faixas · ${state.selected.size} marcadas", Modifier.weight(1f), color = c.onSurfaceVariant, fontSize = 13.sp)
      PillButton(if (state.selected.size == state.results.size) "Desmarcar" else "Marcar todas", a.selectAll, filled = false)
    }

    LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
      itemsIndexed(state.results, key = { _, it -> it.url }) { i, r ->
        Box(Modifier.cascade(i)) {
        ResultCard(r, r.url in state.selected, state.isPlaylist, state.previewing == r.url, state.previewing == r.url && state.previewLoading,
          { a.select(r) }, { a.preview(r) }) }
      }
      if (state.results.isEmpty() && !state.searching) item {
        Text("Cole um link ou digite um nome e toque na seta.", color = c.onSurfaceVariant, fontSize = 13.sp,
          modifier = Modifier.padding(top = 24.dp).fillMaxWidth(), textAlign = androidx.compose.ui.text.style.TextAlign.Center) }
    }
    if (state.selected.size == 1) CutPanel(state, a)
    if (state.status.isNotBlank()) Text(state.status, color = c.onSurfaceVariant, fontSize = 12.sp, maxLines = 2)
    val active = state.queue.count { it.error == null }
    val active2 = 0
    PillButton(modifier = Modifier.fillMaxWidth().onGloballyPositioned { targets.source = it.positionInRoot() + androidx.compose.ui.geometry.Offset(it.size.width / 2f, it.size.height / 2f) },
      text = when { state.selected.size > 1 -> "Baixar ${state.selected.size} faixas"; active == 0 -> "Baixar"
        else -> "Adicionar à fila ($active)" }, onClick = a.download, icon = Icons.Rounded.Download, enabled = state.selected.isNotEmpty())
    if (state.now != null) Spacer(Modifier.height(76.dp))
  }
}

private fun fmt(s: Int) = if (s <= 0) "" else "%d:%02d".format(s / 60, s % 60)

@Composable private fun CutPanel(state: UiState, a: Actions) {
  val c = MaterialTheme.colorScheme
  Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(c.surfaceVariant).padding(horizontal = 16.dp, vertical = 8.dp)) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      Icon(Icons.Rounded.ContentCut, null, tint = c.onSurfaceVariant, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(10.dp))
      Text("Cortar trecho", Modifier.weight(1f), color = c.onSurface, fontSize = 14.sp)
      Switch(state.cutOn, { a.cut(it, state.cutStart, state.cutEnd) }, colors = SwitchDefaults.colors(checkedTrackColor = c.primary,
        checkedThumbColor = c.onPrimary, uncheckedTrackColor = c.background, uncheckedBorderColor = c.outline))
    }
    AnimatedVisibility(state.cutOn) { Row(Modifier.padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
      TimeField("Início", state.cutStart, "0:00", Modifier.weight(1f)) { a.cut(true, it, state.cutEnd) }
      TimeField("Fim", state.cutEnd, "fim", Modifier.weight(1f)) { a.cut(true, state.cutStart, it) }
    } }
  }
}

@Composable private fun TimeField(label: String, v: String, hint: String, m: Modifier, on: (String) -> Unit) {
  val c = MaterialTheme.colorScheme
  OutlinedTextField(v, { t -> on(t.filter { it.isDigit() || it == ':' }.take(8)) }, m, singleLine = true, label = { Text(label) },
    placeholder = { Text(hint) }, shape = CircleShape,
    keyboardOptions = KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = c.primary, unfocusedBorderColor = c.outline, focusedLabelColor = c.onSurface))
}

@Composable private fun ResultCard(r: SearchResult, selected: Boolean, multi: Boolean, playing: Boolean, loading: Boolean,
                                   onSelect: () -> Unit, onPreview: () -> Unit) {
  val c = MaterialTheme.colorScheme
  val src = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
  Row(Modifier.fillMaxWidth().springPress(src).clip(RoundedCornerShape(22.dp))
      .background(c.surfaceVariant).border(1.5.dp, if (selected) c.primary else c.surfaceVariant, RoundedCornerShape(22.dp))
      .clickable(src, null, onClick = onSelect).padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
    if (multi) { Icon(if (selected) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked, null,
      tint = if (selected) c.primary else c.onSurfaceVariant, modifier = Modifier.padding(start = 4.dp, end = 10.dp).size(22.dp)) }
    Box(Modifier.size(width = 112.dp, height = 63.dp).clip(RoundedCornerShape(14.dp)).background(c.outline)) {
      AsyncImage(r.thumb, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
      if (r.duration > 0) Text(fmt(r.duration), Modifier.align(Alignment.BottomEnd).padding(4.dp).clip(CircleShape)
        .background(androidx.compose.ui.graphics.Color(0xAA000000)).padding(horizontal = 6.dp, vertical = 1.dp),
        color = androidx.compose.ui.graphics.Color.White, fontSize = 10.sp)
    }
    Spacer(Modifier.width(12.dp))
    Column(Modifier.weight(1f)) {
      Text(r.title, color = c.onSurface, fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
      Text(r.channel, color = c.onSurfaceVariant, fontSize = 12.sp, maxLines = 1)
    }
    Spacer(Modifier.width(8.dp))
    if (loading) CircularProgressIndicator(Modifier.size(44.dp).padding(11.dp), strokeWidth = 2.dp, color = c.onSurface)
    else RoundIconButton(if (playing) Icons.Rounded.Stop else Icons.Rounded.PlayArrow, "Prévia de 30 segundos", onPreview, container = c.background)
  }
}

@Composable private fun Label(t: String) = Text(t.uppercase(), color = MaterialTheme.colorScheme.onSurfaceVariant,
  fontSize = 11.sp, letterSpacing = 2.sp)

@OptIn(ExperimentalFoundationApi::class)
@Composable fun HistoryPanel(state: UiState, a: Actions, modifier: Modifier) {
  val c = MaterialTheme.colorScheme
  LazyColumn(modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
    if (state.queue.isNotEmpty()) {
      item { Label("Na fila") }
      items(state.queue, key = { it.id }) { q ->
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(c.surfaceVariant).padding(start = 14.dp, end = 6.dp, top = 10.dp, bottom = 10.dp),
          verticalAlignment = Alignment.CenterVertically) {
          Column(Modifier.weight(1f)) {
            Text(q.title, color = c.onSurface, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(when { q.error != null -> q.error; q.running -> "Baixando ${(q.progress * 100).toInt()}%"; else -> "Aguardando" },
              color = if (q.error != null) androidx.compose.ui.graphics.Color(0xFFD08770) else c.onSurfaceVariant, fontSize = 11.sp, maxLines = 2)
            if (q.error == null) { Spacer(Modifier.height(6.dp))
              LinearProgressIndicator({ if (q.running) q.progress else 0f }, Modifier.fillMaxWidth().clip(CircleShape), color = c.primary, trackColor = c.outline) }
          }
          Spacer(Modifier.width(6.dp))
          if (q.error != null) { RoundIconButton(Icons.Rounded.Refresh, "Tentar de novo", { a.retry(q) }, container = c.background)
            Spacer(Modifier.width(4.dp)); RoundIconButton(Icons.Rounded.Close, "Remover", { a.dismiss(q) }, container = c.background) }
          else RoundIconButton(Icons.Rounded.Close, "Cancelar", { a.cancel(q) }, container = c.background)
        }
      }
      item { Spacer(Modifier.height(8.dp)) }
    }
    item { Label("Biblioteca") }
    item { Row(Modifier.fillMaxWidth().clip(CircleShape).background(c.surfaceVariant).padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically) {
      Icon(Icons.Rounded.Search, null, tint = c.onSurfaceVariant, modifier = Modifier.size(18.dp))
      TextField(state.historyFilter, a.historyFilter, Modifier.weight(1f), singleLine = true, textStyle = LocalTextStyle.current.copy(fontSize = 14.sp),
        placeholder = { Text("Buscar no histórico", color = c.onSurfaceVariant, fontSize = 14.sp) },
        colors = TextFieldDefaults.colors(focusedContainerColor = c.surfaceVariant, unfocusedContainerColor = c.surfaceVariant,
          focusedIndicatorColor = c.surfaceVariant, unfocusedIndicatorColor = c.surfaceVariant))
      if (state.historyFilter.isNotEmpty()) IconButton({ a.historyFilter("") }) { Icon(Icons.Rounded.Close, "Limpar", tint = c.onSurfaceVariant) }
    } }
    item { LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      items(listOf("Tudo", "Favoritos") + state.playlists.keys.sorted()) { v -> var plMenu by remember { mutableStateOf(false) }
        Box { val on = v == state.historyView
          Row(Modifier.clip(CircleShape).background(if (on) c.primary else c.surfaceVariant)
              .combinedClickable(onClick = { a.historyView(v) }, onLongClick = { if (v != "Tudo" && v != "Favoritos") plMenu = true })
              .padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (v == "Favoritos") { Icon(Icons.Rounded.Favorite, null, tint = if (on) c.onPrimary else c.onSurfaceVariant, modifier = Modifier.size(14.dp)); Spacer(Modifier.width(6.dp)) }
            Text(v, color = if (on) c.onPrimary else c.onSurface, fontSize = 13.sp) }
          DropdownMenu(plMenu, { plMenu = false }, shape = RoundedCornerShape(18.dp), containerColor = c.surface) {
            DropdownMenuItem({ Text("Apagar playlist") }, { plMenu = false; a.deletePlaylist(v) }, leadingIcon = { Icon(Icons.Rounded.Delete, null) }) }
        } } } }
    val shown = visibleHistory(state)
    if (shown.isEmpty()) item { Column(Modifier.fillMaxWidth().padding(vertical = 28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
      Icon(when (state.historyView) { "Favoritos" -> Icons.Rounded.FavoriteBorder; "Tudo" -> Icons.Rounded.LibraryMusic; else -> Icons.Rounded.QueueMusic }, null,
        tint = c.onSurfaceVariant, modifier = Modifier.size(36.dp)); Spacer(Modifier.height(10.dp))
      Text(when { state.historyFilter.isNotBlank() -> "Nada com esse nome"; state.historyView == "Favoritos" -> "Toque no coração de uma música pra favoritar"
        state.historyView == "Tudo" -> "Nada baixado ainda"; else -> "Playlist vazia" }, color = c.onSurfaceVariant, fontSize = 13.sp) } }
    itemsIndexed(shown, key = { _, h -> h.uri + h.title }) { i, h ->
      var menu by remember { mutableStateOf(false) }
      var pick by remember { mutableStateOf(false) }
      val fav = h.uri in state.favorites
      Box(Modifier.cascade(i)) {
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(c.surfaceVariant)
            .combinedClickable(onClick = { a.play(h) }, onLongClick = { menu = true }).padding(start = 14.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
          verticalAlignment = Alignment.CenterVertically) {
          Box(Modifier.size(40.dp).clip(CircleShape).background(c.background), contentAlignment = Alignment.Center) {
            if (h.art.isNotBlank()) AsyncImage(h.art, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            else Icon(if (h.meta.startsWith("MP4")) Icons.Rounded.Movie else Icons.Rounded.MusicNote, null, tint = c.onSurface) }
          Spacer(Modifier.width(12.dp))
          Column(Modifier.weight(1f)) { Text(h.title, color = c.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(h.artist.ifBlank { h.meta }, color = c.onSurfaceVariant, fontSize = 12.sp, maxLines = 1) }
          val heart by androidx.compose.animation.core.animateFloatAsState(if (fav) 1f else 0f,
            androidx.compose.animation.core.spring(dampingRatio = 0.35f, stiffness = 500f), label = "fav")
          IconButton({ a.favorite(h) }) { Icon(if (fav) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, "Favoritar",
            tint = if (fav) c.primary else c.onSurfaceVariant, modifier = Modifier.graphicsLayer { val s = 1f + heart * .25f - (heart - 1f).let { it * it } * .25f; scaleX = s; scaleY = s }) }
        }
        DropdownMenu(menu, { menu = false }, shape = RoundedCornerShape(18.dp), containerColor = c.surface) {
          DropdownMenuItem({ Text("Tocar aqui") }, { menu = false; a.play(h) }, leadingIcon = { Icon(Icons.Rounded.PlayArrow, null) })
          DropdownMenuItem({ Text("Adicionar à playlist") }, { menu = false; pick = true }, leadingIcon = { Icon(Icons.Rounded.PlaylistAdd, null) })
          if (state.historyView in state.playlists) DropdownMenuItem({ Text("Tirar desta playlist") }, { menu = false; a.removeFromPlaylist(h) },
            leadingIcon = { Icon(Icons.Rounded.PlaylistRemove, null) })
          DropdownMenuItem({ Text("Abrir em outro app") }, { menu = false; a.open(h) }, leadingIcon = { Icon(Icons.Rounded.OpenInNew, null) })
          DropdownMenuItem({ Text("Compartilhar") }, { menu = false; a.share(h) }, leadingIcon = { Icon(Icons.Rounded.Share, null) })
          DropdownMenuItem({ Text("Apagar arquivo") }, { menu = false; a.delete(h) }, leadingIcon = { Icon(Icons.Rounded.Delete, null) })
        }
        if (pick) PlaylistPicker(state.playlists.keys.sorted(), { pick = false }) { name -> pick = false; a.addToPlaylist(h, name) }
      }
    }
    item { Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      PillButton("Exportar", a.exportBackup, Modifier.weight(1f), Icons.Rounded.Upload, filled = false)
      PillButton("Importar", a.importBackup, Modifier.weight(1f), Icons.Rounded.Download, filled = false) } }
  }
}

/** A capa do download concluído sai do botão Baixar num arco até o botão de histórico, que dá um quique ao receber. */
@Composable private fun FlyOverlay(e: FlyEvent, t: FlyTargets) {
  val prog = remember(e.id) { androidx.compose.animation.core.Animatable(0f) }
  var done by remember(e.id) { mutableStateOf(false) }
  LaunchedEffect(e.id) {
    if (t.history == androidx.compose.ui.geometry.Offset.Zero) { done = true; return@LaunchedEffect }
    prog.animateTo(1f, androidx.compose.animation.core.tween(650, easing = androidx.compose.animation.core.FastOutSlowInEasing))
    done = true
    t.bounce.snapTo(1.35f); t.bounce.animateTo(1f, androidx.compose.animation.core.spring(dampingRatio = 0.35f, stiffness = 400f))
  }
  if (done) return
  val d = LocalDensity.current
  val v = prog.value.coerceIn(0f, 1f)
  val s = t.source; val h = t.history
  val ctrl = androidx.compose.ui.geometry.Offset((s.x + h.x) / 2, minOf(s.y, h.y) - 300f)
  val x = (1 - v) * (1 - v) * s.x + 2 * (1 - v) * v * ctrl.x + v * v * h.x
  val y = (1 - v) * (1 - v) * s.y + 2 * (1 - v) * v * ctrl.y + v * v * h.y
  val size = with(d) { androidx.compose.ui.util.lerp(72f, 22f, v).dp }
  Box(Modifier.offset { IntOffset((x - size.toPx() / 2).toInt(), (y - size.toPx() / 2).toInt()) }.size(size)
      .graphicsLayer { rotationZ = v * 200f; alpha = 1f - (v - .85f).coerceAtLeast(0f) * 6f }
      .clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.primary)) {
    if (e.art.isNotBlank()) AsyncImage(e.art, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
    else Icon(Icons.Rounded.MusicNote, null, Modifier.fillMaxSize().padding(10.dp), tint = MaterialTheme.colorScheme.onPrimary)
  }
}

fun visibleHistory(state: UiState): List<HistoryItem> {
  val base = when (state.historyView) { "Tudo" -> state.history; "Favoritos" -> state.history.filter { it.uri in state.favorites }
    else -> state.playlists[state.historyView].orEmpty().mapNotNull { u -> state.history.firstOrNull { it.uri == u } } }
  val q = state.historyFilter.trim()
  return if (q.isEmpty()) base else base.filter { it.title.contains(q, true) || it.artist.contains(q, true) }
}

@Composable private fun PlaylistPicker(names: List<String>, dismiss: () -> Unit, pick: (String) -> Unit) {
  var new by remember { mutableStateOf("") }
  AlertDialog(dismiss, shape = RoundedCornerShape(28.dp), title = { Text("Adicionar à playlist") }, text = {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
      names.forEach { n -> PillButton(n, { pick(n) }, Modifier.fillMaxWidth(), Icons.Rounded.QueueMusic, filled = false) }
      OutlinedTextField(new, { new = it }, Modifier.fillMaxWidth(), singleLine = true, shape = CircleShape, placeholder = { Text("Nova playlist") })
    } }, confirmButton = { TextButton({ if (new.isNotBlank()) pick(new.trim()) }, enabled = new.isNotBlank()) { Text("Criar e adicionar") } },
    dismissButton = { TextButton(dismiss) { Text("Cancelar") } })
}

