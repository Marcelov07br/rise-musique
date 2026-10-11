package com.rise.musique
import android.app.Application
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.compose.runtime.*
import androidx.core.content.FileProvider
import androidx.lifecycle.*
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.*
import androidx.core.content.ContextCompat
import android.content.ComponentName
import androidx.work.*
import com.rise.musique.ui.*
import kotlinx.coroutines.*
import java.io.File

@androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])
class MainViewModel(app: Application) : AndroidViewModel(app) {
  private val prefs = app.getSharedPreferences("rise", 0)
  private var controller: MediaController? = null
  private var controllerFuture: com.google.common.util.concurrent.ListenableFuture<MediaController>? = null
  private var cleared = false
  private val pending = mutableListOf<(MediaController) -> Unit>()
  private fun withPlayer(block: (MediaController) -> Unit) { controller?.let(block) ?: pending.add(block) }
  private var lyricsFor: String? = null
  private var seenHistory = false
  private var previewJob: Job? = null
  private var lastLink: String? = null
  private var searchTerm = ""
  private var searchFetched = 0
  private val ctx get() = getApplication<Application>()
  var state by mutableStateOf(UiState(folder = prefs.getString("tree", null)?.let(Uri::parse), history = HistoryStore.load(app),
    theme = prefs.getString("theme", "Sistema")!!, crossfade = prefs.getInt("crossfade", 0), automix = prefs.getBoolean("automix", false),
    lastfmUser = LastFm.user(app), lastfmOn = LastFm.enabled(app), lastfmConfigured = LastFm.configured, sort = prefs.getString("sort", "Recentes")!!,
    recentSearches = recentSearches(), format = prefs.getString("format", "MP3")!!, favorites = Library.favorites(app), playlists = Library.playlists(app),
    normalize = prefs.getBoolean("normalize", true), normalizeAlbum = prefs.getBoolean("normalizeAlbum", false), lockLyrics = prefs.getBoolean("lockLyrics", false),
    wifiOnly = prefs.getBoolean("wifiOnly", false), followed = FollowStore.all(app), artistPhotos = loadPhotos(app.getSharedPreferences("rise", 0))).let { st -> val m = runCatching { Mode.valueOf(prefs.getString("mode", "Musica")!!) }.getOrDefault(Mode.Musica)
      st.copy(mode = m, quality = prefs.getString("quality_${m.name}", m.qualities.last())!!) })
    private set

  init {
    viewModelScope.launch { state = state.copy(stats = withContext(Dispatchers.IO) { runCatching { PlayStats.summarize(PlayStats.load(app)) }.getOrNull() }) }
    refreshTrackInfo(); LibraryScanWorker.schedule(app); if (FollowStore.all(app).isNotEmpty()) FollowWorker.schedule(app)
    viewModelScope.launch { Engine.update(app) }
    val fut = MediaController.Builder(app, SessionToken(app, ComponentName(app, PlaybackService::class.java))).buildAsync()
    controllerFuture = fut
    fut.addListener({ if (cleared) return@addListener
      runCatching { fut.get() }.onSuccess { c -> controller = c
        c.addListener(object : Player.Listener { override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
          val id = c.currentMediaItem?.mediaId
          if (id != null && id.startsWith("preview:") && previewRetried != id) { previewRetried = id; retryPreview(id.removePrefix("preview:")); return }
          state = state.copy(status = "Não foi possível tocar este arquivo: ${error.errorCodeName}", now = state.now?.copy(loading = false, playing = false))
        } })
        pending.forEach { it(c) }; pending.clear() }
      .onFailure { state = state.copy(status = "Player indisponível: ${it.message}") } }, ContextCompat.getMainExecutor(app))
    viewModelScope.launch { while (true) { delay(250); poll(); video.poll() } }
    viewModelScope.launch {
      WorkManager.getInstance(app).getWorkInfosByTagFlow(DownloadWorker.TAG).collect { infos ->
        val q = infos.mapNotNull { w ->
          val id = w.id.toString(); val data = Jobs.get(app, id) ?: return@mapNotNull null
          when {
            !w.state.isFinished -> QueueItem(id, data.getString("title").orEmpty(), w.progress.getFloat("p", 0f),
              w.progress.getBoolean("running", false), stage = w.progress.getString("stage") ?: "Aguardando")
            w.state == WorkInfo.State.FAILED -> QueueItem(id, data.getString("title").orEmpty(), 0f, false,
              error = w.outputData.getString("err") ?: "Falhou")
            w.state == WorkInfo.State.SUCCEEDED -> QueueItem(id, data.getString("title").orEmpty(), 1f, false, stage = "Concluído", completed = true)
            else -> null
          } }
        val h = HistoryStore.load(app)
        val fresh = h.firstOrNull()?.takeIf { f -> state.history.isNotEmpty() && state.history.none { it.uri == f.uri && it.title == f.title } || (state.history.isEmpty() && h.isNotEmpty() && seenHistory) }
        seenHistory = true
        state = state.copy(queue = q.sortedBy { if (it.running) 0 else if (it.completed) 3 else if (it.error != null) 2 else 1 }, history = h, fly = fresh?.let { FlyEvent(System.nanoTime(), it.art) } ?: state.fly)
      }
    }
  }

  fun onQuery(q: String) { state = state.copy(query = q) }
  fun onMode(m: Mode) { val q = prefs.getString("quality_${m.name}", m.qualities.last())!!
    prefs.edit().putString("mode", m.name).apply(); state = state.copy(mode = m, quality = q, choices = state.choices.chooseMedia()) }
  fun onQuality(q: String) { prefs.edit().putString("quality_${state.mode.name}", q).apply(); state = state.copy(quality = q, choices = state.choices.chooseQuality()) }
  fun onFormat(f: String) { prefs.edit().putString("format", f).apply(); state = state.copy(format = f, choices = state.choices.chooseFormat()) }
  fun onCut(on: Boolean, start: String, end: String) { state = state.copy(cutOn = on, cutStart = start, cutEnd = end) }
  fun onFolder(uri: Uri?) { uri ?: return
    runCatching {
      ctx.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
      prefs.edit().putString("tree", uri.toString()).apply(); state = state.copy(folder = uri)
    }.onFailure { state = state.copy(status = "Não foi possível autorizar essa pasta. Escolha outra pasta.") } }

  fun onIncomingLink(text: String?) {
    val link = text?.let { Regex("https?://\\S+").find(it)?.value } ?: return
    if (link == lastLink) return
    lastLink = link; dlLink(link); state = state.copy(dlRequest = state.dlRequest + 1)
  }

  /** Share a 9:16 card (any track) or a 15 s MP4 with audio (local files only) of the range starting at [startMs]. */
  fun shareSnippet(video: Boolean, startMs: Long, open: (Intent) -> Unit) {
    val n = state.now ?: return
    if (state.snippetBusy) return
    state = state.copy(snippetBusy = true)
    viewModelScope.launch {
      runCatching {
        val start = Snippet.clampStart(startMs, n.duration)
        val lines = Snippet.linesIn(n.lyrics, start)
        val cover = runCatching { val res = coil.ImageLoader(ctx).execute(coil.request.ImageRequest.Builder(ctx).data(if (n.art.startsWith("/")) File(n.art) else n.art)
          .size(720).allowHardware(false).build()); (res.drawable as? android.graphics.drawable.BitmapDrawable)?.bitmap }.getOrNull()
        val local = state.history.firstOrNull { it.uri == n.key }?.takeIf { !n.streaming && !n.isPreview }
        val file = if (video && local != null) Snippet.video(ctx, uriOf(local) ?: error("arquivo não encontrado"), start, n.title, n.artist, cover, lines)
          else Snippet.saveCard(ctx, withContext(Dispatchers.Default) { Snippet.card(n.title, n.artist, cover, lines.map { it.text }) })
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", file)
        open(Intent.createChooser(Intent(Intent.ACTION_SEND).setType(if (file.extension == "mp4") "video/mp4" else "image/png")
          .putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), "Compartilhar trecho"))
      }.onFailure { if (it is CancellationException) throw it; state = state.copy(status = "Não deu pra criar o trecho: ${it.message?.take(80)}") }
      state = state.copy(snippetBusy = false)
    }
  }

  /** Launcher shortcut "Tocar aleatório": the whole local library, shuffled. */
  fun shuffleAll() {
    val pool = state.history.filter { !it.video && it.uri !in state.hidden }.shuffled().take(300)
    val items = pool.mapNotNull { e -> uriOf(e)?.let { u -> MediaItem.Builder().setMediaId(e.uri).setUri(u).setMediaMetadata(meta(e.title, e.artist.ifBlank { e.meta }, e.art, e.album)).build() } }
    if (items.isEmpty()) { state = state.copy(status = "Sua biblioteca está vazia"); return }
    stopPreview(); withPlayer { p -> p.setMediaItems(items, 0, 0); p.prepare(); p.play() }
  }
  fun openDownloads() { state = state.copy(dlRequest = state.dlRequest + 1) }

  /** Download page: preselect one search result (per-item button / action menu). */
  fun downloadItem(r: SearchResult) { state = state.copy(dlItems = listOf(r), dlRequest = state.dlRequest + 1, cutOn = false, cutStart = "", cutEnd = "") }
  fun dlRemove(r: SearchResult) { state = state.copy(dlItems = state.dlItems - r) }
  fun dlQuery(t: String) { state = state.copy(dlQuery = t) }
  private var dlJob: Job? = null
  /** Download page link field: resolves a video/playlist link into the items to download. */
  fun dlLink(text: String) {
    val link = Regex("https?://\\S+").find(text)?.value ?: text.trim().takeIf { it.isNotBlank() } ?: return
    state = state.copy(dlQuery = link, dlLoading = true)
    dlJob?.cancel(); dlJob = viewModelScope.launch {
      runCatching { Engine.search(link) }.onSuccess { r -> state = state.copy(dlLoading = false, dlItems = r.filter { it.playable }.ifEmpty { state.dlItems },
          status = if (r.none { it.playable }) "Nada para baixar nesse link" else "") }
        .onFailure { if (it is CancellationException) throw it; state = state.copy(dlLoading = false, status = "Não deu pra abrir o link: ${it.message?.lineSequence()?.lastOrNull()?.take(80)}") }
    }
  }

  private fun recentSearches(): List<String> = runCatching {
    val a = org.json.JSONArray(prefs.getString("searches", "[]")); List(a.length()) { a.getString(it) }
  }.getOrDefault(emptyList())
  fun eqOpen(open: Boolean) { state = if (open) state.copy(eqOpen = true, eqProfile = EqStore.profileKey(ctx), eq = EqStore.load(ctx, EqStore.profileKey(ctx))) else state.copy(eqOpen = false) }
  /** Saves into the current output's profile; the playback service picks it up instantly (shared prefs listener). */
  fun eqChange(s: EqSettings) { state = state.copy(eq = s); EqStore.save(ctx, state.eqProfile, s) }
  private fun flag(key: String, v: Boolean) = prefs.edit().putBoolean(key, v).apply()
  fun normalize(on: Boolean) { flag("normalize", on); state = state.copy(normalize = on) }
  fun normalizeAlbum(on: Boolean) { flag("normalizeAlbum", on); state = state.copy(normalizeAlbum = on) }
  fun lockLyrics(on: Boolean) { flag("lockLyrics", on); state = state.copy(lockLyrics = on) }
  fun wifiOnly(on: Boolean) { flag("wifiOnly", on); state = state.copy(wifiOnly = on) }
  // ---- 0.20: YouTube Music artist / album pages ----
  private val artistIds = java.util.concurrent.ConcurrentHashMap<String, String>()
  private fun loadPhotos(p: android.content.SharedPreferences): Map<String, String> = runCatching {
    val o = org.json.JSONObject(p.getString("artistPhotos", "{}")!!); o.keys().asSequence().associateWith { o.getString(it) } }.getOrDefault(emptyMap())
  private fun savePhotos() { val o = org.json.JSONObject(); state.artistPhotos.entries.take(500).forEach { (k, v) -> o.put(k, v) }; prefs.edit().putString("artistPhotos", o.toString()).apply() }
  fun artistPage(name: String) {
    if (name in state.artistPages || name in state.artistLoading) return
    state = state.copy(artistLoading = state.artistLoading + name, artistFailed = state.artistFailed - name)
    viewModelScope.launch {
      runCatching { ArtistBrowse.artist(ctx, name, artistIds[name]) }.onSuccess { p ->
        p.similar.forEach { r -> r.url.substringAfter("/channel/", "").takeIf { it.isNotBlank() }?.let { artistIds[r.title] = it } }
        val extra = p.similar.filter { it.thumb.isNotBlank() && it.title !in state.artistPhotos }.associate { it.title to it.thumb }
        state = state.copy(artistPages = state.artistPages + (name to p), artistLoading = state.artistLoading - name,
          artistPhotos = state.artistPhotos + extra + (if (p.image.isNotBlank()) mapOf(name to p.image) else emptyMap()))
        savePhotos()
      }.onFailure { if (it is CancellationException) throw it; state = state.copy(artistLoading = state.artistLoading - name, artistFailed = state.artistFailed + name) }
    }
  }
  fun albumPage(id: String) {
    if (id in state.albumPages || id in state.albumLoading) return
    state = state.copy(albumLoading = state.albumLoading + id)
    viewModelScope.launch {
      val p = runCatching { ArtistBrowse.album(ctx, id) }.onFailure { if (it is CancellationException) throw it }.getOrNull()
      state = state.copy(albumLoading = state.albumLoading - id, albumPages = if (p != null) state.albumPages + (id to p) else state.albumPages,
        status = if (p == null) "Não deu pra abrir o álbum agora. Confira a conexão." else state.status)
    }
  }
  /** Streams a YouTube Music list (top songs / album) as the queue, from [start]. */
  fun playStreams(list: List<SearchResult>, start: Int, shuffle: Boolean) {
    val pl = list.filter { it.playable && Streams.uriFor(it.url) != null }
    val first = list.getOrNull(start)?.let { r -> pl.indexOfFirst { it.url == r.url } }?.coerceAtLeast(0) ?: 0
    val items = pl.mapNotNull { streamItem(it) }
    if (items.isEmpty()) { state = state.copy(status = "Não dá pra tocar esta lista agora"); return }
    stopPreview()
    val idx = if (shuffle) items.indices.random() else first.coerceIn(0, items.lastIndex); val r = pl[idx]
    state = state.copy(now = NowPlaying("stream:" + r.url, r.title, r.channel, r.thumb, loading = true, streaming = true))
    withPlayer { p -> p.setMediaItems(items, idx, 0); p.shuffleModeEnabled = shuffle; p.prepare(); p.play() }
  }
  /** "Baixar tudo": the whole list goes to the Downloads page, preselected. */
  fun downloadAll(list: List<SearchResult>) { val l = list.filter { it.playable }; if (l.isEmpty()) return
    state = state.copy(dlItems = l, dlRequest = state.dlRequest + 1, cutOn = false, cutStart = "", cutEnd = "") }
  private fun pools(): List<List<SearchResult>> = listOf(state.results) + state.popular.values +
    state.artistPages.values.flatMap { listOf(it.top, it.videos) } + state.albumPages.values.map { it.tracks }

  fun follow(name: String) { FollowStore.toggle(ctx, name); state = state.copy(followed = FollowStore.all(ctx)) }
  fun refreshTrackInfo() { viewModelScope.launch { val m = withContext(Dispatchers.IO) { TrackInfoStore.all(ctx) }; state = state.copy(trackInfo = m) } }

  fun removeSearch(q: String) { val list = recentSearches() - q; prefs.edit().putString("searches", org.json.JSONArray(list).toString()).apply(); state = state.copy(recentSearches = list) }
  /** Live (debounced) search: same as search() but doesn't write the query into the history. */
  fun searchLive() = search(record = false)

  // ---- Desfazer: a ação real só acontece depois de ~5 s; até lá o item fica só escondido. ----
  private var undoJob: Job? = null
  private var undoRestore: (() -> Unit)? = null
  private var undoCommit: (suspend () -> Unit)? = null
  private fun withUndo(message: String, hide: () -> Unit, restore: () -> Unit, commit: suspend () -> Unit) {
    flushUndo()
    hide(); undoRestore = restore; undoCommit = commit
    val id = System.nanoTime(); state = state.copy(undo = UndoInfo(id, message))
    undoJob = viewModelScope.launch { delay(5_000); val c = undoCommit; undoCommit = null; undoRestore = null
      if (state.undo?.id == id) state = state.copy(undo = null); c?.invoke() }
  }
  /** Commits a pending action right away (a new one replaces the snackbar). */
  private fun flushUndo() { undoJob?.cancel(); val c = undoCommit; undoCommit = null; undoRestore = null
    if (c != null) viewModelScope.launch { c() }; state = state.copy(undo = null) }
  fun undo() { undoJob?.cancel(); undoCommit = null; undoRestore?.invoke(); undoRestore = null; state = state.copy(undo = null) }

  fun removeFromList(h: HistoryItem, list: String) {
    val before = state.playlists
    withUndo("Removida de $list", { state = state.copy(playlists = before + (list to before[list].orEmpty().filter { it != h.uri })) },
      { state = state.copy(playlists = Library.playlists(ctx)) },
      { Library.removeFromPlaylist(ctx, list, h.uri); state = state.copy(playlists = Library.playlists(ctx)) })
  }
  fun clearQueue() {
    val c = controller ?: return; val from = c.currentMediaItemIndex + 1
    val items = (from until c.mediaItemCount).map { c.getMediaItemAt(it) }; if (items.isEmpty()) return
    c.removeMediaItems(from, c.mediaItemCount)
    withUndo("Fila limpa", {}, { withPlayer { p -> p.addMediaItems((p.currentMediaItemIndex + 1).coerceAtMost(p.mediaItemCount), items) } }, {})
  }
  fun artistPopular(name: String) {
    if (name in state.popular || name in state.popularLoading) return
    state = state.copy(popularLoading = state.popularLoading + name)
    viewModelScope.launch {
      val r = runCatching { Engine.search(name).take(10) }.getOrDefault(emptyList())
      state = state.copy(popular = state.popular + (name to r), popularLoading = state.popularLoading - name)
    }
  }
  /** "Baixar" from an artist's Populares: opens Buscar with just that result selected and the download controls. */
  fun prepareDownload(r: SearchResult) { stopPreview()
    state = state.copy(page = "Buscar", query = r.title, results = listOf(r), selected = setOf(r.url), isPlaylist = false, moreResults = false, radioOf = null) }

  fun clearSearches() { prefs.edit().remove("searches").apply(); state = state.copy(recentSearches = emptyList()) }
  /** Song radio: similar tracks from YouTube Music's mix for this song; shown as selectable results with previews. */
  fun radio(r: SearchResult) = startRadio(r.title) { Engine.radio(r.url) }
  /** Radio from a local track: find it on YouTube first (artist + title), then load its mix. */
  fun radioFor(h: HistoryItem) = startRadio(h.title) {
    val seed = Engine.search(listOf(h.artist, h.title).filter(String::isNotBlank).joinToString(" "), 0, 1).firstOrNull() ?: error("Música não encontrada no YouTube")
    Engine.radio(seed.url) }
  private fun startRadio(title: String, load: suspend () -> List<SearchResult>) {
    if (state.searching) return
    stopPreview(); moreRadio = true
    state = state.copy(page = "Buscar", searching = true, results = emptyList(), selected = emptySet(), moreResults = false, radioOf = title, status = "")
    viewModelScope.launch {
      runCatching { load().also { ensureActive() } }
        .onSuccess { r -> state = state.copy(searching = false, results = r, isPlaylist = true, status = if (r.isEmpty()) "Rádio vazia" else "") }
        .onFailure { if (it is CancellationException) throw it
          state = state.copy(searching = false, radioOf = null, status = "Rádio indisponível: ${it.message?.lineSequence()?.lastOrNull()?.take(100)}") }
    }
  }
  private var moreRadio = false

  private var searchNext: String? = null
  private var searchQ: SearchQuery? = null
  fun searchKind(k: SearchKind) { state = state.copy(searchKind = k); if (state.query.isNotBlank() && !Engine.isLink(state.query)) search(false) }
  fun durationFilter(d: DurationFilter) { state = state.copy(durationFilter = d); if (state.query.isNotBlank() && !Engine.isLink(state.query)) search(false) }
  fun dateFilter(d: DateFilter) { state = state.copy(dateFilter = d, searchKind = if (d != DateFilter.Any) SearchKind.Videos else state.searchKind)
    if (state.query.isNotBlank() && !Engine.isLink(state.query)) search(false) }
  fun search() = search(true)
  private var searchJob: Job? = null
  fun search(record: Boolean) {
    if (state.query.isBlank() || state.loadingMore) return
    if (state.searching) { if (!record) searchJob?.cancel() else return }
    stopPreview(); val query = state.query.trim(); searchTerm = query; searchFetched = 0
    val recent = if (record) (listOf(query) + recentSearches()).distinct().take(12) else recentSearches()
    if (record) prefs.edit().putString("searches", org.json.JSONArray(recent).toString()).apply()
    state = state.copy(page = "Buscar", searching = true, results = emptyList(), selected = emptySet(), moreResults = false, recentSearches = recent, status = "", radioOf = null)
    val link = Engine.isLink(query)
    val q = SearchQuery(query, state.searchKind, state.durationFilter, state.dateFilter)
    searchJob = viewModelScope.launch {
      runCatching { if (link) SearchPage(Engine.search(query), null)
          else runCatching { MusicSearch.search(q) }.getOrElse { if (it is CancellationException) throw it; SearchPage(Engine.search(query), null) } }
        .onSuccess { page -> val r = page.items.also { ensureActive() }; searchFetched = r.size; searchNext = page.next; searchQ = q.takeIf { !link }
          state = state.copy(searching = false, results = r,
          selected = if (r.size == 1 && r[0].playable) setOf(r[0].url) else emptySet(), isPlaylist = Engine.isPlaylist(query) || query.contains("/browse/"),
          moreResults = page.next != null || (link.not() && r.size == 10 && searchQ == null), status = if (r.isEmpty()) "Nada encontrado" else "") }
        .onFailure { if (it is CancellationException) throw it
          state = state.copy(searching = false, status = "Erro na busca: ${it.message?.lineSequence()?.lastOrNull()?.take(100)}") }
    }
  }
  fun moreResults() {
    if (!state.moreResults || state.loadingMore || state.searching) return
    val query = searchTerm; val offset = searchFetched
    state = state.copy(loadingMore = true)
    viewModelScope.launch {
      val q = searchQ; val token = searchNext
      runCatching { if (q != null && token != null) MusicSearch.search(q, token) else SearchPage(Engine.search(query, offset), null) }.onSuccess { page -> val r = page.items
        searchFetched += r.size; searchNext = page.next
        state = state.copy(loadingMore = false, results = (state.results + r).distinctBy { it.url },
          moreResults = if (q != null) page.next != null && searchFetched < 300 else r.size == 10 && searchFetched < 100)
      }.onFailure { if (it is CancellationException) throw it
        state = state.copy(loadingMore = false, status = "Não foi possível carregar mais resultados. Tente novamente.") }
    }
  }

  /** Em playlist marca várias; em busca por nome escolhe uma só. */
  fun select(r: SearchResult) {
    val s = state.selected
    state = state.copy(selected = if (state.isPlaylist) (if (r.url in s) s - r.url else s + r.url) else setOf(r.url))
  }
  fun selectAll() { state = state.copy(selected = if (state.selected.size == state.results.size) emptySet() else state.results.map { it.url }.toSet()) }

  private fun meta(title: String, artist: String, art: String, album: String = "") = MediaMetadata.Builder().setTitle(title).setArtist(artist)
    .setAlbumTitle(album.ifBlank { null })
    .apply { if (art.isNotBlank()) setArtworkUri(if (art.startsWith("/")) Uri.fromFile(File(art)) else Uri.parse(art)) }.build()

  private var previewRetried: String? = null
  private var lastPreview: SearchResult? = null
  private fun retryPreview(url: String) {
    val r = lastPreview?.takeIf { it.url == url } ?: return
    previewJob?.cancel()
    previewJob = viewModelScope.launch { Engine.update(getApplication(), force = true); startPreview(r) }
  }

  /** Plays the whole track from YouTube (not a clip): the visible playable results become the queue, starting at [r]. */
  fun togglePreview(r: SearchResult) {
    if (state.previewing == r.url) { stopPreview(); return }
    if (!r.playable) { openResult(r); return }
    stopPreview()
    val pool = pools().firstOrNull { l -> l.any { it.url == r.url } } ?: listOf(r)
    val list = pool.filter { it.playable && Streams.uriFor(it.url) != null }.ifEmpty { listOf(r) }
    val start = list.indexOfFirst { it.url == r.url }.coerceAtLeast(0)
    val items = list.mapNotNull { streamItem(it) }
    if (items.isEmpty()) { state = state.copy(status = "Não dá pra tocar este link"); return }
    state = state.copy(previewing = r.url, previewLoading = false, now = NowPlaying("stream:" + r.url, r.title, r.channel, r.thumb, loading = true, streaming = true))
    withPlayer { p -> p.setMediaItems(items, start, 0); p.repeatMode = prefs.getInt("repeat", Player.REPEAT_MODE_OFF); p.prepare(); p.play() }
  }
  private fun streamItem(r: SearchResult) = Streams.uriFor(r.url)?.let { u -> MediaItem.Builder().setMediaId("stream:" + r.url).setUri(u)
    .setMediaMetadata(meta(r.title, r.channel, r.thumb, r.album)).build() }
  fun playNextStream(r: SearchResult) = streamItem(r)?.let { item -> withPlayer { p -> if (p.mediaItemCount == 0) { p.setMediaItem(item); p.prepare(); p.play() }
    else p.addMediaItem((p.currentMediaItemIndex + 1).coerceAtMost(p.mediaItemCount), item) } }
  /** Albums / playlists / artists from the search: albums & playlists list their tracks, artists open the artist screen. */
  fun openResult(r: SearchResult) {
    state = state.copy(query = r.url); search(false)
  }

  /** "Guardar": the bytes already played are in the stream cache → saved with tags/cover; otherwise a normal download. */
  val video by lazy { VideoController(ctx, viewModelScope) { v -> state = state.copy(video = v, pip = if (v == null) false else state.pip) } }
  /** Video: the music pauses (its queue stays), the visible videos become the "next" list. */
  fun playVideo(r: SearchResult) { withPlayer { it.pause() }
    val pool = pools().firstOrNull { l -> l.any { it.url == r.url } } ?: listOf(r)
    video.open(r, pool) }
  fun playLocalVideo(h: HistoryItem) { val u = uriOf(h) ?: return; withPlayer { it.pause() }; video.openLocal(h, u) }
  fun voiceDenied() { state = state.copy(voice = VoiceUi(false, error = "Sem permissão do microfone. Libere em Ajustes do Android para buscar por voz.")) }
  fun pip(on: Boolean) { state = state.copy(pip = on) }

  val voice by lazy { VoiceSearch(ctx, { v -> state = state.copy(voice = v) }) { text -> state = state.copy(query = text, page = "Buscar"); search(true) } }

  /** Station: YouTube Music radio of up to three seeds (interleaved), with the station's own local tracks mixed in. */
  fun playStation(st: Station) {
    if (state.stationLoading != null) return
    state = state.copy(stationLoading = st.id)
    viewModelScope.launch {
      runCatching {
        val radios = coroutineScope { st.seeds.take(3).map { h -> async { runCatching {
          val seed = MusicSearch.search(SearchQuery(listOf(h.artist, h.title).filter(String::isNotBlank).joinToString(" "), SearchKind.Songs)).items.firstOrNull()
            ?: return@runCatching emptyList<SearchResult>()
          Engine.radio(seed.url, 25) }.getOrDefault(emptyList()) } }.awaitAll() }
        val known = state.history.map { it.title.lowercase() }.toSet()
        val streams = StationQueue.interleave(radios).filter { st.kind != "discover" || it.title.lowercase() !in known }.take(40)
        val queue = StationQueue.mix(streams, if (st.mixLocal) st.seeds else emptyList()).mapNotNull { e -> when (e) {
          is SearchResult -> streamItem(e)
          is HistoryItem -> uriOf(e)?.let { u -> MediaItem.Builder().setMediaId(e.uri).setUri(u).setMediaMetadata(meta(e.title, e.artist.ifBlank { e.meta }, e.art, e.album)).build() }
          else -> null } }
        if (queue.isEmpty()) error("sem faixas")
        stopPreview()
        withPlayer { p -> p.setMediaItems(queue, 0, 0); p.shuffleModeEnabled = false; p.prepare(); p.play() }
        state = state.copy(stationLoading = null, now = NowPlaying(queue[0].mediaId, queue[0].mediaMetadata.title?.toString().orEmpty(),
          queue[0].mediaMetadata.artist?.toString().orEmpty(), queue[0].mediaMetadata.artworkUri?.toString().orEmpty(), loading = true))
      }.onFailure { if (it is CancellationException) throw it
        state = state.copy(stationLoading = null, status = "Não deu pra montar a estação agora. Confira a conexão.") }
    }
  }

  fun saveStream() {
    val n = state.now ?: return; if (!n.streaming) return
    val url = n.key.removePrefix("stream:"); val id = Engine.videoId(url) ?: return
    state = state.copy(now = n.copy(saveState = "saving"))
    viewModelScope.launch {
      val prefill = withContext(Dispatchers.IO) { val k = Streams.known(id)
        val f = File(ctx.filesDir, "prefill/$id.${k?.ext ?: "m4a"}").apply { parentFile?.mkdirs() }
        if (k != null && Streams.copyCached(ctx, id, f)) f to k.formatId else { f.delete(); null } }
      DownloadWorker.enqueue(ctx, DownloadWorker.data(url, n.title, n.art, n.artist, Mode.Musica, prefs.getString("quality_${Mode.Musica.name}", Mode.Musica.qualities.last())!!,
        state.format, null, state.folder).let { d -> if (prefill == null) d else androidx.work.Data.Builder().putAll(d).putString("prefill", prefill.first.path).putString("formatId", prefill.second).build() })
      state = state.copy(now = state.now?.copy(saveState = if (prefill != null) "cached" else "download"),
        status = if (prefill != null) "Guardando o que já tocou na biblioteca" else "Ainda não tocou inteira: baixando normalmente")
    }
  }
  /** Cut sheet: play exactly the chosen range of the selected result. */
  fun previewRange(startMs: Long, endMs: Long) {
    val r = state.dlItems.firstOrNull() ?: return
    stopPreview(); state = state.copy(previewing = r.url, previewLoading = true,
      now = NowPlaying(r.url, r.title, r.channel, r.thumb, loading = true, isPreview = true))
    lastPreview = r; previewRetried = null
    previewJob = viewModelScope.launch { startPreview(r, startMs, endMs) }
  }
  private var cutJob: Job? = null
  fun openCut(open: Boolean) {
    state = state.copy(cutOpen = open)
    if (!open) { cutJob?.cancel(); return }
    val r = state.dlItems.firstOrNull() ?: return
    val video = state.mode == Mode.Video
    state = state.copy(cutVisual = CutVisual(loading = true))
    cutJob?.cancel(); cutJob = viewModelScope.launch {
      runCatching { withContext(Dispatchers.IO) { val (stream, board) = Engine.cutInfo(r.url)
          if (video) CutVisual(false, storyboard = board) else CutVisual(false, peaks = CutInfo.waveform(stream.url, stream.headers)) } }
        .onSuccess { state = state.copy(cutVisual = it) }
        .onFailure { if (it is CancellationException) throw it; state = state.copy(cutVisual = CutVisual(false, error = "Prévia visual indisponível")) }
    }
  }
  private suspend fun startPreview(r: SearchResult, rangeStart: Long = -1, rangeEnd: Long = -1) = coroutineScope {
      runCatching { Engine.audioStream(r.url).url.also { ensureActive() } }.onSuccess { stream ->
        val start = if (rangeStart >= 0) rangeStart else if (r.duration > 90) 30_000L else 0L
        val end = if (rangeEnd > start) rangeEnd else start + 30_000
        withPlayer { p -> p.setMediaItem(MediaItem.Builder().setMediaId("preview:" + r.url).setUri(stream).setMediaMetadata(meta(r.title, r.channel, r.thumb))
          .setClippingConfiguration(MediaItem.ClippingConfiguration.Builder().setStartPositionMs(start).setEndPositionMs(end).build()).build())
          p.repeatMode = Player.REPEAT_MODE_OFF; p.prepare(); p.play() }
        state = state.copy(previewLoading = false)
      }.onFailure { if (it is CancellationException) throw it
        state = state.copy(previewing = null, previewLoading = false, now = null, status = "Prévia indisponível") }
  }
  private fun stopPreview() { previewJob?.cancel()
    if (state.now?.isPreview == true) { controller?.stop(); controller?.clearMediaItems(); state = state.copy(now = null, playbackQueue = emptyList(), queueOpen = false) }
    state = state.copy(previewing = null, previewLoading = false) }

  /** Toca o histórico como fila, começando pelo item tocado. */
  fun play(h: HistoryItem) {
    val list = visibleHistory(state).ifEmpty { state.history }.mapNotNull { item -> uriOf(item)?.let { u -> item to u } }
    val start = list.indexOfFirst { it.first.uri == h.uri }.takeIf { it >= 0 } ?: return.also {
      state = state.copy(status = "Arquivo indisponível. Um backup de outra instalação não transfere os arquivos de música.") }
    stopPreview()
    val items = list.map { (item, u) -> MediaItem.Builder().setMediaId(item.uri).setUri(u)
      .setMediaMetadata(meta(item.title, item.artist.ifBlank { item.meta }, item.art, item.album)).build() }
    withPlayer { p -> p.setMediaItems(items, start, 0); p.repeatMode = prefs.getInt("repeat", Player.REPEAT_MODE_OFF); p.prepare(); p.play() }
    state = state.copy(now = NowPlaying(h.uri, h.title, h.artist.ifBlank { h.meta }, h.art, loading = true))
  }

  private fun poll() { val p = controller ?: return
    val deadline = p.sessionExtras.getLong(PlaybackService.SLEEP_AT, 0)
    if (deadline != state.sleepAt) state = state.copy(sleepAt = deadline)
    val n = state.now ?: p.currentMediaItem?.let { item ->
      NowPlaying(item.mediaId, item.mediaMetadata.title?.toString().orEmpty(), item.mediaMetadata.artist?.toString().orEmpty(),
        item.mediaMetadata.artworkUri?.toString().orEmpty(), isPreview = item.mediaId.startsWith("preview:"), streaming = item.mediaId.startsWith("stream:"))
    } ?: return
    val item = p.currentMediaItem
    if (item == null) { if (!n.loading && !n.isPreview) state = state.copy(now = null); return }
    val m = item.mediaMetadata; val preview = item.mediaId.startsWith("preview:")
    val key = item.mediaId.removePrefix("preview:")
    var now = n.copy(key = key, title = m.title?.toString() ?: n.title, artist = m.artist?.toString() ?: n.artist,
      art = m.artworkUri?.toString() ?: "", playing = p.isPlaying, position = p.currentPosition.coerceAtLeast(0),
      duration = p.duration.takeIf { it > 0 } ?: n.duration, isPreview = preview, streaming = item.mediaId.startsWith("stream:"),
      loading = p.playbackState == Player.STATE_BUFFERING, hasNext = p.hasNextMediaItem(), hasPrev = p.hasPreviousMediaItem(),
      repeat = p.repeatMode, shuffle = p.shuffleModeEnabled, index = p.currentMediaItemIndex, count = p.mediaItemCount,
      quality = p.sessionExtras.getString(PlaybackService.QUALITY).orEmpty())
    if (key != n.key) now = now.copy(lyrics = null, lyricsState = "", saveState = "")
    if (now.streaming && state.previewing != key.removePrefix("stream:")) state = state.copy(previewing = key.removePrefix("stream:"))
    if (preview && p.playbackState == Player.STATE_ENDED) { stopPreview(); return }
    val queue = if (preview) emptyList() else (0 until p.mediaItemCount).map { i ->
      val track = p.getMediaItemAt(i); val md = track.mediaMetadata
      HistoryItem(md.title?.toString().orEmpty(), "", track.mediaId, md.artworkUri?.toString().orEmpty(), md.artist?.toString().orEmpty())
    }
    state = state.copy(now = now, playbackQueue = queue)
    if (!preview && lyricsFor != key && now.duration > 0) { lyricsFor = key; loadLyrics(key, now.title, now.artist, now.duration / 1000) }
  }

  private fun loadLyrics(key: String, title: String, artist: String, dur: Long) {
    state = state.copy(now = state.now?.copy(lyricsState = "loading"))
    viewModelScope.launch { val l = runCatching { Lyrics.fetch(title, artist, dur) }.getOrNull()
      if (state.now?.key == key) state = state.copy(now = state.now?.copy(lyrics = l, lyricsState = if (l == null) "none" else "ok")) }
  }

  fun playPause() = withPlayer { p -> if (p.isPlaying) p.pause() else { if (p.playbackState == Player.STATE_ENDED) p.seekTo(0); p.play() } }
  fun seek(ms: Long) = withPlayer { it.seekTo(ms) }
  fun next() = withPlayer { it.seekToNextMediaItem() }
  fun prev() = withPlayer { p -> if (p.currentPosition > 3000 || !p.hasPreviousMediaItem()) p.seekTo(0) else p.seekToPreviousMediaItem() }
  /** Album/playlist screen: start the visible list in shuffle mode. */
  fun playShuffled(h: HistoryItem) { play(h); withPlayer { it.shuffleModeEnabled = true } }
  fun queueAll(list: List<HistoryItem>) { list.forEach { addQueue(it) } }
  fun shuffle() = withPlayer { it.shuffleModeEnabled = !it.shuffleModeEnabled }
  fun closePlayer() { sleep(0); previewJob?.cancel(); controller?.stop(); controller?.clearMediaItems(); lyricsFor = null
    state = state.copy(now = null, playbackQueue = emptyList(), queueOpen = false, previewing = null, previewLoading = false) }

  fun page(page: String) { state = state.copy(page = page); if (page == "Biblioteca") refreshTrackInfo() }
  fun showQueue(open: Boolean) { state = state.copy(queueOpen = open) }
  fun repeat() = withPlayer { p ->
    if (p.currentMediaItem?.mediaId?.startsWith("preview:") != true) {
      p.repeatMode = when (p.repeatMode) { Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL; Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE; else -> Player.REPEAT_MODE_OFF }
      prefs.edit().putInt("repeat", p.repeatMode).apply(); poll()
    }
  }
  fun moveQueue(from: Int, to: Int) = withPlayer { p ->
    if (from in 0 until p.mediaItemCount && to in 0 until p.mediaItemCount && from != to) { p.moveMediaItem(from, to); poll() }
  }
  fun removeQueue(index: Int) = withPlayer { p ->
    if (index in 0 until p.mediaItemCount) { p.removeMediaItem(index)
      if (p.mediaItemCount == 0) closePlayer() else poll() }
  }
  fun playQueue(index: Int) = withPlayer { p ->
    if (index in 0 until p.mediaItemCount) { p.seekTo(index, 0); p.prepare(); p.play(); poll() }
  }
  fun addQueue(h: HistoryItem) = queueTrack(h, false)
  fun playNext(h: HistoryItem) = queueTrack(h, true)
  private fun queueTrack(h: HistoryItem, next: Boolean) {
    val uri = uriOf(h) ?: return.also { state = state.copy(status = "Arquivo indisponível") }
    stopPreview()
    val item = MediaItem.Builder().setMediaId(h.uri).setUri(uri).setMediaMetadata(meta(h.title, h.artist.ifBlank { h.meta }, h.art, h.album)).build()
    withPlayer { p ->
      if (p.mediaItemCount == 0) { p.setMediaItem(item); p.repeatMode = prefs.getInt("repeat", Player.REPEAT_MODE_OFF); p.prepare(); p.play() }
      else p.addMediaItem(if (next) p.currentMediaItemIndex + 1 else p.mediaItemCount, item)
      poll()
    }
    state = state.copy(status = if (next) "Vai tocar a seguir" else "Adicionada à fila de reprodução")
  }

  private fun secs(t: String): Int? = t.trim().split(":").map { it.toIntOrNull() ?: return null }
    .fold(0) { acc, n -> acc * 60 + n }.takeIf { t.isNotBlank() }

  fun enqueue() {
    if (!state.choices.ready(state.mode, state.format)) { state = state.copy(status = "Escolha mídia, formato e qualidade antes de baixar"); return }
    val picked = state.dlItems; if (picked.isEmpty()) return
    var cut: String? = null
    if (state.cutOn && picked.size == 1) {
      val a = secs(state.cutStart) ?: 0; val b = secs(state.cutEnd)
      if (b != null && b <= a) { state = state.copy(status = "O fim do corte precisa ser depois do início"); return }
      cut = "$a-${b ?: "inf"}"
    }
    picked.forEach { r -> DownloadWorker.enqueue(ctx, DownloadWorker.data(r.url, r.title, r.thumb, r.channel, state.mode, state.quality,
      if (state.mode == Mode.Musica) state.format else "MP4", cut, state.folder)) }
    state = state.copy(dlItems = emptyList(), dlQuery = "", cutOn = false, cutStart = "", cutEnd = "",
      status = if (picked.size > 1) "${picked.size} itens adicionados à fila" else "")
  }

  fun sort(value: String) { prefs.edit().putString("sort", value).apply(); state = state.copy(sort = value) }
  fun mediaFilter(value: String) { state = state.copy(mediaFilter = value, albumFilter = "") }
  fun albumFilter(value: String) { state = state.copy(albumFilter = value, historyView = "Tudo", historyFilter = "") }
  fun crossfade(seconds: Int) { val v = CrossfadeRules.clampSeconds(seconds); prefs.edit().putInt("crossfade", v).apply(); state = state.copy(crossfade = v) }
  fun stats(open: Boolean) {
    state = state.copy(statsOpen = open, settingsOpen = if (open) false else state.settingsOpen)
    if (open) viewModelScope.launch { state = state.copy(stats = withContext(Dispatchers.IO) { PlayStats.summarize(PlayStats.load(ctx)) }) }
  }
  fun automix(on: Boolean) { prefs.edit().putBoolean("automix", on).apply(); state = state.copy(automix = on) }
  fun recap(open: Boolean) {
    state = state.copy(recapOpen = open, statsOpen = if (open) false else state.statsOpen)
    if (open) viewModelScope.launch { val plays = withContext(Dispatchers.IO) { PlayStats.load(ctx) }
      state = state.copy(recapMonth = RecapBuilder.build(plays, false), recapYear = RecapBuilder.build(plays, true)) }
  }
  /** Story image for the share sheet. */
  fun recapIntent(period: String): Intent? {
    val r = (if (period == "Ano") state.recapYear else state.recapMonth) ?: return null
    val uri = runCatching { RecapImage.share(ctx, r) }.getOrElse { state = state.copy(status = "Não foi possível gerar a imagem"); return null }
    return Intent.createChooser(Intent(Intent.ACTION_SEND).setType("image/png").putExtra(Intent.EXTRA_STREAM, uri)
      .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), "Compartilhar retrospectiva")
  }
  private fun lastfmState() = state.copy(lastfmUser = LastFm.user(ctx), lastfmOn = LastFm.enabled(ctx), lastfmConfigured = LastFm.configured)
  fun lastfmLogin(user: String, password: String) {
    state = state.copy(status = "Entrando no Last.fm…")
    viewModelScope.launch { runCatching { withContext(Dispatchers.IO) { LastFm.login(ctx, user, password) } }
      .onSuccess { state = lastfmState().copy(status = "Last.fm conectado como $it") }
      .onFailure { if (it is CancellationException) throw it; state = state.copy(status = "Last.fm: ${it.message}") } }
  }
  fun lastfmLogout() { LastFm.logout(ctx); state = lastfmState() }
  fun lastfmToggle(on: Boolean) { LastFm.setEnabled(ctx, on); state = lastfmState() }
  fun theme(value: String) { prefs.edit().putString("theme", value).apply(); state = state.copy(theme = value) }
  fun settings(open: Boolean) { state = state.copy(settingsOpen = open) }
  fun importFiles(uris: List<Uri>) = importMedia { uris to false }
  fun importFolder(uri: Uri?) { uri ?: return; importMedia {
    ctx.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
    LocalMedia.scan(ctx, uri)
  } }
  private fun importMedia(source: suspend () -> Pair<List<Uri>, Boolean>) {
    if (state.importing) return
    state = state.copy(importing = true, page = "Biblioteca", status = "Lendo arquivos locais…")
    viewModelScope.launch {
      runCatching { withContext(Dispatchers.IO) {
        val (uris, limited) = source()
        // File picker grants are per document. Folder children use the tree's grant.
        uris.forEach { uri -> runCatching { ctx.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) } }
        LocalMedia.import(ctx, uris, limited)
      } }.onSuccess { report ->
        state = state.copy(importing = false, history = HistoryStore.load(ctx),
          status = "${report.added} adicionados · ${report.repeated} já estavam na biblioteca · ${report.failed} indisponíveis" + if (report.limited) ". Limite da pasta atingido; importe as subpastas separadamente." else "")
      }.onFailure { if (it is CancellationException) throw it
        state = state.copy(importing = false, status = "Não foi possível importar: ${it.message}") }
    }
  }
  fun refreshMetadata() {
    if (state.importing) return
    val tracks = state.history
    state = state.copy(importing = true, status = "Lendo duração, artista e álbum…")
    viewModelScope.launch {
      runCatching { withContext(Dispatchers.IO) {
        tracks.forEach { h -> ensureActive()
          val uri = if (h.uri.startsWith("content://")) Uri.parse(h.uri) else Uri.fromFile(File(h.uri))
          HistoryStore.replace(ctx, LocalMedia.metadata(ctx, uri, h))
        }
      } }.onSuccess { state = state.copy(importing = false, history = HistoryStore.load(ctx), status = "Informações da biblioteca atualizadas") }
        .onFailure { if (it is CancellationException) throw it
          state = state.copy(importing = false, status = "Falha ao ler informações: ${it.message}") }
    }
  }
  fun removeLocal(h: HistoryItem) = withUndo("Removida da biblioteca", { state = state.copy(hidden = state.hidden + h.uri) }, { state = state.copy(hidden = state.hidden - h.uri) }) { removeLocalNow(h) }
  private fun removeLocalNow(h: HistoryItem) { HistoryStore.remove(ctx, h); Library.removeTrack(ctx, h.uri)
    state = state.copy(history = HistoryStore.load(ctx), favorites = Library.favorites(ctx), playlists = Library.playlists(ctx), hidden = state.hidden - h.uri) }
  fun renamePlaylist(old: String, name: String) { runCatching { Library.renamePlaylist(ctx, old, name.trim()) }
    .onSuccess { state = state.copy(playlists = Library.playlists(ctx), historyView = if (state.historyView == old) name.trim() else state.historyView) }
    .onFailure { state = state.copy(status = it.message.orEmpty()) } }
  fun movePlaylist(name: String, from: Int, to: Int) { runCatching { Library.moveTrack(ctx, name, from, to) }
    .onSuccess { state = state.copy(playlists = Library.playlists(ctx)) }.onFailure { state = state.copy(status = it.message.orEmpty()) } }

  fun favorite(h: HistoryItem) { Library.toggleFavorite(ctx, h.uri); state = state.copy(favorites = Library.favorites(ctx)) }
  fun addToPlaylist(h: HistoryItem, name: String) { runCatching { Library.addToPlaylist(ctx, name.trim(), h.uri) }
    .onSuccess { state = state.copy(playlists = Library.playlists(ctx), status = "Adicionada a ${name.trim()}") }
    .onFailure { state = state.copy(status = it.message.orEmpty()) } }
  fun removeFromPlaylist(h: HistoryItem) { Library.removeFromPlaylist(ctx, state.historyView, h.uri); state = state.copy(playlists = Library.playlists(ctx)) }
  fun deletePlaylist(n: String) { Library.deletePlaylist(ctx, n); state = state.copy(playlists = Library.playlists(ctx), historyView = "Tudo") }
  fun historyFilter(q: String) { state = state.copy(historyFilter = q) }
  fun historyView(v: String) { state = state.copy(historyView = v, albumFilter = "") }
  fun exportBackup(to: Uri?) { to ?: return; viewModelScope.launch { runCatching { withContext(Dispatchers.IO) { Library.export(ctx, to) } }
    .onSuccess { state = state.copy(status = "Backup de biblioteca salvo. Os arquivos de música devem ser guardados separadamente.") }.onFailure { state = state.copy(status = "Falha no backup: ${it.message}") } } }
  fun importBackup(from: Uri?) { from ?: return; viewModelScope.launch { runCatching { withContext(Dispatchers.IO) { Library.import(ctx, from) } }
    .onSuccess { n -> state = state.copy(history = HistoryStore.load(ctx), favorites = Library.favorites(ctx), playlists = Library.playlists(ctx),
      status = "Backup importado: $n registros. O backup não inclui músicas nem permissões de outras instalações.") }.onFailure { state = state.copy(status = "Não deu pra importar: ${it.message}") } } }

  /** Temporizador: em `min` minutos baixa o volume aos poucos por 8 s e pausa. 0 cancela. */
  fun sleep(min: Int) = withPlayer { it.sendCustomCommand(SessionCommand(PlaybackService.SLEEP, android.os.Bundle.EMPTY),
    android.os.Bundle().apply { putInt("minutes", min) }) }

  fun cancel(q: QueueItem) = DownloadWorker.cancel(ctx, q.id)
  fun retry(q: QueueItem) { val d = Jobs.get(ctx, q.id) ?: return; Jobs.forget(ctx, q.id); DownloadWorker.enqueue(ctx, d) }
  fun dismiss(q: QueueItem) { Jobs.forget(ctx, q.id); state = state.copy(queue = state.queue - q) }

  private fun uriOf(h: HistoryItem): Uri? = runCatching { when {
    h.uri.startsWith("content://") -> Uri.parse(h.uri)
    h.uri.isNotBlank() && File(h.uri).exists() -> FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", File(h.uri))
    else -> null } }.getOrNull()
  private fun ext(h: HistoryItem) = h.meta.substringBefore(" ·").lowercase()

  /** Devolve a Intent pra Activity abrir (abrir no player ou compartilhar). */
  fun openIntent(h: HistoryItem, share: Boolean): Intent? { val u = uriOf(h) ?: return null.also { state = state.copy(status = "Arquivo não encontrado") }
    val i = if (share) Intent(Intent.ACTION_SEND).setType(Engine.mime(ext(h))).putExtra(Intent.EXTRA_STREAM, u)
            else Intent(Intent.ACTION_VIEW).setDataAndType(u, Engine.mime(ext(h)))
    return Intent.createChooser(i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), null) }

  fun delete(h: HistoryItem) = withUndo("Arquivo apagado", { state = state.copy(hidden = state.hidden + h.uri) }, { state = state.copy(hidden = state.hidden - h.uri) }) { deleteNow(h) }
  private fun deleteNow(h: HistoryItem) {
    viewModelScope.launch {
      runCatching { withContext(Dispatchers.IO) {
        val deleted = if (h.uri.startsWith("content://")) DocumentsContract.deleteDocument(ctx.contentResolver, Uri.parse(h.uri)) else File(h.uri).let { !it.exists() || it.delete() }
        require(deleted) { "O arquivo não pôde ser apagado" }
        HistoryStore.remove(ctx, h); Library.removeTrack(ctx, h.uri)
      } }.onSuccess { state = state.copy(history = HistoryStore.load(ctx), favorites = Library.favorites(ctx), playlists = Library.playlists(ctx), hidden = state.hidden - h.uri) }
        .onFailure { state = state.copy(status = "Falha ao apagar: ${it.message}", hidden = state.hidden - h.uri) }
    }
  }

  override fun onCleared() { video.close(); voice.stop(false); runBlocking { undoJob?.cancel(); undoCommit?.invoke() }; cleared = true; pending.clear(); controllerFuture?.let(MediaController::releaseFuture); super.onCleared() }
}

