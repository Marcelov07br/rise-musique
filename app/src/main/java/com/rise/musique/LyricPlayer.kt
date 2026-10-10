package com.rise.musique

import androidx.media3.common.FlagSet
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player

/**
 * What the notification / lock screen / car see. When lyrics-on-lockscreen is on, the artist line is replaced by the
 * current synced lyric line (only when the line changes). Stats, scrobbles and the app read the real player.
 */
@androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])
class LyricPlayer(player: Player) : ForwardingPlayer(player) {
  private val listeners = mutableListOf<Player.Listener>()
  var line: String? = null
    set(v) { if (v == field) return; field = v
      val m = mediaMetadata; listeners.toList().forEach { it.onMediaMetadataChanged(m)
        it.onEvents(this, Player.Events(FlagSet.Builder().add(Player.EVENT_MEDIA_METADATA_CHANGED).build())) } }
  override fun addListener(l: Player.Listener) { listeners += l; super.addListener(l) }
  override fun removeListener(l: Player.Listener) { listeners -= l; super.removeListener(l) }
  override fun getMediaMetadata(): MediaMetadata {
    val m = super.getMediaMetadata(); val l = line ?: return m
    return m.buildUpon().setArtist(l).setSubtitle(m.artist).build()
  }

  companion object {
    /** Index of the line playing at [posMs] (-1 before the first). Visible for tests. */
    fun lineAt(lines: List<Lyrics.Line>, posMs: Long): Int { var i = -1; for ((k, l) in lines.withIndex()) { if (l.ms <= posMs) i = k else break }; return i }
  }
}
