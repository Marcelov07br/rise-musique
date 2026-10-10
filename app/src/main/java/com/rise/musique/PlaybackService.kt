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

/** Dono do player: mantém a música tocando com a tela desligada e mostra controles na notificação e na tela de bloqueio. */
class PlaybackService : MediaSessionService() {
  private var session: MediaSession? = null
  private val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Main)
  override fun onCreate() { super.onCreate()
    val renderers = object : DefaultRenderersFactory(this) {
      override fun buildAudioSink(ctx: Context, floatOut: Boolean, params: Boolean): AudioSink =
        DefaultAudioSink.Builder(ctx).setAudioProcessors(arrayOf(TeeAudioProcessor(com.rise.musique.AudioLevels))).build()
    }
    val player = ExoPlayer.Builder(this, renderers)
      .setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(), true)
      .setHandleAudioBecomingNoisy(true).build()
    val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
    session = MediaSession.Builder(this, player).setSessionActivity(open).build()
    player.addListener(object : androidx.media3.common.Player.Listener {
      private var lastArt: String? = "none"
      override fun onEvents(p: androidx.media3.common.Player, e: androidx.media3.common.Player.Events) {
        if (!e.containsAny(androidx.media3.common.Player.EVENT_IS_PLAYING_CHANGED, androidx.media3.common.Player.EVENT_MEDIA_METADATA_CHANGED,
            androidx.media3.common.Player.EVENT_MEDIA_ITEM_TRANSITION)) return
        if (!p.isPlaying) AudioLevels.silence()
        val m = p.mediaMetadata; val art = m.artworkUri?.toString()
        val changed = art != lastArt; lastArt = art
        scope.launch { RiseWidget.push(this@PlaybackService, m.title?.toString() ?: "Rise Musique", m.artist?.toString() ?: "", p.isPlaying, art, changed) }
      }
    })
  }
  override fun onGetSession(info: MediaSession.ControllerInfo) = session
  override fun onTaskRemoved(i: Intent?) { session?.player?.let { if (!it.playWhenReady || it.mediaItemCount == 0) stopSelf() } }
  override fun onDestroy() { scope.coroutineContext[kotlinx.coroutines.Job]?.cancel()
    session?.run { player.release(); release() }; session = null; super.onDestroy() }
}
