package com.rise.musique
import android.content.*
import android.graphics.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.*
import androidx.datastore.preferences.core.*
import androidx.glance.*
import androidx.glance.action.*
import androidx.glance.appwidget.*
import androidx.glance.appwidget.action.*
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.layout.*
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.*
import androidx.glance.unit.ColorProvider
import androidx.media3.session.*
import java.io.File

/** Widget da tela inicial: capa desfocada de fundo, título, artista e play, no mesmo visual do player. */
class RiseWidget : GlanceAppWidget() {
  override val stateDefinition = PreferencesGlanceStateDefinition
  override suspend fun provideGlance(context: Context, id: GlanceId) = provideContent {
    val p = currentState<Preferences>()
    Content(context, p[TITLE] ?: "Rise Musique", p[ARTIST] ?: "Nada tocando", p[PLAYING] ?: false, p[STAMP] ?: 0L)
  }

  @Composable private fun Content(ctx: Context, title: String, artist: String, playing: Boolean, stamp: Long) {
    val blur = File(ctx.filesDir, "widget_blur.png").takeIf { it.exists() && stamp > 0 }?.let { BitmapFactory.decodeFile(it.path) }
    val art = File(ctx.filesDir, "widget_art.png").takeIf { it.exists() && stamp > 0 }?.let { BitmapFactory.decodeFile(it.path) }
    val white = ColorProvider(Color.White)
    Box(GlanceModifier.fillMaxSize().cornerRadius(28.dp).background(Color(0xFF111111)).clickable(actionStartActivity<MainActivity>())) {
      if (blur != null) Image(ImageProvider(blur), null, GlanceModifier.fillMaxSize(), contentScale = androidx.glance.layout.ContentScale.Crop)
      Box(GlanceModifier.fillMaxSize().background(Color(0x99000000))) {}
      Row(GlanceModifier.fillMaxSize().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
        if (art != null) Image(ImageProvider(art), null, GlanceModifier.size(64.dp).cornerRadius(16.dp), contentScale = androidx.glance.layout.ContentScale.Crop)
        else Box(GlanceModifier.size(64.dp).cornerRadius(16.dp).background(Color(0xFF262626)), contentAlignment = Alignment.Center) {
          Text("♪", style = TextStyle(color = white, fontSize = 26.sp)) }
        Spacer(GlanceModifier.width(14.dp))
        Column(GlanceModifier.defaultWeight()) {
          Text(title, maxLines = 1, style = TextStyle(color = white, fontSize = 15.sp, fontWeight = FontWeight.Bold))
          Text(artist, maxLines = 1, style = TextStyle(color = ColorProvider(Color(0xB3FFFFFF)), fontSize = 12.sp))
        }
        Spacer(GlanceModifier.width(10.dp))
        Box(GlanceModifier.size(52.dp).cornerRadius(26.dp).background(Color.White).clickable(actionRunCallback<PlayPauseAction>()),
          contentAlignment = Alignment.Center) {
          Image(ImageProvider(if (playing) R.drawable.ic_w_pause else R.drawable.ic_w_play), if (playing) "Pausar" else "Tocar", GlanceModifier.size(24.dp))
        }
      }
    }
  }

  companion object {
    val TITLE = stringPreferencesKey("t"); val ARTIST = stringPreferencesKey("a")
    val PLAYING = booleanPreferencesKey("p"); val STAMP = longPreferencesKey("s")

    /** Chamado pelo serviço de reprodução quando a música ou o play/pausa muda. */
    suspend fun push(ctx: Context, title: String, artist: String, playing: Boolean, artUrl: String?, artChanged: Boolean) {
      if (artChanged) runCatching {
        val bmp = artUrl?.takeIf { it.isNotBlank() }?.let { url ->
          val res = coil.ImageLoader(ctx).execute(coil.request.ImageRequest.Builder(ctx).data(url).size(256).allowHardware(false).build())
          (res.drawable as? android.graphics.drawable.BitmapDrawable)?.bitmap }
        if (bmp == null) { File(ctx.filesDir, "widget_art.png").delete(); File(ctx.filesDir, "widget_blur.png").delete() }
        else {
          val side = minOf(bmp.width, bmp.height)
          val sq = Bitmap.createBitmap(bmp, (bmp.width - side) / 2, (bmp.height - side) / 2, side, side)
          File(ctx.filesDir, "widget_art.png").outputStream().use { Bitmap.createScaledBitmap(sq, 192, 192, true).compress(Bitmap.CompressFormat.PNG, 90, it) }
          // desfoque barato e igual em todo Android: reduz muito e amplia com filtro
          val tiny = Bitmap.createScaledBitmap(bmp, 12, 12, true)
          File(ctx.filesDir, "widget_blur.png").outputStream().use { Bitmap.createScaledBitmap(tiny, 240, 120, true).compress(Bitmap.CompressFormat.PNG, 90, it) }
        }
      }
      val m = GlanceAppWidgetManager(ctx)
      m.getGlanceIds(RiseWidget::class.java).forEach { id ->
        updateAppWidgetState(ctx, id) { it[TITLE] = title; it[ARTIST] = artist; it[PLAYING] = playing; it[STAMP] = System.currentTimeMillis() }
        RiseWidget().update(ctx, id)
      }
    }
  }
}

class RiseWidgetReceiver : GlanceAppWidgetReceiver() { override val glanceAppWidget: GlanceAppWidget = RiseWidget() }

class PlayPauseAction : ActionCallback {
  override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
    val fut = MediaController.Builder(context, SessionToken(context, ComponentName(context, PlaybackService::class.java))).buildAsync()
    val c = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { fut.get() }
    if (c.mediaItemCount == 0) context.startActivity(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    else if (c.isPlaying) c.pause() else c.play()
    kotlinx.coroutines.delay(400); c.release()
  }
}
