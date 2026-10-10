package com.rise.musique.ui
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.animation.core.animateFloat
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
  val loading: Boolean = false, val position: Long = 0, val duration: Long = 0, val isPreview: Boolean = false, val streaming: Boolean = false, val saveState: String = "",
  val hasNext: Boolean = false, val hasPrev: Boolean = false, val shuffle: Boolean = false, val index: Int = 0, val count: Int = 1,
  val repeat: Int = 0, val lyrics: List<com.rise.musique.Lyrics.Line>? = null, val lyricsState: String = "", val quality: String = "")
data class SearchResult(val url: String, val title: String, val channel: String, val duration: Int, val thumb: String,
  /** song, video, album, artist, playlist */ val kind: String = "video", val album: String = "", val subtitle: String = "") {
  val playable get() = kind == "song" || kind == "video"
}
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
  val importing: Boolean = false, val sort: String = "Recentes", val mediaFilter: String = "Tudo", val albumFilter: String = "", val theme: String = "Sistema", val settingsOpen: Boolean = false, val page: String = "Início", val choices: com.rise.musique.DownloadChoices = com.rise.musique.DownloadChoices(),
  val playbackQueue: List<HistoryItem> = emptyList(), val queueOpen: Boolean = false, val historyView: String = "Tudo", val sleepAt: Long = 0, val queue: List<QueueItem> = emptyList(),
  val crossfade: Int = 0, val statsOpen: Boolean = false, val radioOf: String? = null, val automix: Boolean = false, val cutOpen: Boolean = false, val cutVisual: com.rise.musique.CutVisual? = null,
  val lastfmUser: String? = null, val lastfmOn: Boolean = false, val lastfmConfigured: Boolean = false, val recapOpen: Boolean = false,
  val recapMonth: com.rise.musique.Recap? = null, val recapYear: com.rise.musique.Recap? = null, val stats: com.rise.musique.StatsSummary? = null,
  val eq: com.rise.musique.EqSettings = com.rise.musique.EqSettings(), val eqProfile: String = "speaker", val eqOpen: Boolean = false,
  val normalize: Boolean = true, val normalizeAlbum: Boolean = false, val lockLyrics: Boolean = false, val wifiOnly: Boolean = false,
  val followed: Set<String> = emptySet(), val trackInfo: Map<String, com.rise.musique.TrackInfo> = emptyMap(),
  val searchKind: com.rise.musique.SearchKind = com.rise.musique.SearchKind.Songs, val durationFilter: com.rise.musique.DurationFilter = com.rise.musique.DurationFilter.Any,
  val dateFilter: com.rise.musique.DateFilter = com.rise.musique.DateFilter.Any, val video: VideoUi? = null, val pip: Boolean = false,
  val voice: VoiceUi? = null,
  val undo: UndoInfo? = null, val popular: Map<String, List<SearchResult>> = emptyMap(), val popularLoading: Set<String> = emptySet(), val hidden: Set<String> = emptySet())

/** Snackbar with Desfazer; the real action runs only after the timeout. */
data class UndoInfo(val id: Long, val message: String)
data class Actions(val query: (String) -> Unit = {}, val mode: (Mode) -> Unit = {}, val quality: (String) -> Unit = {},
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
  val crossfade: (Int) -> Unit = {}, val stats: (Boolean) -> Unit = {},
  val playShuffled: (HistoryItem) -> Unit = {}, val queueAll: (List<HistoryItem>) -> Unit = {},
  val radio: (SearchResult) -> Unit = {}, val radioFor: (HistoryItem) -> Unit = {}, val automix: (Boolean) -> Unit = {},
  val lastfmLogin: (String, String) -> Unit = { _, _ -> }, val lastfmLogout: () -> Unit = {}, val lastfmToggle: (Boolean) -> Unit = {},
  val recap: (Boolean) -> Unit = {}, val shareRecap: (String) -> Unit = {},
  val openCut: (Boolean) -> Unit = {}, val previewRange: (Long, Long) -> Unit = { _, _ -> },
  val undo: () -> Unit = {}, val removeSearch: (String) -> Unit = {}, val searchLive: () -> Unit = {}, val clearQueue: () -> Unit = {},
  val artistPopular: (String) -> Unit = {}, val prepareDownload: (SearchResult) -> Unit = {}, val removeFromList: (HistoryItem, String) -> Unit = { _, _ -> },
  val openArtist: (String) -> Unit = {}, val openAlbum: (String) -> Unit = {}, val openPlaylist: (String) -> Unit = {}, val openDownloads: () -> Unit = {},
  val eqOpen: (Boolean) -> Unit = {}, val eqChange: (com.rise.musique.EqSettings) -> Unit = {}, val normalize: (Boolean) -> Unit = {},
  val normalizeAlbum: (Boolean) -> Unit = {}, val lockLyrics: (Boolean) -> Unit = {}, val wifiOnly: (Boolean) -> Unit = {},
  val follow: (String) -> Unit = {}, val openSmart: (String, String) -> Unit = { _, _ -> },
  val searchKind: (com.rise.musique.SearchKind) -> Unit = {}, val durationFilter: (com.rise.musique.DurationFilter) -> Unit = {}, val dateFilter: (com.rise.musique.DateFilter) -> Unit = {},
  val openResult: (SearchResult) -> Unit = {}, val saveStream: () -> Unit = {}, val playVideo: (SearchResult) -> Unit = {}, val playLocalVideo: (HistoryItem) -> Unit = {},
  val video: VideoActions = VideoActions(), val voiceStart: () -> Unit = {}, val voiceStop: () -> Unit = {})
val audioFormats = listOf("MP3", "M4A", "Opus", "FLAC")

val sampleHistory = listOf(HistoryItem("Faixa de exemplo", "MP3 · 320k · 8,4 MB"),
  HistoryItem("Vídeo de exemplo", "MP4 · 1080p · 142 MB"), HistoryItem("Outra faixa", "M4A · 256k · 6,1 MB")).mapIndexed { i, h -> h.copy(uri = "sample:$i") }
val sampleResults = listOf(SearchResult("a", "Música de exemplo (Clipe Oficial)", "Artista Exemplo", 214, ""),
  SearchResult("b", "Música de exemplo (Ao Vivo)", "Canal Exemplo", 251, ""),
  SearchResult("c", "Música de exemplo · versão acústica", "Outro Canal", 189, ""))

@Composable fun RiseApp(state: UiState, actions: Actions, initialHistoryOpen: Boolean = false, initialExpanded: Boolean = false, initialLyrics: Boolean = false,
                       initialCollectionItem: Int = 0, initialRoute: Route? = null) {
  val targets = remember { FlyTargets() }
  Box(Modifier.fillMaxSize()) {
    RiseContent(state, actions, initialHistoryOpen, targets, initialCollectionItem, initialRoute)
    UndoBar(state, actions, Modifier.align(Alignment.BottomCenter))
    state.fly?.let { FlyOverlay(it, targets) }
    state.now?.let { CompositionLocalProvider(LocalSleep provides state.sleepAt) { PlayerSheet(it, actions, initialExpanded, initialLyrics, state.playbackQueue) } }
    if (state.queueOpen) PlaybackQueueSheet(state, actions)
    if (state.settingsOpen) SettingsSheet(state, actions)
    if (state.statsOpen) StatsSheet(state, actions)
    if (state.cutOpen) CutSheet(state, actions)
    state.video?.let { VideoPlayerScreen(it, actions.video, state.pip, Modifier.fillMaxSize()) }
    if (state.eqOpen) EqSheet(state, actions)
    if (state.recapOpen) RecapSheet(state, actions)
  }
}

/**
 * The one owner of the navigation SeekableTransitionState: state and Transition are created together in one
 * unconditional group (no key()/branch around them), so a Transition is never re-created for an existing state.
 */
@Composable private fun rememberScreenTransition(initial: Screen):
    Pair<androidx.compose.animation.core.SeekableTransitionState<Screen>, androidx.compose.animation.core.Transition<Screen>> {
  val seek = remember { androidx.compose.animation.core.SeekableTransitionState(initial) }
  return seek to androidx.compose.animation.core.rememberTransition(seek, label = "screen")
}

private val Route?.isDetail get() = this is Route.Album || this is Route.Playlist || this is Route.Artist || this is Route.Smart

@OptIn(androidx.compose.animation.ExperimentalSharedTransitionApi::class)
@Composable private fun RiseContent(state: UiState, actions: Actions, initialHistoryOpen: Boolean, targets: FlyTargets, initialCollectionItem: Int = 0,
                                    initialRoute: Route? = null) {
  val c = MaterialTheme.colorScheme
  val tab = when (state.page) { "Downloads" -> "Buscar"; in Tabs -> state.page; else -> "Início" }
  val nav = remember { NavState().apply { if (initialRoute != null) push(tab, initialRoute) } }
  val screen = Screen(tab, nav.top(tab))
  val (seek, transition) = rememberScreenTransition(screen)
  val scope = rememberCoroutineScope()
  var seeking by remember { mutableStateOf(false) }
  // Mesma aba: transição animada e interrompível. Troca de aba: instantânea.
  LaunchedEffect(screen) { if (!seeking) { if (seek.currentState.tab != screen.tab) seek.snapTo(screen) else seek.animateTo(screen, Motion.enter(Motion.Long)) } }
  val pop: () -> Unit = { nav.pop(tab) }
  androidx.activity.compose.PredictiveBackHandler(enabled = nav.stack(tab).isNotEmpty()) { progress ->
    val prev = nav.previous(tab)
    try {
      seeking = true
      progress.collect { e -> seek.seekTo((e.progress * .9f).coerceIn(0f, 1f), targetState = prev) }
      seek.animateTo(prev, Motion.enter(Motion.Medium)); nav.pop(tab)
    } catch (e: kotlin.coroutines.cancellation.CancellationException) {
      scope.launch { seek.animateTo(screen, Motion.settle()) }; throw e
    } finally { seeking = false }
  }
  androidx.activity.compose.BackHandler(enabled = nav.stack(tab).isEmpty() && tab != "Início") { actions.page("Início") }
  val rootKey = "root:$tab"
  val navActions = remember(actions, tab, state.playlists.keys) { actions.copy(
    openAlbum = { nav.push(tab, Route.Album(it)) }, openPlaylist = { nav.push(tab, Route.Playlist(it)) }, openArtist = { nav.push(tab, Route.Artist(it)) },
    openDownloads = { nav.push("Buscar", Route.Downloads); actions.page("Buscar") },
    openSmart = { k, v -> nav.push(tab, Route.Smart(k, v)) },
    albumFilter = { if (it.isBlank()) nav.pop(tab) else nav.push(tab, Route.Album(it)) },
    historyView = { if (it in state.playlists) nav.push(tab, Route.Playlist(it)) else actions.historyView(it) },
    page = { t -> val target = if (t == "Downloads") "Buscar" else t
      // Tocar de novo na aba: volta à raiz; se já está na raiz, rola para o topo.
      if (target == tab) { if (nav.stack(tab).isNotEmpty()) nav.popToRoot(tab) else scope.launch { nav.list("root:$tab").animateScrollToItem(0) } }
      else actions.page(target) }) }
  var lastDetail by remember { mutableStateOf<Route?>(initialRoute?.takeIf { it.isDetail }) }
  if (screen.route.isDetail && lastDetail != screen.route) lastDetail = screen.route
  val backdropAlpha = transition.animateFloat({ Motion.enter(Motion.Long) }, label = "backdrop") { if (it.route.isDetail) 1f else 0f }
  val dismiss = remember { androidx.compose.animation.core.Animatable(0f) }
  Box(Modifier.fillMaxSize().background(c.background)) {
    // Telas de detalhe: fundo vivo fixo cobrindo a tela inteira, entrando em fade junto com a transição.
    lastDetail?.let { d -> if (screen.route.isDetail || transition.isRunning) {
      val art = when (d) { is Route.Artist -> artistArt(state, d.name)
        is Route.Album -> state.history.firstOrNull { it.album == d.name && it.art.isNotBlank() }?.art.orEmpty()
        is Route.Smart -> smartFolder(state, d.kind, d.value)?.tracks?.firstOrNull { it.art.isNotBlank() }?.art.orEmpty()
        is Route.Playlist -> state.playlists[d.name].orEmpty().firstNotNullOfOrNull { u -> state.history.firstOrNull { it.uri == u && it.art.isNotBlank() }?.art }.orEmpty()
        else -> "" }
      CollectionBackdrop(art, collapseOf(nav.list(d.key, initialCollectionItem)),
        Modifier.fillMaxSize().graphicsLayer { alpha = (backdropAlpha.value * (1f - dismiss.value)).coerceIn(0f, 1f) })
    } }
    Column(Modifier.fillMaxSize().safeDrawingPadding()) {
      androidx.compose.animation.SharedTransitionLayout(Modifier.weight(1f).fillMaxWidth()) {
        CompositionLocalProvider(LocalShared provides this) {
          transition.AnimatedContent(Modifier.fillMaxSize(), transitionSpec = {
            if (initialState.tab != targetState.tab) EnterTransition.None togetherWith ExitTransition.None
            else Motion.pageEnter togetherWith Motion.pageExit using SizeTransform(clip = false) }) { sc ->
            CompositionLocalProvider(LocalAnimScope provides this) {
              val clean = state.copy(historyFilter = "", mediaFilter = "Tudo", albumFilter = "", historyView = "Tudo")
              when (val r = sc.route) {
                is Route.Album -> CollectionScreen(clean.copy(albumFilter = r.name), navActions, nav.list(r.key, initialCollectionItem), Modifier.fillMaxSize(), pop, dismiss)
                is Route.Playlist -> CollectionScreen(clean.copy(historyView = r.name), navActions, nav.list(r.key, initialCollectionItem), Modifier.fillMaxSize(), pop, dismiss)
                is Route.Smart -> smartFolder(clean, r.kind, r.value).let { f -> if (f != null) CollectionScreen(clean, navActions, nav.list(r.key, initialCollectionItem), Modifier.fillMaxSize(), pop, dismiss, f)
                  else Box(Modifier.fillMaxSize()) { EmptyState(androidx.compose.material.icons.Icons.Rounded.AutoAwesome, "Coleção vazia", "As faixas mudaram desde a última análise.") } }
                is Route.Artist -> ArtistScreen(clean, navActions, r.name, nav.list(r.key, initialCollectionItem), Modifier.fillMaxSize(), pop, dismiss)
                Route.Downloads -> Column(Modifier.fillMaxSize()) {
                  Row(Modifier.fillMaxWidth().height(TopBarHeight).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(pop) { Icon(Icons.Rounded.ArrowBack, "Voltar") }
                    Text("Downloads", style = MaterialTheme.typography.titleLarge) }
                  DownloadHub(state, navActions, Modifier.weight(1f).fillMaxWidth())
                }
                null -> Column(Modifier.fillMaxSize()) {
                  Row(Modifier.fillMaxWidth().padding(start = 24.dp, end = 16.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("rise", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge, letterSpacing = (-1).sp)
                    RoundIconButton(Icons.Rounded.SystemUpdate, "Verificar atualizações", actions.updates, container = c.background)
                    Spacer(Modifier.width(8.dp))
                    RoundIconButton(Icons.Rounded.Tune, "Ajustes", { actions.settings(true) }, container = c.background)
                  }
                  if (state.status.isNotBlank() && state.status != "Nada encontrado") Text(state.status, Modifier.padding(horizontal = 24.dp, vertical = 4.dp), color = c.onSurfaceVariant, fontSize = 12.sp, maxLines = 2)
                  when (sc.tab) {
                    "Buscar" -> MainPane(state, navActions, targets, null, Modifier.weight(1f).fillMaxWidth())
                    "Biblioteca" -> LibraryHome(state.copy(albumFilter = "", historyView = state.historyView.takeIf { it !in state.playlists } ?: "Tudo"), navActions,
                      Modifier.weight(1f).fillMaxWidth(), nav.list("root:Biblioteca"), nav.list("row:lib-al") to nav.list("row:lib-pl"), false, nav.list("row:lib-ar"))
                    else -> LibraryHome(clean.copy(sort = "Recentes"), navActions, Modifier.weight(1f).fillMaxWidth(), nav.list("root:Início"),
                      nav.list("row:home-al") to nav.list("row:home-pl"), true, nav.list("row:home-ar"))
                  }
                }
              }
            }
          }
        }
      }
      NavigationDock(tab, navActions, Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
        .graphicsLayer { scaleX = targets.bounce.value; scaleY = targets.bounce.value }
        .onGloballyPositioned { targets.history = it.positionInRoot() + androidx.compose.ui.geometry.Offset(it.size.width / 6f, it.size.height / 2f) })
    }
  }
}

@Composable private fun NavigationDock(tab: String, a: Actions, modifier: Modifier) {
  val c = MaterialTheme.colorScheme
  val haptics = rememberHaptics()
  // Barra de vidro: translúcida, com borda de luz em cima.
  val edge = androidx.compose.ui.graphics.Brush.verticalGradient(listOf(c.onSurface.copy(alpha = .22f), c.onSurface.copy(alpha = .04f)))
  Row(modifier.clip(RoundedCornerShape(32.dp)).background(c.surface.copy(alpha = .78f)).border(1.dp, edge, RoundedCornerShape(32.dp)).padding(5.dp)) {
    Tabs.forEach { page ->
      val on = tab == page
      val src = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
      Column(Modifier.weight(1f).springPress(src).clip(RoundedCornerShape(28.dp)).background(if (on) c.surfaceVariant else androidx.compose.ui.graphics.Color.Transparent)
        .clickable(src, null) { haptics.tick(); a.page(page) }.padding(vertical = 9.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(when (page) { "Biblioteca" -> Icons.Rounded.LibraryMusic; "Buscar" -> Icons.Rounded.Search; else -> Icons.Rounded.Home }, null,
          tint = if (on) c.onSurface else c.onSurfaceVariant, modifier = Modifier.size(23.dp))
        Text(page, color = if (on) c.onSurface else c.onSurfaceVariant, fontSize = 11.sp, maxLines = 1)
      }
    }
  }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable private fun MainPane(state: UiState, a: Actions, targets: FlyTargets, onHistory: (() -> Unit)?, modifier: Modifier) {
  val c = MaterialTheme.colorScheme
  val keyboard = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
  val focus = androidx.compose.ui.platform.LocalFocusManager.current
  var typed by remember { mutableStateOf(false) }
  val q = state.query.trim()
  val isLink = com.rise.musique.Engine.isLink(q)
  // Busca ao vivo: links na hora; texto depois de 600 ms sem digitar (só quando foi o usuário que digitou).
  LaunchedEffect(state.query, typed) {
    if (!typed || q.isBlank()) return@LaunchedEffect
    kotlinx.coroutines.delay(if (isLink) 150 else 600)
    if (isLink || q.length >= 3) { typed = false; a.searchLive() }
  }
  val local = remember(q, state.history, state.playlists, state.hidden) { localMatches(state, q) }
  val hideKeyboard = remember { object : androidx.compose.ui.input.nestedscroll.NestedScrollConnection {
    override fun onPreScroll(available: androidx.compose.ui.geometry.Offset, source: androidx.compose.ui.input.nestedscroll.NestedScrollSource): androidx.compose.ui.geometry.Offset {
      if (source == androidx.compose.ui.input.nestedscroll.NestedScrollSource.UserInput && kotlin.math.abs(available.y) > 2f) { keyboard?.hide(); focus.clearFocus() }
      return androidx.compose.ui.geometry.Offset.Zero } } }
  Column(modifier.background(c.background).padding(horizontal = 24.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      Text("Buscar", Modifier.weight(1f), style = MaterialTheme.typography.headlineLarge)
      val active = state.queue.count { it.error == null && !it.completed }
      BadgedBox({ if (active > 0) Badge(containerColor = c.onSurface, contentColor = c.surface) { Text("$active") } }) {
        RoundIconButton(Icons.Rounded.Download, "Downloads", a.openDownloads) }
    }
    Row(Modifier.fillMaxWidth().clip(CircleShape).background(c.surfaceVariant).padding(start = 18.dp, end = 6.dp, top = 4.dp, bottom = 4.dp),
      verticalAlignment = Alignment.CenterVertically) {
      Icon(Icons.Rounded.Search, null, tint = c.onSurfaceVariant)
      TextField(state.query, { typed = true; a.query(it) }, Modifier.weight(1f), singleLine = true,
        placeholder = { Text("Músicas, artistas ou um link", color = c.onSurfaceVariant) },
        trailingIcon = { if (state.query.isNotEmpty()) IconButton({ typed = false; a.query("") }) { Icon(Icons.Rounded.Close, "Limpar busca") } },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search), keyboardActions = KeyboardActions(onSearch = { typed = false; keyboard?.hide(); a.search() }),
        colors = TextFieldDefaults.colors(focusedContainerColor = c.surfaceVariant, unfocusedContainerColor = c.surfaceVariant,
          focusedIndicatorColor = c.surfaceVariant, unfocusedIndicatorColor = c.surfaceVariant))
      RoundIconButton(if (state.voice?.listening == true) Icons.Rounded.MicOff else Icons.Rounded.Mic, if (state.voice?.listening == true) "Parar de ouvir" else "Buscar por voz",
        { keyboard?.hide(); if (state.voice?.listening == true) a.voiceStop() else a.voiceStart() }, container = c.surfaceVariant)
      if (state.searching) CircularProgressIndicator(Modifier.size(44.dp).padding(10.dp), strokeWidth = 2.dp, color = c.onSurface)
      else RoundIconButton(Icons.Rounded.ArrowForward, "Buscar", a.search, filled = state.query.isNotBlank())
    }
    if (!isLink) SearchFilters(state, a)
    val resultsList = androidx.compose.foundation.lazy.rememberLazyListState()
    val nearEnd by remember { derivedStateOf { val li = resultsList.layoutInfo; li.totalItemsCount > 0 && (li.visibleItemsInfo.lastOrNull()?.index ?: 0) >= li.totalItemsCount - 4 } }
    LaunchedEffect(nearEnd, state.moreResults, state.loadingMore, state.results.size) { if (nearEnd && state.moreResults && !state.loadingMore && !state.searching) a.moreResults() }
    LazyColumn(Modifier.weight(1f).nestedScroll(hideKeyboard), state = resultsList, verticalArrangement = Arrangement.spacedBy(12.dp)) {
      state.voice?.let { v -> item(key = "voice") { VoicePanel(v, a) } }
      if (isLink && state.results.isEmpty()) item(key = "link") { Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(c.surfaceVariant)
          .clickable { keyboard?.hide(); a.search() }.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Rounded.Link, null); Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) { Text("Link detectado", style = MaterialTheme.typography.titleMedium)
          Text(if (state.searching) "Abrindo…" else "Toque para abrir este link", color = c.onSurfaceVariant, fontSize = 13.sp) }
        if (state.searching) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = c.onSurface) else Icon(Icons.Rounded.ArrowForward, null)
      } }
      if (q.isNotBlank() && !isLink && !local.isEmpty) {
        item(key = "local-title") { Text("Na sua biblioteca", style = MaterialTheme.typography.titleLarge) }
        items(local.tracks, key = { "lt:" + it.uri }) { h -> TrackRow(h, state, a) }
        items(local.albums, key = { "la:" + it.first }) { (name, h) -> LocalRow(h, name, "Álbum", false) { a.openAlbum(name) } }
        items(local.artists, key = { "lr:" + it.first }) { (name, h) -> LocalRow(h, name, "Artista", true) { a.openArtist(name) } }
        items(local.playlists, key = { "lp:" + it.first }) { (name, h) -> LocalRow(h, name, "Playlist", false) { a.openPlaylist(name) } }
      }
      if (q.isNotBlank() && !isLink && (state.searching || state.results.isNotEmpty()) && state.radioOf == null) item(key = "yt-title") {
        Row(verticalAlignment = Alignment.CenterVertically) { Text("No YouTube", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
          if (state.searching) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = c.onSurface) } }
      state.radioOf?.let { seed -> item { Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Rounded.Radio, null, tint = c.onSurface); Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) { Text("Rádio", style = MaterialTheme.typography.titleLarge)
          Text("Parecidas com $seed", color = c.onSurfaceVariant, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        if (state.results.isNotEmpty()) PillButton(if (state.selected.size == state.results.size) "Desmarcar" else "Baixar todas", a.selectAll, filled = false)
      } } }
      if (state.selected.isNotEmpty()) item { DownloadOptions(state, a) }
      if (state.results.size > 1 && state.isPlaylist) item { Row(verticalAlignment = Alignment.CenterVertically) {
        Text("${state.results.size} faixas · ${state.selected.size} marcadas", Modifier.weight(1f), color = c.onSurfaceVariant, fontSize = 13.sp)
        TextButton(a.selectAll) { Text(if (state.selected.size == state.results.size) "Desmarcar" else "Marcar todas") }
      } }
      if (state.searching && state.results.isEmpty() && !isLink) items(6, key = { "sk$it" }) { SkeletonRow(state.searchKind) }
      itemsIndexed(state.results, key = { _, it -> it.url }) { i, r ->
        Box(Modifier.cascade(i)) {
        if (!r.playable) CollectionResult(r) { keyboard?.hide(); if (r.kind == "artist") a.openArtist(r.title) else a.openResult(r) }
        else ResultCard(r, r.url in state.selected, state.isPlaylist, state.previewing == r.url, state.previewing == r.url && state.previewLoading,
          { a.select(r) }, { a.preview(r) }, { a.radio(r) }, if (r.kind == "video" || state.searchKind == com.rise.musique.SearchKind.Videos) ({ a.playVideo(r) }) else null) }
      }
      if (state.moreResults) item(key = "more") { Box(Modifier.fillMaxWidth().padding(8.dp), contentAlignment = Alignment.Center) {
        if (state.loadingMore) CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp, color = c.onSurface)
        else TextButton(a.moreResults) { Text("Carregar mais", color = c.onSurface) } } }
      if (state.results.isEmpty() && !state.searching) {
        if (q.isNotBlank() && state.status == "Nada encontrado" && local.isEmpty) item(key = "none") {
          EmptyState(Icons.Rounded.SearchOff, "Nada encontrado para “$q”", "Confira a grafia, tente o nome do artista ou cole um link.") }
        else if (q.isBlank()) item(key = "hint") { Text("Um nome, artista ou link. Encontre o que quer ouvir.", color = c.onSurfaceVariant, modifier = Modifier.padding(vertical = 8.dp)) }
        if (q.isBlank() && state.recentSearches.isNotEmpty()) {
          item(key = "recent-title") { Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Buscas recentes", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
            TextButton(a.clearSearches) { Text("Limpar tudo") }
          } }
          item(key = "recent") { FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            state.recentSearches.forEach { query ->
              InputChip(false, { typed = false; a.query(query); a.search() }, label = { Text(query, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                leadingIcon = { Icon(Icons.Rounded.History, null, Modifier.size(18.dp)) },
                trailingIcon = { Icon(Icons.Rounded.Close, "Remover $query", Modifier.size(18.dp).clickable { a.removeSearch(query) }) },
                modifier = Modifier.heightIn(min = 40.dp))
            } } }
        }
      }
    }
    val activeJobs = state.queue.count { it.error == null && !it.completed }
    if (state.selected.isNotEmpty()) PillButton(modifier = Modifier.fillMaxWidth().onGloballyPositioned { targets.source = it.positionInRoot() + androidx.compose.ui.geometry.Offset(it.size.width / 2f, it.size.height / 2f) },
      text = when { state.selected.size > 1 -> "Baixar ${state.selected.size} faixas"; activeJobs == 0 -> "Baixar"
        else -> "Adicionar à fila ($activeJobs)" }, onClick = a.download, icon = Icons.Rounded.Download, enabled = state.selected.isNotEmpty() && state.choices.ready(state.mode, state.format))
    if (state.now != null) Spacer(Modifier.height(88.dp))
  }
}

private fun fmt(s: Int) = if (s <= 0) "" else "%d:%02d".format(s / 60, s % 60)

@Composable internal fun TimeField(label: String, v: String, hint: String, m: Modifier, on: (String) -> Unit) {
  val c = MaterialTheme.colorScheme
  OutlinedTextField(v, { t -> on(t.filter { it.isDigit() || it == ':' }.take(8)) }, m, singleLine = true, label = { Text(label) },
    placeholder = { Text(hint) }, shape = CircleShape,
    keyboardOptions = KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = c.primary, unfocusedBorderColor = c.outline, focusedLabelColor = c.onSurface))
}

@Composable private fun ResultCard(r: SearchResult, selected: Boolean, multi: Boolean, playing: Boolean, loading: Boolean,
                                   onSelect: () -> Unit, onPreview: () -> Unit, onRadio: () -> Unit = {}, onVideo: (() -> Unit)? = null) {
  val c = MaterialTheme.colorScheme
  val src = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
  Row(Modifier.fillMaxWidth().weightedPress(src, 22.dp).clip(RoundedCornerShape(22.dp))
      .background(if (selected) c.surfaceVariant else c.background).border(1.dp, if (selected) c.outline else c.background, RoundedCornerShape(22.dp))
      .clickable(src, null, onClick = onSelect).padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
    if (multi) { Icon(if (selected) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked, null,
      tint = if (selected) c.primary else c.onSurfaceVariant, modifier = Modifier.padding(start = 4.dp, end = 10.dp).size(22.dp)) }
    Box(Modifier.size(width = if (r.kind == "song") 63.dp else 112.dp, height = 63.dp).clip(RoundedCornerShape(14.dp)).background(c.outline)) {
      AsyncImage(r.thumb, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
      if (r.duration > 0) Text(fmt(r.duration), Modifier.align(Alignment.BottomEnd).padding(4.dp).clip(CircleShape)
        .background(androidx.compose.ui.graphics.Color(0xAA000000)).padding(horizontal = 6.dp, vertical = 1.dp),
        color = androidx.compose.ui.graphics.Color.White, fontSize = 10.sp)
    }
    Spacer(Modifier.width(12.dp))
    Column(Modifier.weight(1f)) {
      Text(r.title, color = c.onSurface, fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
      Text(r.subtitle.ifBlank { r.channel }, color = c.onSurfaceVariant, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
    Spacer(Modifier.width(8.dp))
    if (onVideo != null) RoundIconButton(Icons.Rounded.SmartDisplay, "Assistir vídeo", onVideo, container = c.background)
    else RoundIconButton(Icons.Rounded.Radio, "Rádio desta música", onRadio, container = c.background)
    Spacer(Modifier.width(4.dp))
    if (loading) CircularProgressIndicator(Modifier.size(44.dp).padding(11.dp), strokeWidth = 2.dp, color = c.onSurface)
    else RoundIconButton(if (playing) Icons.Rounded.Stop else Icons.Rounded.PlayArrow, if (playing) "Parar" else "Tocar", onPreview, container = c.background)
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
    prog.animateTo(1f, Motion.enter(Motion.ExtraLong))
    done = true
    t.bounce.snapTo(1.08f); t.bounce.animateTo(1f, Motion.press())
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
  val all = if (state.hidden.isEmpty()) state.history else state.history.filter { it.uri !in state.hidden }
  val base = when (state.historyView) { "Tudo" -> all; "Favoritos" -> all.filter { it.uri in state.favorites }
    else -> state.playlists[state.historyView].orEmpty().mapNotNull { u -> all.firstOrNull { it.uri == u } } }
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


/** Instant local matches for the unified search. */
data class LocalMatches(val tracks: List<HistoryItem>, val albums: List<Pair<String, HistoryItem>>, val artists: List<Pair<String, HistoryItem>>,
                        val playlists: List<Pair<String, HistoryItem>>) { val isEmpty get() = tracks.isEmpty() && albums.isEmpty() && artists.isEmpty() && playlists.isEmpty() }

fun localMatches(s: UiState, q: String): LocalMatches {
  if (q.isBlank()) return LocalMatches(emptyList(), emptyList(), emptyList(), emptyList())
  val all = s.history.filter { it.uri !in s.hidden }
  fun has(t: String) = t.contains(q, ignoreCase = true)
  val tracks = all.filter { has(it.title) || has(it.artist) }.take(4)
  val albums = all.filter { it.album.isNotBlank() && has(it.album) }.groupBy { it.album }.map { (n, t) -> n to (t.firstOrNull { it.art.isNotBlank() } ?: t.first()) }.take(3)
  val artists = all.filter { it.artist.isNotBlank() && has(it.artist) }.groupBy { it.artist }.map { (n, t) -> n to (t.firstOrNull { it.art.isNotBlank() } ?: t.first()) }.take(3)
  val lists = s.playlists.keys.filter(::has).map { n -> n to (s.playlists[n].orEmpty().firstNotNullOfOrNull { u -> all.firstOrNull { it.uri == u } } ?: HistoryItem(n, "", "pl:$n")) }.take(3)
  return LocalMatches(tracks, albums, artists, lists)
}

@Composable private fun LocalRow(h: HistoryItem, title: String, kind: String, circle: Boolean, onClick: () -> Unit) {
  val c = MaterialTheme.colorScheme
  val src = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
  Row(Modifier.fillMaxWidth().weightedPress(src, 16.dp).clip(RoundedCornerShape(16.dp)).clickable(src, null, onClick = onClick).padding(vertical = 4.dp),
    verticalAlignment = Alignment.CenterVertically) {
    Cover(h, Modifier.size(60.dp), if (circle) 30.dp else 14.dp); Spacer(Modifier.width(14.dp))
    Column(Modifier.weight(1f)) { Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
      Text(kind, color = c.onSurfaceVariant, fontSize = 13.sp) }
    Icon(Icons.Rounded.ChevronRight, null, tint = c.onSurfaceVariant)
  }
}

/** Snackbar "Desfazer": glass pill above the mini player / dock; the real deletion waits ~5 s. */
@Composable fun UndoBar(state: UiState, a: Actions, modifier: Modifier) {
  val c = MaterialTheme.colorScheme
  val u = state.undo
  androidx.compose.animation.AnimatedVisibility(u != null, modifier.navigationBarsPadding().padding(bottom = if (state.now != null) 168.dp else 92.dp, start = 16.dp, end = 16.dp),
    enter = fadeIn(Motion.enter()) + slideInVertically(Motion.enter()) { it / 2 }, exit = fadeOut(Motion.exit())) {
    val last = remember { mutableStateOf(u) }; if (u != null) last.value = u
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(c.inverseSurface).padding(start = 20.dp, end = 8.dp).heightIn(min = 56.dp),
      verticalAlignment = Alignment.CenterVertically) {
      Text(last.value?.message.orEmpty(), Modifier.weight(1f), color = c.inverseOnSurface, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
      TextButton(a.undo, Modifier.heightIn(min = 48.dp)) { Text("Desfazer", color = c.inverseOnSurface, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold) }
    }
  }
}
