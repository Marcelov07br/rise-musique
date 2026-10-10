package com.rise.musique
import android.app.PendingIntent
import android.content.Intent
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.audio.*
import android.content.Context
import androidx.media3.session.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import android.os.Bundle
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture

/** Dono do player: mantém a música tocando com a tela desligada e mostra controles na notificação e na tela de bloqueio. */
@androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])
class PlaybackService : MediaLibraryService() {
  private var session: MediaLibrarySession? = null
  private var crossfader: Crossfader? = null
  private var playSession: PlaySession? = null
  private var lastTick = 0L
  private var sleepJob: kotlinx.coroutines.Job? = null
  private var previewActive = false
  private fun savePlayback() {
    val p = session?.player ?: return
    if (p.currentMediaItem?.mediaId?.startsWith("preview:") == true) { previewActive = true; return }
    val prefs = getSharedPreferences("playback", 0)
    if (p.mediaItemCount == 0) { if (!previewActive) prefs.edit().remove("snapshot").apply(); return }
    previewActive = false
    val tracks = (0 until p.mediaItemCount).map { i ->
      val item = p.getMediaItemAt(i); val m = item.mediaMetadata
      com.rise.musique.ui.HistoryItem(m.title?.toString().orEmpty(), "", item.mediaId,
        m.artworkUri?.toString().orEmpty(), m.artist?.toString().orEmpty(), album = m.albumTitle?.toString().orEmpty())
    }
    val snapshot = PlaybackSnapshot(tracks, p.currentMediaItemIndex.coerceAtLeast(0), p.currentPosition.coerceAtLeast(0), p.repeatMode, p.shuffleModeEnabled)
    prefs.edit().putString("snapshot", snapshot.json()).apply()
  }
  private fun restorePlayback(player: ExoPlayer) {
    val prefs = getSharedPreferences("playback", 0)
    val text = prefs.getString("snapshot", null) ?: return
    runCatching {
      val saved = PlaybackSnapshot.parse(text)
      val tracks = saved.tracks.map { h ->
        val uri = if (h.uri.startsWith("content://")) android.net.Uri.parse(h.uri) else android.net.Uri.fromFile(java.io.File(h.uri))
        androidx.media3.common.MediaItem.Builder().setMediaId(h.uri).setUri(uri).setMediaMetadata(
          androidx.media3.common.MediaMetadata.Builder().setTitle(h.title).setArtist(h.artist).setAlbumTitle(h.album.ifBlank { null })
            .apply { if (h.art.isNotBlank()) setArtworkUri(android.net.Uri.parse(h.art)) }.build()).build()
      }
      player.setMediaItems(tracks, saved.index, saved.position)
      player.repeatMode = saved.repeat; player.shuffleModeEnabled = saved.shuffle
      player.playWhenReady = false; player.prepare()
    }.onFailure { prefs.edit().remove("snapshot").apply() }
  }
  companion object { const val SLEEP = "rise.sleep"; const val SLEEP_AT = "sleepAt" }
  private fun setSleep(at: Long) {
    sleepJob?.cancel()
    val player = session?.player ?: return
    player.volume = 1f
    getSharedPreferences("playback", 0).edit().putLong(SLEEP_AT, at).apply()
    session?.setSessionExtras(Bundle().apply { putLong(SLEEP_AT, at) })
    if (at <= 0) return
    sleepJob = scope.launch {
      delay((at - System.currentTimeMillis() - 8_000).coerceAtLeast(0))
      val initialVolume = player.volume
      while (System.currentTimeMillis() < at) {
        player.volume = initialVolume * ((at - System.currentTimeMillis()) / 8000f).coerceIn(0f, 1f)
        delay(200)
      }
      player.pause(); player.volume = initialVolume
      getSharedPreferences("playback", 0).edit().putLong(SLEEP_AT, 0).apply()
      session?.setSessionExtras(Bundle().apply { putLong(SLEEP_AT, 0) })
    }
  }
  private val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Main)
  override fun onCreate() { super.onCreate()
    val renderers = object : DefaultRenderersFactory(this) {
      override fun buildAudioSink(ctx: Context, floatOut: Boolean, params: Boolean): AudioSink =
        DefaultAudioSink.Builder(ctx).setAudioProcessors(arrayOf(TeeAudioProcessor(com.rise.musique.AudioLevels))).build()
    }
    val player = ExoPlayer.Builder(this, renderers)
      .setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(), true)
      .setHandleAudioBecomingNoisy(true)
      .setMediaSourceFactory(androidx.media3.exoplayer.source.DefaultMediaSourceFactory(streamDataSource(this))).build()
    val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
    session = MediaLibrarySession.Builder(this, player, object : MediaLibrarySession.Callback {
      override fun onConnect(s: MediaSession, controller: MediaSession.ControllerInfo): MediaSession.ConnectionResult {
        val base = MediaSession.ConnectionResult.AcceptedResultBuilder(s)
        val commands = MediaSession.ConnectionResult.DEFAULT_SESSION_AND_LIBRARY_COMMANDS.buildUpon()
        if (controller.packageName == packageName) commands.add(SessionCommand(SLEEP, Bundle.EMPTY))
        return base.setAvailableSessionCommands(commands.build()).build()
      }
      override fun onCustomCommand(s: MediaSession, controller: MediaSession.ControllerInfo, command: SessionCommand,
                                   args: Bundle): ListenableFuture<SessionResult> {
        if (command.customAction != SLEEP || controller.packageName != packageName)
          return Futures.immediateFuture(SessionResult(SessionError.ERROR_NOT_SUPPORTED))
        val minutes = args.getInt("minutes").coerceIn(0, 1440)
        setSleep(if (minutes == 0) 0 else System.currentTimeMillis() + minutes * 60_000L)
        return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
      }
      override fun onGetLibraryRoot(s: MediaLibrarySession, browser: MediaSession.ControllerInfo, params: LibraryParams?) =
        Futures.immediateFuture(LibraryResult.ofItem(AutoLibrary.folderItem(AutoLibrary.Folder(AutoLibrary.ROOT, "Rise Musique")), params))
      override fun onGetChildren(s: MediaLibrarySession, browser: MediaSession.ControllerInfo, parentId: String, page: Int, pageSize: Int,
                                 params: LibraryParams?): ListenableFuture<LibraryResult<com.google.common.collect.ImmutableList<androidx.media3.common.MediaItem>>> {
        val nodes = browseChildren(parentId)
        val from = (page.coerceAtLeast(0).toLong() * pageSize.coerceAtLeast(1)).coerceAtMost(nodes.size.toLong()).toInt()
        val slice = nodes.drop(from).take(pageSize.coerceAtLeast(1)).map(::nodeItem)
        return Futures.immediateFuture(LibraryResult.ofItemList(slice, params))
      }
      override fun onGetItem(s: MediaLibrarySession, browser: MediaSession.ControllerInfo, mediaId: String) =
        Futures.immediateFuture(findTrack(mediaId)?.let { LibraryResult.ofItem(AutoLibrary.trackItem(this@PlaybackService, it, true), null) }
          ?: LibraryResult.ofError(SessionError.ERROR_BAD_VALUE))
      /** Items from the app already carry a URI; items picked in the car only carry the id, so resolve them locally. */
      override fun onAddMediaItems(s: MediaSession, controller: MediaSession.ControllerInfo, items: MutableList<androidx.media3.common.MediaItem>) =
        Futures.immediateFuture(resolve(items))
      override fun onSetMediaItems(s: MediaSession, controller: MediaSession.ControllerInfo, items: MutableList<androidx.media3.common.MediaItem>,
                                   startIndex: Int, startPositionMs: Long): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
        val query = items.singleOrNull()?.requestMetadata?.searchQuery
        if (query != null) {
          // Voice: "tocar <x> no Rise Musique" — match title/artist/album in local files; empty query = whole library.
          val all = HistoryStore.load(this@PlaybackService).filter(AutoLibrary::isLocal)
          val hits = AutoLibrary.search(all, query)
          val queue = hits.map { AutoLibrary.trackItem(this@PlaybackService, it, true) }.filter { it.localConfiguration != null }
          if (queue.isNotEmpty()) return Futures.immediateFuture(MediaSession.MediaItemsWithStartPosition(queue, 0, C.TIME_UNSET))
        }
        if (items.size == 1 && items[0].localConfiguration == null) {
          // Picked a single track in the car: queue its whole folder context (library order) starting at it.
          val all = HistoryStore.load(this@PlaybackService).filter(AutoLibrary::isLocal)
          val idx = all.indexOfFirst { it.uri == items[0].mediaId }
          if (idx >= 0) {
            val queue = all.map { AutoLibrary.trackItem(this@PlaybackService, it, true) }.filter { it.localConfiguration != null }
            val start = queue.indexOfFirst { it.mediaId == items[0].mediaId }
            if (start >= 0) return Futures.immediateFuture(MediaSession.MediaItemsWithStartPosition(queue, start, C.TIME_UNSET))
          }
        }
        return Futures.immediateFuture(MediaSession.MediaItemsWithStartPosition(resolve(items), startIndex, startPositionMs))
      }
    }).setSessionActivity(open).build()
    restorePlayback(player)
    setSleep(getSharedPreferences("playback", 0).getLong(SLEEP_AT, 0))
    scope.launch { while (true) { delay(5_000); savePlayback() } }
    val fader = Crossfader(this, player) { getSharedPreferences("playback", 0).getLong(SLEEP_AT, 0).let { it > 0 && it - System.currentTimeMillis() < 30_000 } }
    crossfader = fader
    scope.launch { while (true) { delay(200); fader.tick(); trackPlay(player) } }
    player.addListener(object : androidx.media3.common.Player.Listener {
      private var lastArt: String? = "none"
      override fun onMediaItemTransition(item: androidx.media3.common.MediaItem?, reason: Int) { fader.onTransition(reason); finishPlay() }
      override fun onPositionDiscontinuity(o: androidx.media3.common.Player.PositionInfo, n: androidx.media3.common.Player.PositionInfo, reason: Int) {
        if (reason == androidx.media3.common.Player.DISCONTINUITY_REASON_SEEK && o.mediaItemIndex == n.mediaItemIndex) fader.abort() }
      override fun onEvents(p: androidx.media3.common.Player, e: androidx.media3.common.Player.Events) {
        savePlayback()
        if (!e.containsAny(androidx.media3.common.Player.EVENT_IS_PLAYING_CHANGED, androidx.media3.common.Player.EVENT_MEDIA_METADATA_CHANGED,
            androidx.media3.common.Player.EVENT_MEDIA_ITEM_TRANSITION)) return
        if (!p.isPlaying) AudioLevels.silence()
        val m = p.mediaMetadata; val art = m.artworkUri?.toString()
        val changed = art != lastArt; lastArt = art
        scope.launch { RiseWidget.push(this@PlaybackService, m.title?.toString() ?: "Rise Musique", m.artist?.toString() ?: "", p.isPlaying, art, changed) }
      }
    })
  }
  override fun onGetSession(info: MediaSession.ControllerInfo): MediaLibrarySession? = session

  private fun browseChildren(parentId: String): List<AutoLibrary.Node> = AutoLibrary.children(parentId,
    HistoryStore.load(this), Library.favorites(this), Library.playlists(this))
  private fun nodeItem(n: AutoLibrary.Node) = when (n) {
    is AutoLibrary.Folder -> AutoLibrary.folderItem(n)
    is AutoLibrary.Track -> AutoLibrary.trackItem(this, n.item, false)
  }
  private fun findTrack(id: String) = HistoryStore.load(this).firstOrNull { it.uri == id && AutoLibrary.isLocal(it) }
  private fun resolve(items: List<androidx.media3.common.MediaItem>): MutableList<androidx.media3.common.MediaItem> =
    items.mapNotNull { if (it.localConfiguration != null) it else findTrack(it.mediaId)?.let { h -> AutoLibrary.trackItem(this, h, true) }
      ?.takeIf { r -> r.localConfiguration != null } }.toMutableList()

  /** Statistics: accumulate time actually played per item; a play counts after 30 s or half the track. */
  private fun trackPlay(p: ExoPlayer) {
    val now = android.os.SystemClock.elapsedRealtime(); val delta = if (lastTick == 0L) 0 else now - lastTick; lastTick = now
    val item = p.currentMediaItem ?: return
    if (item.mediaId.startsWith("preview:") || !p.isPlaying) return
    var s = playSession
    if (s == null || s.key != item.mediaId) { finishPlay()
      s = PlaySession(item.mediaId, item.mediaMetadata.title?.toString().orEmpty(), item.mediaMetadata.artist?.toString().orEmpty(), System.currentTimeMillis())
      playSession = s }
    if (s.add(delta, p.duration.takeIf { it != C.TIME_UNSET } ?: 0)) saveStat(s)
  }
  private fun finishPlay() { playSession?.let { if (it.counted) saveStat(it) }; playSession = null }
  private fun saveStat(s: PlaySession) { val play = s.play()
    scope.launch(kotlinx.coroutines.Dispatchers.IO) { runCatching { PlayStats.record(this@PlaybackService, play) } } }
  override fun onTaskRemoved(i: Intent?) { session?.player?.let { if (!it.playWhenReady || it.mediaItemCount == 0) stopSelf() } }
  override fun onDestroy() { savePlayback(); finishPlay(); crossfader?.abort(); scope.coroutineContext[kotlinx.coroutines.Job]?.cancel()
    session?.run { player.release(); release() }; session = null; super.onDestroy() }
}

/** HTTP stack that sends the same User-Agent/headers yt-dlp used to resolve the googlevideo URL. */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
internal fun streamDataSource(ctx: Context): androidx.media3.datasource.DataSource.Factory {
  val http = androidx.media3.datasource.DefaultHttpDataSource.Factory()
    .setUserAgent(Engine.DEFAULT_UA).setAllowCrossProtocolRedirects(true)
    .setConnectTimeoutMs(15_000).setReadTimeoutMs(20_000)
  val resolving = androidx.media3.datasource.ResolvingDataSource.Factory(http) { spec ->
    val h = Engine.streamHeaders[spec.uri.toString()]
    if (h.isNullOrEmpty()) spec else spec.withAdditionalHeaders(h.filterKeys { !it.equals("Accept-Encoding", true) })
  }
  return androidx.media3.datasource.DefaultDataSource.Factory(ctx, resolving)
}
