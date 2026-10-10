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

/**
 * Widget de vidro, responsivo: 2x2 (capa + play), 4x2 (capa, título, controles), 4x4 (capa, linha da letra, progresso, controles).
 * Fundo = capa desfocada com véu escuro; cores fixas (nada de cor dinâmica).
 */
class RiseWidget : GlanceAppWidget() {
  override val stateDefinition = PreferencesGlanceStateDefinition
  override val sizeMode = SizeMode.Responsive(setOf(SMALL, WIDE, LARGE))
  override suspend fun provideGlance(context: Context, id: GlanceId) = provideContent {
    val p = currentState<Preferences>()
    val s = WidgetState(p[TITLE] ?: "Rise Musique", p[ARTIST] ?: "Nada tocando", p[PLAYING] ?: false, p[POS] ?: 0L, p[DUR] ?: 0L, p[LYRIC].orEmpty(), p[STAMP] ?: 0L)
    val stamp = s.stamp
    val blur = File(context.filesDir, "widget_blur.png").takeIf { it.exists() && stamp > 0 }?.let { BitmapFactory.decodeFile(it.path) }
    val art = File(context.filesDir, "widget_art.png").takeIf { it.exists() && stamp > 0 }?.let { BitmapFactory.decodeFile(it.path) }
    Content(s, LocalSize.current, blur, art)
  }

  @Composable private fun Content(s: WidgetState, size: DpSize, blur: Bitmap?, art: Bitmap?) {
    val white = ColorProvider(Color.White); val dim = ColorProvider(Color(0xB3FFFFFF))
    val layout = WidgetLayout.of(size)
    Box(GlanceModifier.fillMaxSize().cornerRadius(28.dp).background(Color(0xFF111111)).clickable(actionStartActivity<MainActivity>())) {
      if (blur != null) Image(ImageProvider(blur), null, GlanceModifier.fillMaxSize(), contentScale = androidx.glance.layout.ContentScale.Crop)
      Box(GlanceModifier.fillMaxSize().background(Color(0x8C000000))) {}
      when (layout) {
        WidgetLayout.Small -> Box(GlanceModifier.fillMaxSize().padding(12.dp)) {
          Cover(art, GlanceModifier.fillMaxSize())
          Box(GlanceModifier.fillMaxSize(), contentAlignment = Alignment.BottomEnd) { PlayButton(s.playing, 44.dp) }
        }
        WidgetLayout.Wide -> Row(GlanceModifier.fillMaxSize().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
          Cover(art, GlanceModifier.size(84.dp)); Spacer(GlanceModifier.width(14.dp))
          Column(GlanceModifier.defaultWeight()) {
            Text(s.title, maxLines = 1, style = TextStyle(color = white, fontSize = 15.sp, fontWeight = FontWeight.Bold))
            Text(s.artist, maxLines = 1, style = TextStyle(color = dim, fontSize = 12.sp))
            Spacer(GlanceModifier.height(8.dp)); Controls(s.playing)
          }
        }
        WidgetLayout.Large -> Column(GlanceModifier.fillMaxSize().padding(16.dp)) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Cover(art, GlanceModifier.size(96.dp)); Spacer(GlanceModifier.width(14.dp))
            Column(GlanceModifier.defaultWeight()) {
              Text(s.title, maxLines = 2, style = TextStyle(color = white, fontSize = 17.sp, fontWeight = FontWeight.Bold))
              Text(s.artist, maxLines = 1, style = TextStyle(color = dim, fontSize = 13.sp)) }
          }
          Spacer(GlanceModifier.defaultWeight())
          Text(s.lyric.removePrefix("♪ ").ifBlank { if (s.playing) "♪" else "" }, maxLines = 3,
            style = TextStyle(color = white, fontSize = 18.sp, fontWeight = FontWeight.Medium))
          Spacer(GlanceModifier.defaultWeight())
          LinearProgressIndicator(WidgetLayout.progress(s.pos, s.dur), GlanceModifier.fillMaxWidth().height(4.dp).cornerRadius(2.dp),
            color = white, backgroundColor = ColorProvider(Color(0x33FFFFFF)))
          Spacer(GlanceModifier.height(10.dp))
          Box(GlanceModifier.fillMaxWidth(), contentAlignment = Alignment.Center) { Controls(s.playing) }
        }
      }
    }
  }

  @Composable private fun Cover(art: Bitmap?, m: GlanceModifier) =
    if (art != null) Image(ImageProvider(art), null, m.cornerRadius(16.dp), contentScale = androidx.glance.layout.ContentScale.Crop)
    else Box(m.cornerRadius(16.dp).background(Color(0x33FFFFFF)), contentAlignment = Alignment.Center) {
      Text("♪", style = TextStyle(color = ColorProvider(Color.White), fontSize = 26.sp)) }

  @Composable private fun PlayButton(playing: Boolean, d: Dp) =
    Box(GlanceModifier.size(d).cornerRadius(d / 2).background(Color.White).clickable(actionRunCallback<PlayPauseAction>()), contentAlignment = Alignment.Center) {
      Image(ImageProvider(if (playing) R.drawable.ic_w_pause else R.drawable.ic_w_play), if (playing) "Pausar" else "Tocar", GlanceModifier.size(d / 2)) }

  @Composable private fun Controls(playing: Boolean) = Row(verticalAlignment = Alignment.CenterVertically) {
    Box(GlanceModifier.size(48.dp).clickable(actionRunCallback<PrevAction>()), contentAlignment = Alignment.Center) {
      Image(ImageProvider(R.drawable.ic_w_prev), "Anterior", GlanceModifier.size(24.dp)) }
    Spacer(GlanceModifier.width(8.dp)); PlayButton(playing, 48.dp); Spacer(GlanceModifier.width(8.dp))
    Box(GlanceModifier.size(48.dp).clickable(actionRunCallback<NextAction>()), contentAlignment = Alignment.Center) {
      Image(ImageProvider(R.drawable.ic_w_next), "Próxima", GlanceModifier.size(24.dp)) }
  }

  companion object {
    val SMALL = DpSize(110.dp, 110.dp); val WIDE = DpSize(250.dp, 110.dp); val LARGE = DpSize(250.dp, 250.dp)
    val TITLE = stringPreferencesKey("t"); val ARTIST = stringPreferencesKey("a"); val LYRIC = stringPreferencesKey("l")
    val PLAYING = booleanPreferencesKey("p"); val STAMP = longPreferencesKey("s"); val POS = longPreferencesKey("pos"); val DUR = longPreferencesKey("dur")

    /** Chamado pelo serviço quando a música, o play/pausa ou a linha da letra muda (não a cada quadro). */
    suspend fun push(ctx: Context, title: String, artist: String, playing: Boolean, artUrl: String?, artChanged: Boolean,
                     pos: Long = 0, dur: Long = 0, lyric: String? = null) {
      if (artChanged) runCatching {
        val bmp = artUrl?.takeIf { it.isNotBlank() }?.let { url ->
          val res = coil.ImageLoader(ctx).execute(coil.request.ImageRequest.Builder(ctx).data(url).size(256).allowHardware(false).build())
          (res.drawable as? android.graphics.drawable.BitmapDrawable)?.bitmap }
        if (bmp == null) { File(ctx.filesDir, "widget_art.png").delete(); File(ctx.filesDir, "widget_blur.png").delete() }
        else {
          val side = minOf(bmp.width, bmp.height)
          val sq = Bitmap.createBitmap(bmp, (bmp.width - side) / 2, (bmp.height - side) / 2, side, side)
          File(ctx.filesDir, "widget_art.png").outputStream().use { Bitmap.createScaledBitmap(sq, 256, 256, true).compress(Bitmap.CompressFormat.PNG, 90, it) }
          // desfoque barato e igual em todo Android: reduz muito e amplia com filtro
          val tiny = Bitmap.createScaledBitmap(sq, 10, 10, true)
          File(ctx.filesDir, "widget_blur.png").outputStream().use { Bitmap.createScaledBitmap(tiny, 240, 240, true).compress(Bitmap.CompressFormat.PNG, 90, it) }
        }
      }
      val m = GlanceAppWidgetManager(ctx)
      m.getGlanceIds(RiseWidget::class.java).forEach { id ->
        updateAppWidgetState(ctx, id) { it[TITLE] = title; it[ARTIST] = artist; it[PLAYING] = playing; it[STAMP] = System.currentTimeMillis()
          it[POS] = pos; it[DUR] = dur; it[LYRIC] = lyric.orEmpty() }
        RiseWidget().update(ctx, id)
      }
    }
  }
}

data class WidgetState(val title: String, val artist: String, val playing: Boolean, val pos: Long, val dur: Long, val lyric: String, val stamp: Long)

/** Pure size → layout choice (unit-tested). */
enum class WidgetLayout { Small, Wide, Large;
  companion object {
    fun of(s: DpSize) = when { s.width < 200.dp -> Small; s.height < 200.dp -> Wide; else -> Large }
    fun progress(pos: Long, dur: Long) = if (dur <= 0) 0f else (pos.toFloat() / dur).coerceIn(0f, 1f)
  }
}

class RiseWidgetReceiver : GlanceAppWidgetReceiver() { override val glanceAppWidget: GlanceAppWidget = RiseWidget() }

private suspend fun withController(context: Context, block: (MediaController) -> Unit) {
  val fut = MediaController.Builder(context, SessionToken(context, ComponentName(context, PlaybackService::class.java))).buildAsync()
  val c = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { fut.get() }
  block(c); kotlinx.coroutines.delay(400); c.release()
}
class PlayPauseAction : ActionCallback {
  override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) = withController(context) { c ->
    if (c.mediaItemCount == 0) context.startActivity(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    else if (c.isPlaying) c.pause() else c.play() }
}
class NextAction : ActionCallback {
  override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) = withController(context) { it.seekToNextMediaItem() }
}
class PrevAction : ActionCallback {
  override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) = withController(context) { it.seekToPreviousMediaItem() }
}
