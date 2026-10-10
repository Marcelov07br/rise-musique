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
class PlaybackService : MediaSessionService() {
  private var session: MediaSession? = null
  private var sleepJob: kotlinx.coroutines.Job? = null
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
      .setHandleAudioBecomingNoisy(true).build()
    val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
    session = MediaSession.Builder(this, player).setSessionActivity(open).setCallback(object : MediaSession.Callback {
      override fun onConnect(s: MediaSession, controller: MediaSession.ControllerInfo): MediaSession.ConnectionResult {
        val commands = MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon()
        if (controller.packageName == packageName) commands.add(SessionCommand(SLEEP, Bundle.EMPTY))
        return MediaSession.ConnectionResult.AcceptedResultBuilder(s).setAvailableSessionCommands(commands.build()).build()
      }
      override fun onCustomCommand(s: MediaSession, controller: MediaSession.ControllerInfo, command: SessionCommand,
                                   args: Bundle): ListenableFuture<SessionResult> {
        if (command.customAction != SLEEP || controller.packageName != packageName)
          return Futures.immediateFuture(SessionResult(SessionError.ERROR_NOT_SUPPORTED))
        val minutes = args.getInt("minutes").coerceIn(0, 1440)
        setSleep(if (minutes == 0) 0 else System.currentTimeMillis() + minutes * 60_000L)
        return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
      }
    }).build()
    setSleep(getSharedPreferences("playback", 0).getLong(SLEEP_AT, 0))
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

