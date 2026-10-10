package com.rise.musique

import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.FixMethodOrder
import org.junit.runners.MethodSorters
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.TimeUnit

@androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class LocalPlaybackTest {
  private val instrumentation = InstrumentationRegistry.getInstrumentation()
  private val ctx get() = instrumentation.targetContext
  private fun fixture(name: String): File {
    val file = File(ctx.getExternalFilesDir(null), name)
    val rate = 8000; val size = rate * 2 * 2
    val out = ByteBuffer.allocate(44 + size).order(ByteOrder.LITTLE_ENDIAN)
    out.put("RIFF".toByteArray()).putInt(36 + size).put("WAVEfmt ".toByteArray()).putInt(16).putShort(1).putShort(1)
      .putInt(rate).putInt(rate * 2).putShort(2).putShort(16).put("data".toByteArray()).putInt(size)
    file.writeBytes(out.array()); return file
  }
  @Test fun importReadsDurationAndDeduplicatesWithoutTouchingOriginal() = runBlocking<Unit> {
    val file = fixture("rise-import-fixture.wav")
    val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", file)
    val first = LocalMedia.import(ctx, listOf(uri)); assertEquals(1, first.added)
    val h = HistoryStore.load(ctx).first { it.uri == uri.toString() }
    assertTrue(h.imported); assertFalse(h.video); assertTrue(h.duration in 1900..2100)
    val again = LocalMedia.import(ctx, listOf(uri)); assertEquals(0, again.added); assertEquals(1, again.repeated)
    HistoryStore.remove(ctx, h); Library.removeTrack(ctx, h.uri)
    assertTrue(file.exists()); file.delete()
  }
  private fun controller(): MediaController {
    var future: com.google.common.util.concurrent.ListenableFuture<MediaController>? = null
    instrumentation.runOnMainSync { future = MediaController.Builder(ctx, SessionToken(ctx, ComponentName(ctx, PlaybackService::class.java))).buildAsync() }
    return future!!.get(15, TimeUnit.SECONDS)
  }
  @Test fun serviceRestoresQueueAndPositionPaused() {
    val one = fixture("rise-resume-one.wav"); val two = fixture("rise-resume-two.wav")
    val player = controller()
    instrumentation.runOnMainSync {
      val tracks = listOf(one, two).map { file -> MediaItem.Builder().setMediaId(file.absolutePath).setUri(Uri.fromFile(file))
        .setMediaMetadata(MediaMetadata.Builder().setTitle(file.name).build()).build() }
      player.setMediaItems(tracks, 1, 700); player.repeatMode = Player.REPEAT_MODE_ALL; player.shuffleModeEnabled = true; player.prepare(); player.pause()
    }
    // Wait for session IPC/events; the production service writes on state changes.
    val deadline = System.currentTimeMillis() + 10000
    while (System.currentTimeMillis() < deadline) {
      val json = ctx.getSharedPreferences("playback", 0).getString("snapshot", null)
      if (json != null && runCatching { PlaybackSnapshot.parse(json).position == 700L }.getOrDefault(false)) break
      Thread.sleep(100)
    }
    instrumentation.runOnMainSync { player.release(); ctx.stopService(Intent(ctx, PlaybackService::class.java)) }
    Thread.sleep(600)
    val restored = controller()
    instrumentation.runOnMainSync {
      assertEquals(2, restored.mediaItemCount); assertEquals(1, restored.currentMediaItemIndex)
      assertEquals(700L, restored.currentPosition); assertEquals(Player.REPEAT_MODE_ALL, restored.repeatMode)
      assertTrue(restored.shuffleModeEnabled); assertFalse(restored.playWhenReady)
      restored.clearMediaItems(); restored.release(); ctx.stopService(Intent(ctx, PlaybackService::class.java))
    }
    one.delete(); two.delete()
  }
  @Test fun zCaptureLibraryThemes() {
    val files = (1..8).map { fixture("rise-visual-$it.wav") }
    val names = listOf("Horizonte", "Entre linhas", "Voltar ao mar", "Noite tranquila", "Caminhos", "Ainda aqui", "Luz da manhã", "Passagem")
    val tracks = files.mapIndexed { i, file ->
      val cover = File(ctx.getExternalFilesDir(null), "rise-cover-$i.png")
      val bitmap = android.graphics.Bitmap.createBitmap(256, 256, android.graphics.Bitmap.Config.ARGB_8888)
      val canvas = android.graphics.Canvas(bitmap)
      val paint = android.graphics.Paint().apply { shader = android.graphics.LinearGradient(0f, 0f, 256f, 256f,
        if (i % 2 == 0) 0xFF5D7375.toInt() else 0xFF776254.toInt(), 0xFF181C24.toInt(), android.graphics.Shader.TileMode.CLAMP) }
      canvas.drawRect(0f, 0f, 256f, 256f, paint)
      paint.shader = null; paint.color = 0xFFE6E6E6.toInt(); paint.textSize = 112f; canvas.drawText("${i + 1}", 90f, 170f, paint)
      cover.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }; bitmap.recycle()
      com.rise.musique.ui.HistoryItem(names[i], "WAV · Local", file.absolutePath, Uri.fromFile(cover).toString(),
        if (i < 4) "Artista de exemplo" else "Outra voz", 192000, if (i < 4) "Horizonte" else "Caminhos", imported = true)
    }
    HistoryStore.addAll(ctx, tracks); Library.toggleFavorite(ctx, tracks.first().uri); Library.addToPlaylist(ctx, "Minha seleção", tracks.first().uri)
    ctx.getSharedPreferences("rise", 0).edit().putString("theme", "Escuro").apply()
    val activity = instrumentation.startActivitySync(Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as MainActivity
    fun capture(name: String) { Thread.sleep(1200)
      val screenshot = instrumentation.uiAutomation.takeScreenshot(); assertNotNull(screenshot)
      if (name == "library-dark.png") {
        var light = 0
        for (y in 350 until 470) for (x in 60 until 780) {
          val pixel = screenshot.getPixel(x, y)
          if (android.graphics.Color.red(pixel) > 180 && android.graphics.Color.green(pixel) > 180 && android.graphics.Color.blue(pixel) > 180) light++
        }
        assertTrue("Dark theme headline must have visible light text", light > 400)
      }
      File(ctx.getExternalFilesDir(null), name).outputStream().use { screenshot.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }; screenshot.recycle()
    }
    capture("library-dark.png")
    instrumentation.runOnMainSync { androidx.lifecycle.ViewModelProvider(activity)[MainViewModel::class.java].theme("Claro") }
    capture("library-light.png")
    instrumentation.runOnMainSync { androidx.lifecycle.ViewModelProvider(activity)[MainViewModel::class.java].settings(true) }
    capture("settings-light.png")
    instrumentation.runOnMainSync { activity.finish() }
  }
}
