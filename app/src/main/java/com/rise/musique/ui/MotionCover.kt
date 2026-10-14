package com.rise.musique.ui

import android.app.Application
import android.content.Context
import android.graphics.Matrix
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.view.TextureView
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import com.rise.musique.MotionArt
import com.rise.musique.MotionArtworkRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.File

/** Ajustes → Aparência → "Capas animadas": 0 Não, 1 Só no Wi-Fi (padrão), 2 Sempre. Reduzir movimento desliga. */
object MotionArtPrefs {
  val OPTIONS = listOf(2, 1, 0)
  fun label(m: Int) = when (m) { 2 -> "Sempre"; 1 -> "Só Wi-Fi"; else -> "Não" }
  var mode by mutableIntStateOf(1); private set
  private var loaded = false
  fun load(ctx: Context) { if (loaded) return; loaded = true; mode = ctx.getSharedPreferences("rise", 0).getInt("motionArt", 1) }
  fun set(ctx: Context, m: Int) { mode = m; loaded = true; ctx.getSharedPreferences("rise", 0).edit().putInt("motionArt", m).apply() }
  /** Regra pura (testada). */
  fun allowed(mode: Int, reduceMotion: Boolean, unmetered: Boolean) = !reduceMotion && (mode == 2 || (mode == 1 && unmetered))
  fun unmetered(ctx: Context): Boolean = runCatching {
    val cm = ctx.getSystemService(ConnectivityManager::class.java) ?: return false
    cm.getNetworkCapabilities(cm.activeNetwork)?.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED) == true
  }.getOrDefault(false)
}

sealed interface MotionArtState { data object None : MotionArtState; data object Loading : MotionArtState; data class Ready(val art: MotionArt) : MotionArtState }

/** Estado da capa animada por artista+álbum para o Compose; rede só pelo repositório (Dispatchers.IO). */
class MotionArtworkViewModel(app: Application) : AndroidViewModel(app) {
  private val repo = MotionArtworkRepository(File(app.filesDir, "motion_art.json"))
  private val states = HashMap<String, MutableStateFlow<MotionArtState>>()
  fun state(artist: String, album: String): StateFlow<MotionArtState> {
    val k = com.rise.musique.MotionArtKey.of(artist, album)
    states[k]?.let { if (it.value != MotionArtState.None) return it }
    val f = states.getOrPut(k) { MutableStateFlow(MotionArtState.None) }
    f.value = MotionArtState.Loading
    viewModelScope.launch { f.value = runCatching { repo.find(artist, album) }.getOrNull()?.let { MotionArtState.Ready(it) } ?: MotionArtState.None }
    return f
  }
}

/**
 * Um único ExoPlayer de vídeo (mudo, em loop, sem foco de áudio, sem MediaSession; separado do player de música).
 * Quem mostra a capa "reivindica" o player; vence a maior prioridade (player cheio > cabeçalho do álbum > mini).
 * Sem ninguém por 3 s, o player é liberado. Cache: SimpleCache LRU de 120 MB em cacheDir/motion-art.
 */
@androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])
object MotionArtPlayer {
  private var player: ExoPlayer? = null
  private var cache: Any? = null
  private var url: String? = null
  private val claims = mutableStateMapOf<Any, Int>()
  var firstFrame by mutableIntStateOf(0); private set
  var failed by mutableStateOf<String?>(null); private set
  var videoSize by mutableStateOf(VideoSize.UNKNOWN); private set
  /** Dono atual (maior prioridade; empate = mais recente). */
  val owner: Any? get() = claims.entries.maxByOrNull { it.value }?.key
  private var releaseGen = 0

  fun claim(token: Any, priority: Int) { claims.remove(token); claims[token] = priority; releaseGen++ }
  fun unclaim(token: Any) { claims.remove(token); if (claims.isEmpty()) { val g = ++releaseGen
    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({ if (g == releaseGen && claims.isEmpty()) release() }, 3_000) } }

  @androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])
  private fun cacheOf(ctx: Context): SimpleCache = cache as? SimpleCache ?: SimpleCache(File(ctx.cacheDir, "motion-art"),
    LeastRecentlyUsedCacheEvictor(120L * 1024 * 1024), StandaloneDatabaseProvider(ctx)).also { cache = it }

  @androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])
  fun play(ctx: Context, art: MotionArt, view: TextureView) {
    val p = player ?: ExoPlayer.Builder(ctx.applicationContext).build().also { p ->
      p.setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(), false)
      p.volume = 0f; p.repeatMode = Player.REPEAT_MODE_ONE
      p.trackSelectionParameters = p.trackSelectionParameters.buildUpon().setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, true)
        .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true).setMaxVideoSize(800, 800).build()
      p.addListener(object : Player.Listener {
        override fun onRenderedFirstFrame() { firstFrame++ }
        override fun onVideoSizeChanged(v: VideoSize) { videoSize = v }
        override fun onPlayerError(error: PlaybackException) { failed = url; com.rise.musique.ErrorLog.w("motionArt", "video: ${error.errorCodeName}") }
      })
      player = p
    }
    p.setVideoTextureView(view)
    if (url != art.videoUrl) {
      url = art.videoUrl; failed = null; videoSize = VideoSize.UNKNOWN
      val ds = CacheDataSource.Factory().setCache(cacheOf(ctx.applicationContext))
        .setUpstreamDataSourceFactory(DefaultHttpDataSource.Factory().setConnectTimeoutMs(6_000).setReadTimeoutMs(8_000).setUserAgent("RiseMusique"))
        .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
      val item = MediaItem.fromUri(art.videoUrl)
      p.setMediaSource(if (art.isHls) HlsMediaSource.Factory(ds).createMediaSource(item) else ProgressiveMediaSource.Factory(ds).createMediaSource(item))
      p.prepare()
    }
    p.playWhenReady = true
  }
  fun detach(view: TextureView) { player?.clearVideoTextureView(view) }
  fun pause() { player?.playWhenReady = false }
  fun release() { player?.release(); player = null; url = null }
}

@androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])
private fun centerCrop(v: TextureView, size: VideoSize) {
  if (size.width <= 0 || size.height <= 0 || v.width == 0 || v.height == 0) return
  val vw = size.width * size.pixelWidthHeightRatio; val vh = size.height.toFloat()
  val s = maxOf(v.width / vw, v.height / vh)
  val m = Matrix(); m.setScale(vw * s / v.width, vh * s / v.height, v.width / 2f, v.height / 2f); v.setTransform(m)
}

/**
 * Capa com vídeo por cima: [staticContent] (a capa normal, com Ken Burns de 0.18 quando não há vídeo) sempre por baixo;
 * o vídeo entra com fade depois do primeiro quadro e some em qualquer falha. [active] = falso durante voos da capa,
 * letra etc. (mostra a estática). Pausa fora da tela (dispose) e com o app em segundo plano.
 */
@androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])
@Composable fun MotionCover(artist: String, album: String, active: Boolean, priority: Int, modifier: Modifier = Modifier,
                            staticContent: @Composable BoxScope.(videoOn: Boolean) -> Unit) {
  val ctx = LocalContext.current
  MotionArtPrefs.load(ctx)
  val reduce = rememberReduceMotion()
  val canUse = !StaticPreview.on && LocalViewModelStoreOwner.current != null && artist.isNotBlank() && album.isNotBlank() &&
    MotionArtPrefs.mode != 0 && !reduce
  if (!canUse) { Box(modifier) { staticContent(false) }; return }
  val allowedNet = remember(MotionArtPrefs.mode, reduce, artist, album) { MotionArtPrefs.allowed(MotionArtPrefs.mode, reduce, MotionArtPrefs.unmetered(ctx)) }
  val vm: MotionArtworkViewModel = viewModel()
  val state by remember(artist, album, allowedNet) { if (allowedNet) vm.state(artist, album) else MutableStateFlow(MotionArtState.None) }.collectAsState()
  val lifecycle = LocalLifecycleOwner.current.lifecycle
  var resumed by remember { mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) }
  DisposableEffect(lifecycle) {
    val o = LifecycleEventObserver { _, e -> when (e) { Lifecycle.Event.ON_START -> resumed = true; Lifecycle.Event.ON_STOP -> { resumed = false; MotionArtPlayer.pause() }; else -> {} } }
    lifecycle.addObserver(o); onDispose { lifecycle.removeObserver(o) } }
  val art = (state as? MotionArtState.Ready)?.art
  val token = remember { Any() }
  val want = active && resumed && art != null && MotionArtPlayer.failed != art.videoUrl
  DisposableEffect(want, priority) { if (want) MotionArtPlayer.claim(token, priority); onDispose { MotionArtPlayer.unclaim(token) } }
  val mine = want && MotionArtPlayer.owner === token
  val alpha = remember { Animatable(0f) }
  var startFrame by remember { mutableIntStateOf(-1) }
  LaunchedEffect(mine, art?.videoUrl) { if (!mine) { alpha.snapTo(0f); startFrame = -1 } else startFrame = MotionArtPlayer.firstFrame }
  LaunchedEffect(MotionArtPlayer.firstFrame, mine) { if (mine && startFrame >= 0 && MotionArtPlayer.firstFrame > startFrame) { delay(60); alpha.animateTo(1f, tween(450)) } }
  LaunchedEffect(MotionArtPlayer.failed) { if (art != null && MotionArtPlayer.failed == art.videoUrl) alpha.snapTo(0f) }
  val videoOn = mine && alpha.value > .99f
  Box(modifier) {
    staticContent(videoOn)
    if (mine && art != null) {
      val size = MotionArtPlayer.videoSize
      AndroidView({ c -> TextureView(c).apply { isOpaque = false
          addOnLayoutChangeListener { v, _, _, _, _, _, _, _, _ -> centerCrop(v as TextureView, MotionArtPlayer.videoSize) } } },
        Modifier.fillMaxSize().graphicsLayer { this.alpha = alpha.value },
        update = { v -> MotionArtPlayer.play(ctx, art, v); centerCrop(v, size) },
        onRelease = { v -> MotionArtPlayer.detach(v) })
    }
  }
}
