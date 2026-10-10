package com.rise.musique

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.MoreExecutors

/** Launcher shortcuts (long-press the icon): Tocar aleatório, Estações, Buscar, Downloads. Dynamic, so they follow the build's package. */
object Shortcuts {
  const val SHUFFLE = "com.rise.musique.action.SHUFFLE"; const val STATIONS = "com.rise.musique.action.STATIONS"
  const val SEARCH = "com.rise.musique.action.SEARCH"; const val DOWNLOADS = "com.rise.musique.action.DOWNLOADS"
  val all = listOf(Triple(SHUFFLE, "Tocar aleatório", android.R.drawable.ic_media_play), Triple(STATIONS, "Estações", android.R.drawable.ic_menu_compass),
    Triple(SEARCH, "Buscar", android.R.drawable.ic_menu_search), Triple(DOWNLOADS, "Downloads", android.R.drawable.stat_sys_download_done))
  fun publish(c: Context) = runCatching {
    ShortcutManagerCompat.setDynamicShortcuts(c, all.mapIndexed { i, (action, label, icon) ->
      ShortcutInfoCompat.Builder(c, action.substringAfterLast('.').lowercase()).setShortLabel(label).setRank(i)
        .setIcon(IconCompat.createWithResource(c, icon))
        .setIntent(Intent(action).setClass(c, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)).build() })
  }
}

/** What the Quick Settings tile shows; pushed by the playback service. */
object TileState {
  @Volatile var title: String = ""; @Volatile var artist: String = ""; @Volatile var playing = false
  fun push(c: Context, title: String, artist: String, playing: Boolean) {
    if (this.title == title && this.artist == artist && this.playing == playing) return
    this.title = title; this.artist = artist; this.playing = playing
    runCatching { TileService.requestListeningState(c, ComponentName(c, PlayTile::class.java)) }
  }
}

/** Quick Settings tile: play/pause, label = current track. */
class PlayTile : TileService() {
  override fun onStartListening() { super.onStartListening(); render() }
  private fun render() { val t = qsTile ?: return
    t.state = if (TileState.playing) Tile.STATE_ACTIVE else if (TileState.title.isBlank()) Tile.STATE_INACTIVE else Tile.STATE_INACTIVE
    t.label = TileState.title.ifBlank { "Rise Musique" }
    if (Build.VERSION.SDK_INT >= 29) t.subtitle = if (TileState.title.isBlank()) "Tocar" else TileState.artist.ifBlank { if (TileState.playing) "Tocando" else "Pausado" }
    t.icon = Icon.createWithResource(this, if (TileState.playing) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play)
    t.updateTile() }
  override fun onClick() { super.onClick()
    val f = MediaController.Builder(this, SessionToken(this, ComponentName(this, PlaybackService::class.java))).buildAsync()
    f.addListener({ runCatching { val mc = f.get()
      if (mc.isPlaying) mc.pause() else { if (mc.playbackState == androidx.media3.common.Player.STATE_IDLE) mc.prepare(); mc.play() }
      TileState.playing = !TileState.playing; render(); mc.release() } }, MoreExecutors.directExecutor())
  }
}
