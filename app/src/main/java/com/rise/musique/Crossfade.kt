package com.rise.musique

import android.content.Context
import android.os.SystemClock
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.launch

/** Pure rules for when a crossfade applies. Visible for tests. */
object CrossfadeRules {
  val OPTIONS = listOf(0, 2, 4, 6, 8, 10, 12)
  fun clampSeconds(s: Int) = OPTIONS.minBy { kotlin.math.abs(it - s) }
  fun isPreview(id: String?) = id == null || id.startsWith("preview:")
  /** Consecutive tracks of the same album play gapless (no fade). */
  fun sameAlbum(a: String?, b: String?, artistA: String?, artistB: String?) =
    !a.isNullOrBlank() && a == b && (artistA.isNullOrBlank() || artistB.isNullOrBlank() || artistA == artistB)
  /** Fade length in ms, never longer than a third of the track; 0 = no crossfade. */
  fun fadeMs(seconds: Int, durationMs: Long) = if (seconds <= 0 || durationMs <= 0) 0L else minOf(seconds * 1000L, durationMs / 3)
  fun volumes(elapsedMs: Long, fadeMs: Long): Pair<Float, Float> {
    val f = if (fadeMs <= 0) 1f else (elapsedMs.toFloat() / fadeMs).coerceIn(0f, 1f)
    return (1f - f) to f
  }
}

/**
 * Crossfade between local tracks: near the end of the current item a second, silent player starts the next item
 * and the volumes cross; when the main player reaches that item it jumps to the aux position and the aux is released.
 * Automix: same mechanism, but the overlap starts on a downbeat of the outgoing track, the incoming one enters on its
 * first downbeat, lasts whole bars and is tempo-matched (±8%) with PlaybackParameters. Low-confidence analysis falls
 * back to the plain crossfade. Previews, repeat-one and same-album sequences (gapless) are excluded.
 */
@androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])
class Crossfader(private val ctx: Context, private val main: ExoPlayer, private val scope: kotlinx.coroutines.CoroutineScope,
                 private val sleepActive: () -> Boolean) {
  private var aux: ExoPlayer? = null
  private var target = C.INDEX_UNSET
  private var startedAt = 0L
  private var fade = 0L
  @Volatile private var plan: Plan? = null
  private var planFor: String? = null
  private var planJob: kotlinx.coroutines.Job? = null

  /** Beat-aligned transition: start main at [startMs], aux enters at [inMs] at [speed], overlap [overlapMs]. */
  data class Plan(val startMs: Long, val inMs: Long, val overlapMs: Long, val speed: Float)

  private val prefs get() = ctx.getSharedPreferences("rise", 0)
  fun seconds() = prefs.getInt("crossfade", 0)
  fun automix() = prefs.getBoolean("automix", false)

  fun tick() {
    val a = aux
    if (a != null) {
      if (!main.playWhenReady) { abort(); return }
      if (!main.isPlaying || main.currentMediaItemIndex == target) return
      val (out, inn) = CrossfadeRules.volumes(SystemClock.elapsedRealtime() - startedAt, fade)
      main.volume = out; a.volume = inn; return
    }
    val auto = automix()
    val secs = if (auto) seconds().takeIf { it > 0 } ?: 8 else seconds()
    if (secs <= 0 || !main.isPlaying || sleepActive()) return
    if (main.repeatMode == Player.REPEAT_MODE_ONE) return
    val next = main.nextMediaItemIndex; if (next == C.INDEX_UNSET) return
    val cur = main.currentMediaItem ?: return; val nxt = main.getMediaItemAt(next)
    if (CrossfadeRules.isPreview(cur.mediaId) || CrossfadeRules.isPreview(nxt.mediaId) || nxt.localConfiguration == null) return
    if (CrossfadeRules.sameAlbum(cur.mediaMetadata.albumTitle?.toString(), nxt.mediaMetadata.albumTitle?.toString(),
        cur.mediaMetadata.artist?.toString(), nxt.mediaMetadata.artist?.toString())) return
    val dur = main.duration; if (dur == C.TIME_UNSET) return
    if (auto) {
      val pair = cur.mediaId + ">" + nxt.mediaId
      if (planFor != pair) { planFor = pair; plan = null; planJob?.cancel()
        val curUri = cur.localConfiguration?.uri; val nxtUri = nxt.localConfiguration!!.uri
        if (curUri != null) planJob = scope.launch(kotlinx.coroutines.Dispatchers.IO) {
          val outG = BeatStore.grid(ctx, cur.mediaId, curUri, tailOfMs = dur); val inG = BeatStore.grid(ctx, nxt.mediaId, nxtUri)
          if (outG != null && inG != null) plan = plan(outG, inG, secs * 1000L, dur)
        } }
      val p = plan
      if (p != null) {
        val pos = main.currentPosition
        if (pos in (p.startMs - 40)..(p.startMs + 400)) { start(nxt, next, p.overlapMs, p.inMs + ((pos - p.startMs) * p.speed).toLong(), p.speed); return }
        if (pos < p.startMs) return
      }
    }
    val f = CrossfadeRules.fadeMs(secs, dur); val remaining = dur - main.currentPosition
    if (f < 1000 || remaining > f || remaining < 400) return
    start(nxt, next, f, 0, 1f)
  }

  fun plan(outG: BeatGrid, inG: BeatGrid, wantMs: Long, dur: Long): Plan? {
    val overlap = BeatAnalysis.barsOverlap(outG, wantMs, dur / 3)
    var startMs = outG.downbeatAtOrBefore(dur - overlap - 200)
    if (startMs + overlap > dur) startMs -= outG.barMs.toLong()
    if (startMs <= 0) return null
    return Plan(startMs, inG.firstDownbeatMs, overlap, BeatAnalysis.tempoRatio(outG.bpm, inG.bpm))
  }

  private fun start(item: MediaItem, index: Int, f: Long, fromMs: Long, speed: Float) {
    aux = ExoPlayer.Builder(ctx).setMediaSourceFactory(androidx.media3.exoplayer.source.DefaultMediaSourceFactory(streamDataSource(ctx)))
      .setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(), false)
      .build().apply { volume = 0f; setMediaItem(item, fromMs.coerceAtLeast(0)); if (speed != 1f) setPlaybackSpeed(speed); prepare(); play() }
    target = index; fade = f; startedAt = SystemClock.elapsedRealtime()
  }

  /** Main player moved to another item. */
  fun onTransition(reason: Int) {
    planFor = null; plan = null
    val a = aux ?: return
    if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO && main.currentMediaItemIndex == target) {
      val pos = a.currentPosition
      main.volume = 1f; if (pos > 300) main.seekTo(pos)
      release()
    } else abort()
  }
  fun abort() { if (aux != null) { main.volume = 1f; release() } }
  private fun release() { aux?.run { stop(); release() }; aux = null; target = C.INDEX_UNSET }
}
