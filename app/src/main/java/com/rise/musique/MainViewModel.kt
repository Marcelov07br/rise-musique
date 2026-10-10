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
    theme = prefs.getString("theme", "Sistema")!!, sort = prefs.getString("sort", "Recentes")!!,
    recentSearches = recentSearches(), format = prefs.getString("format", "MP3")!!, favorites = Library.favorites(app), playlists = Library.playlists(app)).let { st -> val m = runCatching { Mode.valueOf(prefs.getString("mode", "Musica")!!) }.getOrDefault(Mode.Musica)
      st.copy(mode = m, quality = prefs.getString("quality_${m.name}", m.qualities.last())!!) })
    private set

  init {
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
    viewModelScope.launch { while (true) { delay(250); poll() } }
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
    lastLink = link; state = state.copy(query = link, page = "Buscar"); search()
  }

  private fun recentSearches(): List<String> = runCatching {
    val a = org.json.JSONArray(prefs.getString("searches", "[]")); List(a.length()) { a.getString(it) }
  }.getOrDefault(emptyList())
  fun clearSearches() { prefs.edit().remove("searches").apply(); state = state.copy(recentSearches = emptyList()) }
  fun search() {
    if (state.query.isBlank() || state.searching || state.loadingMore) return
    stopPreview(); val query = state.query.trim(); searchTerm = query; searchFetched = 0
    val recent = (listOf(query) + recentSearches()).distinct().take(12)
    prefs.edit().putString("searches", org.json.JSONArray(recent).toString()).apply()
    state = state.copy(page = "Buscar", searching = true, results = emptyList(), selected = emptySet(), moreResults = false, recentSearches = recent, status = "")
    viewModelScope.launch {
      runCatching { Engine.search(query).also { ensureActive() } }
        .onSuccess { r -> searchFetched = r.size; state = state.copy(searching = false, results = r,
          selected = if (r.size == 1) setOf(r[0].url) else emptySet(), isPlaylist = Engine.isPlaylist(query),
          moreResults = !Engine.isLink(query) && r.size == 10, status = if (r.isEmpty()) "Nada encontrado" else "") }
        .onFailure { if (it is CancellationException) throw it
          state = state.copy(searching = false, status = "Erro na busca: ${it.message?.lineSequence()?.lastOrNull()?.take(100)}") }
    }
  }
  fun moreResults() {
    if (!state.moreResults || state.loadingMore || state.searching) return
    val query = searchTerm; val offset = searchFetched
    state = state.copy(loadingMore = true)
    viewModelScope.launch {
      runCatching { Engine.search(query, offset).also { ensureActive() } }.onSuccess { r ->
        searchFetched += r.size
        state = state.copy(loadingMore = false, results = (state.results + r).distinctBy { it.url }, moreResults = r.size == 10 && searchFetched < 100)
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

  private fun meta(title: String, artist: String, art: String) = MediaMetadata.Builder().setTitle(title).setArtist(artist)
    .apply { if (art.isNotBlank()) setArtworkUri(Uri.parse(art)) }.build()

  private var previewRetried: String? = null
  private var lastPreview: SearchResult? = null
  private fun retryPreview(url: String) {
    val r = lastPreview?.takeIf { it.url == url } ?: return
    previewJob?.cancel()
    previewJob = viewModelScope.launch { Engine.update(getApplication(), force = true); startPreview(r) }
  }

  fun togglePreview(r: SearchResult) {
    if (state.previewing == r.url) { stopPreview(); return }
    stopPreview(); state = state.copy(previewing = r.url, previewLoading = true,
      now = NowPlaying(r.url, r.title, r.channel, r.thumb, loading = true, isPreview = true))
    lastPreview = r; previewRetried = null
    previewJob = viewModelScope.launch { startPreview(r) }
  }
  private suspend fun startPreview(r: SearchResult) = coroutineScope {
      runCatching { Engine.audioStream(r.url).url.also { ensureActive() } }.onSuccess { stream ->
        val start = if (r.duration > 90) 30_000L else 0L
        withPlayer { p -> p.setMediaItem(MediaItem.Builder().setMediaId("preview:" + r.url).setUri(stream).setMediaMetadata(meta(r.title, r.channel, r.thumb))
          .setClippingConfiguration(MediaItem.ClippingConfiguration.Builder().setStartPositionMs(start).setEndPositionMs(start + 30_000).build()).build())
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
      .setMediaMetadata(meta(item.title, item.artist.ifBlank { item.meta }, item.art)).build() }
    withPlayer { p -> p.setMediaItems(items, start, 0); p.repeatMode = prefs.getInt("repeat", Player.REPEAT_MODE_OFF); p.prepare(); p.play() }
    state = state.copy(now = NowPlaying(h.uri, h.title, h.artist.ifBlank { h.meta }, h.art, loading = true))
  }

  private fun poll() { val p = controller ?: return
    val deadline = p.sessionExtras.getLong(PlaybackService.SLEEP_AT, 0)
    if (deadline != state.sleepAt) state = state.copy(sleepAt = deadline)
    val n = state.now ?: p.currentMediaItem?.let { item ->
      NowPlaying(item.mediaId, item.mediaMetadata.title?.toString().orEmpty(), item.mediaMetadata.artist?.toString().orEmpty(),
        item.mediaMetadata.artworkUri?.toString().orEmpty(), isPreview = item.mediaId.startsWith("preview:"))
    } ?: return
    val item = p.currentMediaItem
    if (item == null) { if (!n.loading && !n.isPreview) state = state.copy(now = null); return }
    val m = item.mediaMetadata; val preview = item.mediaId.startsWith("preview:")
    val key = item.mediaId.removePrefix("preview:")
    var now = n.copy(key = key, title = m.title?.toString() ?: n.title, artist = m.artist?.toString() ?: n.artist,
      art = m.artworkUri?.toString() ?: "", playing = p.isPlaying, position = p.currentPosition.coerceAtLeast(0),
      duration = p.duration.takeIf { it > 0 } ?: n.duration, isPreview = preview,
      loading = p.playbackState == Player.STATE_BUFFERING, hasNext = p.hasNextMediaItem(), hasPrev = p.hasPreviousMediaItem(),
      repeat = p.repeatMode, shuffle = p.shuffleModeEnabled, index = p.currentMediaItemIndex, count = p.mediaItemCount)
    if (key != n.key) now = now.copy(lyrics = null, lyricsState = "")
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
  fun shuffle() = withPlayer { it.shuffleModeEnabled = !it.shuffleModeEnabled }
  fun closePlayer() { sleep(0); previewJob?.cancel(); controller?.stop(); controller?.clearMediaItems(); lyricsFor = null
    state = state.copy(now = null, playbackQueue = emptyList(), queueOpen = false, previewing = null, previewLoading = false) }

  fun page(page: String) { state = state.copy(page = page) }
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
    val item = MediaItem.Builder().setMediaId(h.uri).setUri(uri).setMediaMetadata(meta(h.title, h.artist.ifBlank { h.meta }, h.art)).build()
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
    val picked = state.results.filter { it.url in state.selected }; if (picked.isEmpty()) return
    var cut: String? = null
    if (state.cutOn && picked.size == 1) {
      val a = secs(state.cutStart) ?: 0; val b = secs(state.cutEnd)
      if (b != null && b <= a) { state = state.copy(status = "O fim do corte precisa ser depois do início"); return }
      cut = "$a-${b ?: "inf"}"
    }
    picked.forEach { r -> DownloadWorker.enqueue(ctx, DownloadWorker.data(r.url, r.title, r.thumb, r.channel, state.mode, state.quality,
      if (state.mode == Mode.Musica) state.format else "MP4", cut, state.folder)) }
    stopPreview(); state = state.copy(query = "", results = emptyList(), selected = emptySet(), cutOn = false, cutStart = "", cutEnd = "",
      status = if (picked.size > 1) "${picked.size} itens adicionados à fila" else "")
  }

  fun sort(value: String) { prefs.edit().putString("sort", value).apply(); state = state.copy(sort = value) }
  fun mediaFilter(value: String) { state = state.copy(mediaFilter = value, albumFilter = "") }
  fun albumFilter(value: String) { state = state.copy(albumFilter = value, historyView = "Tudo", historyFilter = "") }
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
  fun removeLocal(h: HistoryItem) { HistoryStore.remove(ctx, h); Library.removeTrack(ctx, h.uri)
    state = state.copy(history = HistoryStore.load(ctx), favorites = Library.favorites(ctx), playlists = Library.playlists(ctx), status = "Removida da biblioteca. O arquivo original foi preservado.") }
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

  fun delete(h: HistoryItem) {
    viewModelScope.launch {
      runCatching { withContext(Dispatchers.IO) {
        val deleted = if (h.uri.startsWith("content://")) DocumentsContract.deleteDocument(ctx.contentResolver, Uri.parse(h.uri)) else File(h.uri).let { !it.exists() || it.delete() }
        require(deleted) { "O arquivo não pôde ser apagado" }
        HistoryStore.remove(ctx, h); Library.removeTrack(ctx, h.uri)
      } }.onSuccess { state = state.copy(history = HistoryStore.load(ctx), favorites = Library.favorites(ctx), playlists = Library.playlists(ctx)) }
        .onFailure { state = state.copy(status = "Falha ao apagar: ${it.message}") }
    }
  }

  override fun onCleared() { cleared = true; pending.clear(); controllerFuture?.let(MediaController::releaseFuture); super.onCleared() }
}

