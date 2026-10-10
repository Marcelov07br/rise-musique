package com.rise.musique

import android.content.Context
import android.net.Uri
import androidx.media3.common.*
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.MergingMediaSource
import com.rise.musique.ui.HistoryItem
import com.rise.musique.ui.SearchResult
import com.rise.musique.ui.VideoUi
import kotlinx.coroutines.*

/** Lets the PlayerView in Compose reach the one video player (no-op in previews/tests). */
object VideoHolder { @Volatile var player: ExoPlayer? = null }

/**
 * In-app video: its own ExoPlayer (the music service keeps its queue). YouTube video-only + audio-only formats are merged
 * (MergingMediaSource), quality switch keeps the position, subtitles (vtt) are side-loaded, expired URLs re-resolve once.
 */
@androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])
class VideoController(private val ctx: Context, private val scope: CoroutineScope, private val onState: (VideoUi?) -> Unit) {
  private var player: ExoPlayer? = null
  private var info: Engine.VideoInfo? = null
  private var source: SearchResult? = null
  private var queue: List<SearchResult> = emptyList()
  private var ui: VideoUi? = null
  private var retried = false
  private var job: Job? = null

  private fun emit(u: VideoUi?) { ui = u; onState(u) }
  private fun ensurePlayer(): ExoPlayer = player ?: ExoPlayer.Builder(ctx)
    .setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(), true)
    .setHandleAudioBecomingNoisy(true).setSeekBackIncrementMs(10_000).setSeekForwardIncrementMs(10_000).build().also { p ->
      p.addListener(object : Player.Listener {
        override fun onPlayerError(error: PlaybackException) {
          val http = generateSequence(error.cause) { it.cause }.filterIsInstance<androidx.media3.datasource.HttpDataSource.InvalidResponseCodeException>().firstOrNull()
          if (http != null && http.responseCode in listOf(403, 410) && !retried && source != null) { retried = true; reload(source!!, p.currentPosition, ui?.quality ?: 0); return }
          emit(ui?.copy(loading = false, playing = false, error = "Não foi possível tocar o vídeo: ${error.errorCodeName}"))
        }
        override fun onVideoSizeChanged(v: VideoSize) { if (v.width > 0 && v.height > 0) emit(ui?.copy(aspect = v.width * v.pixelWidthHeightRatio / v.height)) }
        override fun onPlaybackStateChanged(s: Int) { if (s == Player.STATE_ENDED && ui?.hasNext == true) next() }
      })
      player = p; VideoHolder.player = p
    }

  fun open(r: SearchResult, list: List<SearchResult>) {
    queue = list.filter { it.playable }.ifEmpty { listOf(r) }; source = r; retried = false
    emit(VideoUi(r.title, r.channel, r.thumb, loading = true, hasNext = queue.indexOfFirst { it.url == r.url } in 0 until queue.size - 1))
    reload(r, 0, 0)
  }
  fun openLocal(h: HistoryItem, uri: Uri) {
    source = null; queue = emptyList(); info = null
    emit(VideoUi(h.title, h.artist, h.art, loading = true, local = true))
    val p = ensurePlayer(); p.setMediaItem(MediaItem.fromUri(uri)); p.prepare(); p.play()
  }

  private fun reload(r: SearchResult, posMs: Long, wantHeight: Int) {
    job?.cancel(); job = scope.launch {
      runCatching { withContext(Dispatchers.IO) { Engine.videoInfo(r.url) } }.onSuccess { i ->
        info = i
        val heights = i.formats.map { it.height }.distinct()
        val h = VideoQuality.pick(heights, wantHeight)
        val sub = i.subtitles.entries.firstOrNull()
        emit(ui?.copy(qualities = heights, quality = h, subtitles = i.subtitles.keys.toList(), subtitle = ui?.subtitle ?: sub?.key, error = null,
          duration = i.durationSec * 1000L))
        val p = ensurePlayer(); p.setMediaSource(build(i, h, ui?.subtitle)); if (posMs > 0) p.seekTo(posMs); p.prepare(); p.play()
      }.onFailure { if (it is CancellationException) throw it; emit(ui?.copy(loading = false, error = "Vídeo indisponível: ${it.message?.lineSequence()?.lastOrNull()?.take(90)}")) }
    }
  }

  private fun build(i: Engine.VideoInfo, height: Int, subtitle: String?): MediaSource {
    val f = DefaultMediaSourceFactory(streamDataSource(ctx))
    val subs = listOfNotNull(subtitle?.let { k -> i.subtitles[k]?.let { url -> MediaItem.SubtitleConfiguration.Builder(Uri.parse(url)).setMimeType(MimeTypes.TEXT_VTT)
      .setLanguage(k.substringBefore(" ")).setLabel(k).setSelectionFlags(C.SELECTION_FLAG_DEFAULT).build() } })
    val v = i.formats.firstOrNull { it.height == height }
    if (v == null || i.audio == null) {
      val m = i.muxed ?: error("sem formato de vídeo")
      return f.createMediaSource(MediaItem.Builder().setUri(m.url).setSubtitleConfigurations(subs).build())
    }
    return MergingMediaSource(true, f.createMediaSource(MediaItem.Builder().setUri(v.url).setSubtitleConfigurations(subs).build()),
      f.createMediaSource(MediaItem.fromUri(i.audio.url)))
  }

  fun quality(h: Int) { val r = source ?: return; val i = info ?: return; val p = player ?: return
    emit(ui?.copy(quality = h, loading = true)); val pos = p.currentPosition
    p.setMediaSource(build(i, h, ui?.subtitle)); p.seekTo(pos); p.prepare(); p.play() }
  fun subtitle(k: String?) { val i = info; val p = player ?: return
    emit(ui?.copy(subtitle = k))
    p.trackSelectionParameters = p.trackSelectionParameters.buildUpon().setTrackTypeDisabled(C.TRACK_TYPE_TEXT, k == null).build()
    if (k != null && i != null && source != null) { val pos = p.currentPosition; p.setMediaSource(build(i, ui?.quality ?: 0, k)); p.seekTo(pos); p.prepare(); p.play() } }
  fun playPause() { val p = player ?: return; if (p.isPlaying) p.pause() else { if (p.playbackState == Player.STATE_ENDED) p.seekTo(0); p.play() } }
  fun pause() { player?.pause() }
  fun seek(ms: Long) { player?.seekTo(ms.coerceAtLeast(0)) }
  fun seekBy(ms: Long) { player?.let { it.seekTo((it.currentPosition + ms).coerceIn(0, it.duration.takeIf { d -> d > 0 } ?: Long.MAX_VALUE)) } }
  fun next() { val cur = source ?: return; val i = queue.indexOfFirst { it.url == cur.url }; queue.getOrNull(i + 1)?.let { open(it, queue) } }
  fun fullscreen(on: Boolean) = emit(ui?.copy(fullscreen = on))
  fun poll() { val p = player ?: return; val u = ui ?: return
    val n = u.copy(playing = p.isPlaying, position = p.currentPosition.coerceAtLeast(0), duration = p.duration.takeIf { it > 0 } ?: u.duration,
      loading = p.playbackState == Player.STATE_BUFFERING || (u.loading && p.playbackState == Player.STATE_IDLE))
    if (n != u) emit(n) }
  fun close() { job?.cancel(); player?.release(); player = null; VideoHolder.player = null; info = null; source = null; emit(null) }
}

object VideoQuality {
  /** [want] if available, else the best ≤1080p (data friendly), else the lowest. Visible for tests. */
  fun pick(heights: List<Int>, want: Int): Int = when {
    heights.isEmpty() -> 0
    want > 0 && want in heights -> want
    else -> heights.filter { it <= 1080 }.maxOrNull() ?: heights.min()
  }
  fun label(h: Int) = when { h >= 2160 -> "4K"; h >= 1440 -> "1440p"; else -> "${h}p" }
}
