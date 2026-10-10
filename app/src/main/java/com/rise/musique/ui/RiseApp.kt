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
data class HistoryItem(val title: String, val meta: String, val uri: String = "", val art: String = "", val artist: String = "", val duration: Long = 0, val album: String = "", val imported: Boolean = false, val video: Boolean = meta.startsWith("MP4"))
data class NowPlaying(val key: String, val title: String, val artist: String, val art: String, val playing: Boolean = false,
  val loading: Boolean = false, val position: Long = 0, val duration: Long = 0, val isPreview: Boolean = false,
  val hasNext: Boolean = false, val hasPrev: Boolean = false, val shuffle: Boolean = false, val index: Int = 0, val count: Int = 1,
  val repeat: Int = 0, val lyrics: List<com.rise.musique.Lyrics.Line>? = null, val lyricsState: String = "")
data class SearchResult(val url: String, val title: String, val channel: String, val duration: Int, val thumb: String)
data class FlyEvent(val id: Long, val art: String)
class FlyTargets { var history by mutableStateOf(androidx.compose.ui.geometry.Offset.Zero); var source by mutableStateOf(androidx.compose.ui.geometry.Offset.Zero)
  val bounce = androidx.compose.animation.core.Animatable(1f) }
data class QueueItem(val id: String, val title: String, val progress: Float, val running: Boolean, val error: String? = null, val stage: String = "Aguardando", val completed: Boolean = false)
data class UiState(val query: String = "", val mode: Mode = Mode.Musica, val quality: String = "320k",
  val folder: android.net.Uri? = null, val status: String = "", val history: List<HistoryItem> = emptyList(),
  val searching: Boolean = false, val results: List<SearchResult> = emptyList(), val selected: Set<String> = emptySet(),
  val isPlaylist: Boolean = false, val format: String = "MP3", val cutOn: Boolean = false, val cutStart: String = "", val cutEnd: String = "",
  val previewing: String? = null, val previewLoading: Boolean = false, val now: NowPlaying? = null, val fly: FlyEvent? = null,
  val favorites: Set<String> = emptySet(), val playlists: Map<String, List<String>> = emptyMap(), val historyFilter: String = "",
  val recentSearches: List<String> = emptyList(), val moreResults: Boolean = false, val loadingMore: Boolean = false,
  val importing: Boolean = false, val sort: String = "Recentes", val mediaFilter: String = "Tudo", val albumFilter: String = "", val theme: String = "Sistema", val settingsOpen: Boolean = false, val page: String = "Biblioteca", val choices: com.rise.musique.DownloadChoices = com.rise.musique.DownloadChoices(),
  val playbackQueue: List<HistoryItem> = emptyList(), val queueOpen: Boolean = false, val historyView: String = "Tudo", val sleepAt: Long = 0, val queue: List<QueueItem> = emptyList(),
  val crossfade: Int = 0, val statsOpen: Boolean = false, val stats: com.rise.musique.StatsSummary? = null)
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
  val open: (HistoryItem) -> Unit = {}, val share: (HistoryItem) -> Unit = {}, val delete: (HistoryItem) -> Unit = {}, val updates: () -> Unit = {},
  val page: (String) -> Unit = {}, val repeat: () -> Unit = {}, val showQueue: (Boolean) -> Unit = {},
  val moveQueue: (Int, Int) -> Unit = { _, _ -> }, val removeQueue: (Int) -> Unit = {}, val playQueue: (Int) -> Unit = {},
  val playNext: (HistoryItem) -> Unit = {}, val addQueue: (HistoryItem) -> Unit = {},
  val importFiles: () -> Unit = {}, val importFolder: () -> Unit = {}, val moreResults: () -> Unit = {}, val clearSearches: () -> Unit = {},
  val sort: (String) -> Unit = {}, val mediaFilter: (String) -> Unit = {}, val albumFilter: (String) -> Unit = {},
  val renamePlaylist: (String, String) -> Unit = { _, _ -> }, val movePlaylist: (String, Int, Int) -> Unit = { _, _, _ -> },
  val refreshMetadata: () -> Unit = {}, val theme: (String) -> Unit = {}, val settings: (Boolean) -> Unit = {}, val removeLocal: (HistoryItem) -> Unit = {},
  val crossfade: (Int) -> Unit = {}, val stats: (Boolean) -> Unit = {})
val audioFormats = listOf("MP3", "M4A", "Opus", "FLAC")

val sampleHistory = listOf(HistoryItem("Faixa de exemplo", "MP3 · 320k · 8,4 MB"),
  HistoryItem("Vídeo de exemplo", "MP4 · 1080p · 142 MB"), HistoryItem("Outra faixa", "M4A · 256k · 6,1 MB")).mapIndexed { i, h -> h.copy(uri = "sample:$i") }
val sampleResults = listOf(SearchResult("a", "Música de exemplo (Clipe Oficial)", "Artista Exemplo", 214, ""),
  SearchResult("b", "Música de exemplo (Ao Vivo)", "Canal Exemplo", 251, ""),
  SearchResult("c", "Música de exemplo · versão acústica", "Outro Canal", 189, ""))

@Composable fun RiseApp(state: UiState, actions: Actions, initialHistoryOpen: Boolean = false, initialExpanded: Boolean = false, initialLyrics: Boolean = false) {
  val targets = remember { FlyTargets() }
  Box(Modifier.fillMaxSize()) {
    RiseContent(state, actions, initialHistoryOpen, targets)
    state.fly?.let { FlyOverlay(it, targets) }
    state.now?.let { CompositionLocalProvider(LocalSleep provides state.sleepAt) { PlayerSheet(it, actions, initialExpanded, initialLyrics) } }
    if (state.queueOpen) PlaybackQueueSheet(state, actions)
    if (state.settingsOpen) SettingsSheet(state, actions)
    if (state.statsOpen) StatsSheet(state, actions)
  }
}

@Composable private fun RiseContent(state: UiState, actions: Actions, initialHistoryOpen: Boolean, targets: FlyTargets) {
  val c = MaterialTheme.colorScheme
  Column(Modifier.fillMaxSize().background(c.background).safeDrawingPadding()) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
      Text("rise", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge, letterSpacing = (-1).sp)
      RoundIconButton(Icons.Rounded.SystemUpdate, "Verificar atualizações", actions.updates, container = c.background)
      Spacer(Modifier.width(8.dp))
      RoundIconButton(Icons.Rounded.Tune, "Ajustes", { actions.settings(true) }, container = c.background)
    }
    if (state.status.isNotBlank()) Text(state.status, Modifier.padding(horizontal = 24.dp, vertical = 4.dp), color = c.onSurfaceVariant, fontSize = 12.sp, maxLines = 2)
    when (state.page) {
      "Buscar" -> MainPane(state, actions, targets, null, Modifier.weight(1f).fillMaxWidth())
      "Downloads" -> DownloadHub(state, actions, Modifier.weight(1f).fillMaxWidth())
      else -> LibraryHome(state, actions, Modifier.weight(1f).fillMaxWidth())
    }
    NavigationDock(state, actions, Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)
      .graphicsLayer { scaleX = targets.bounce.value; scaleY = targets.bounce.value }
      .onGloballyPositioned { targets.history = it.positionInRoot() + androidx.compose.ui.geometry.Offset(it.size.width / 6f, it.size.height / 2f) })
  }
}

@Composable private fun NavigationDock(s: UiState, a: Actions, modifier: Modifier) {
  val c = MaterialTheme.colorScheme
  Row(modifier.clip(RoundedCornerShape(32.dp)).background(c.surface).border(1.dp, c.outlineVariant, RoundedCornerShape(32.dp)).padding(5.dp)) {
    listOf("Biblioteca", "Buscar", "Downloads").forEach { page ->
      val on = s.page == page
      val src = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
      Column(Modifier.weight(1f).springPress(src).clip(RoundedCornerShape(28.dp)).background(if (on) c.surfaceVariant else c.surface)
        .clickable(src, null) { a.page(page) }.padding(vertical = 9.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(when (page) { "Biblioteca" -> Icons.Rounded.LibraryMusic; "Buscar" -> Icons.Rounded.Search; else -> Icons.Rounded.Download }, null,
          tint = if (on) c.onSurface else c.onSurfaceVariant, modifier = Modifier.size(23.dp))
        Text(if (page == "Biblioteca") "Ouvir" else page, color = if (on) c.onSurface else c.onSurfaceVariant, fontSize = 11.sp, maxLines = 1)
      }
    }
  }
}

@Composable private fun MainPane(state: UiState, a: Actions, targets: FlyTargets, onHistory: (() -> Unit)?, modifier: Modifier) {
  val c = MaterialTheme.colorScheme
  Column(modifier.background(c.background).padding(horizontal = 24.dp, vertical = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
    Text("Buscar", style = MaterialTheme.typography.headlineLarge)
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
    LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
      if (state.selected.isNotEmpty()) item { DownloadOptions(state, a) }
      if (state.results.size > 1 && state.isPlaylist) item { Row(verticalAlignment = Alignment.CenterVertically) {
        Text("${state.results.size} faixas · ${state.selected.size} marcadas", Modifier.weight(1f), color = c.onSurfaceVariant, fontSize = 13.sp)
        TextButton(a.selectAll) { Text(if (state.selected.size == state.results.size) "Desmarcar" else "Marcar todas") }
      } }
      itemsIndexed(state.results, key = { _, it -> it.url }) { i, r ->
        Box(Modifier.cascade(i)) {
        ResultCard(r, r.url in state.selected, state.isPlaylist, state.previewing == r.url, state.previewing == r.url && state.previewLoading,
          { a.select(r) }, { a.preview(r) }) }
      }
      if (state.moreResults) item { PillButton(if (state.loadingMore) "Carregando…" else "Carregar mais resultados", a.moreResults, Modifier.fillMaxWidth(), filled = false, enabled = !state.loadingMore) }
      if (state.results.isEmpty() && !state.searching) {
        item { Text("Um nome, artista ou link. Encontre o que quer ouvir.", color = c.onSurfaceVariant, modifier = Modifier.padding(vertical = 16.dp)) }
        if (state.recentSearches.isNotEmpty()) {
          item { Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Buscas recentes", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
            TextButton(a.clearSearches) { Text("Limpar") }
          } }
          items(state.recentSearches) { query -> Row(Modifier.fillMaxWidth().clickable { a.query(query); a.search() }.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.History, null, tint = c.onSurfaceVariant); Spacer(Modifier.width(14.dp)); Text(query, maxLines = 2, overflow = TextOverflow.Ellipsis)
          } }
        }
      }
      if (state.selected.size == 1) item { CutPanel(state, a) }
    }
    val active = state.queue.count { it.error == null && !it.completed }
    if (state.selected.isNotEmpty()) PillButton(modifier = Modifier.fillMaxWidth().onGloballyPositioned { targets.source = it.positionInRoot() + androidx.compose.ui.geometry.Offset(it.size.width / 2f, it.size.height / 2f) },
      text = when { state.selected.size > 1 -> "Baixar ${state.selected.size} faixas"; active == 0 -> "Baixar"
        else -> "Adicionar à fila ($active)" }, onClick = a.download, icon = Icons.Rounded.Download, enabled = state.selected.isNotEmpty() && state.choices.ready(state.mode, state.format))
    if (state.now != null) Spacer(Modifier.height(88.dp))
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
      .background(if (selected) c.surfaceVariant else c.background).border(1.dp, if (selected) c.outline else c.background, RoundedCornerShape(22.dp))
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

@Composable fun HistoryPanel(state: UiState, a: Actions, modifier: Modifier, showLibrary: Boolean = true, showDownloads: Boolean = true) {
  if (showLibrary) LibraryHome(state, a, modifier) else DownloadHub(state, a, modifier)
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
  val filtered = base.filter { h ->
    (state.mediaFilter == "Tudo" || (state.mediaFilter == "Vídeos") == h.video) &&
    (state.albumFilter.isEmpty() || h.album == state.albumFilter) &&
    (q.isEmpty() || h.title.contains(q, true) || h.artist.contains(q, true) || h.album.contains(q, true))
  }
  if (state.historyView in state.playlists) return filtered
  return when (state.sort) {
    "Título" -> filtered.sortedBy { it.title.lowercase() }
    "Artista" -> filtered.sortedWith(compareBy<HistoryItem> { it.artist.lowercase() }.thenBy { it.title.lowercase() })
    else -> filtered
  }
}

@Composable internal fun PlaylistPicker(names: List<String>, dismiss: () -> Unit, pick: (String) -> Unit) {
  var new by remember { mutableStateOf("") }
  AlertDialog(dismiss, shape = RoundedCornerShape(28.dp), title = { Text("Adicionar à playlist") }, text = {
    Column(Modifier.heightIn(max = 340.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
      names.forEach { n -> PillButton(n, { pick(n) }, Modifier.fillMaxWidth(), Icons.Rounded.QueueMusic, filled = false) }
      OutlinedTextField(new, { new = it }, Modifier.fillMaxWidth(), singleLine = true, shape = CircleShape, placeholder = { Text("Nova playlist") })
    } }, confirmButton = { TextButton({ if (new.isNotBlank()) pick(new.trim()) }, enabled = new.isNotBlank()) { Text("Criar e adicionar") } },
    dismissButton = { TextButton(dismiss) { Text("Cancelar") } })
}

