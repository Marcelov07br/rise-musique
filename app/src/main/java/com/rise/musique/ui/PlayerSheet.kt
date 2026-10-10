package com.rise.musique.ui
import android.os.Build
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import androidx.compose.ui.util.lerp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

private val bouncy = spring<Float>(dampingRatio = 0.72f, stiffness = Spring.StiffnessMediumLow) // quica de leve; p é travado em 0..1 no layout
private fun fmtMs(ms: Long) = (ms / 1000).let { "%d:%02d".format(it / 60, it % 60) }

/**
 * Mini player que vira player em tela cheia. `f` vai de 0 (mini) a 1 (cheio) e controla tudo:
 * tamanho e posição da capa, fundo desfocado, cantos e controles. Arrastar move `f` junto com o dedo;
 * soltar assenta com mola. O voltar com prévia (Android 14+) encolhe a tela seguindo o gesto.
 */
@Composable fun PlayerSheet(now: NowPlaying, a: Actions, initialExpanded: Boolean = false, initialLyrics: Boolean = false) {
  val c = MaterialTheme.colorScheme
  val scope = rememberCoroutineScope()
  val f = remember { Animatable(if (initialExpanded) 1f else 0f) }
  var back by remember { mutableFloatStateOf(0f) }
  val density = LocalDensity.current
  var showLyrics by remember { mutableStateOf(initialLyrics) }

  PredictiveBackHandler(enabled = f.value > 0.5f) { progress ->
    try { progress.collect { back = it.progress }; scope.launch { f.animateTo(0f, bouncy) }; back = 0f }
    catch (e: CancellationException) { animate(back, 0f, animationSpec = bouncy) { v, _ -> back = v } }
  }

  BoxWithConstraints(Modifier.fillMaxSize()) {
    val fullH = constraints.maxHeight.toFloat()
    val miniH = with(density) { 68.dp.toPx() }
    val margin = with(density) { 12.dp.toPx() }
    val p = f.value.coerceIn(0f, 1f) // nunca sai de 0..1, nem em rebote
    val dragState = rememberDraggableState { d -> scope.launch { f.snapTo((f.value - d / (fullH - miniH)).coerceIn(0f, 1f)) } }
    // Mini: cartão compacto (miniH) logo acima da barra de navegação, com a mesma margem dos lados e embaixo.
    val navBottom = WindowInsets.navigationBars.getBottom(density).toFloat()
    val sheetTop = lerp(fullH - miniH - margin - navBottom, 0f, p)
    val sheetH = lerp(miniH, fullH, p).coerceIn(miniH, fullH)
    val corner = lerp(34f, 0f, p) + back * 36f
    val scale = 1f - back * 0.12f

    Box(Modifier.fillMaxWidth().height(with(density) { sheetH.toDp() })
        .offset { IntOffset(0, sheetTop.toInt()) }
        .padding(horizontal = with(density) { lerp(margin, 0f, p).toDp() })
        .graphicsLayer { scaleX = scale; scaleY = scale; shape = RoundedCornerShape(corner.dp); clip = true }
        .background(c.surface).border(1.dp, c.outlineVariant, RoundedCornerShape(corner.dp))
        .draggable(dragState, Orientation.Vertical, onDragStopped = { v ->
          val target = when { v < -800 -> 1f; v > 800 -> 0f; else -> if (f.value > .5f) 1f else 0f }
          f.animateTo(target, bouncy) })
        .clickable(enabled = p < .05f, indication = null, interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }) {
          scope.launch { f.animateTo(1f, bouncy) } }) {

      // Fundo imersivo: a própria capa, gigante e desfocada, com véu escuro por cima.
      if (p > 0.01f) Box(Modifier.fillMaxSize().alpha(p)) {
        val req = ImageRequest.Builder(androidx.compose.ui.platform.LocalContext.current).data(now.art)
          .apply { if (Build.VERSION.SDK_INT < 31) size(24) }.crossfade(true).build()
        AsyncImage(req, null, Modifier.fillMaxSize().scale(1.4f).then(if (Build.VERSION.SDK_INT >= 31) Modifier.blur(70.dp) else Modifier),
          contentScale = ContentScale.Crop)
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Black.copy(.25f), Color.Black.copy(.55f), Color.Black.copy(.85f)))))
      }

      val maxW = this@BoxWithConstraints.maxWidth
      val artFull = (maxW - 56.dp).coerceAtMost(420.dp)
      val artSize = lerp(48.dp, artFull, p)
      val artX = lerp(10.dp, (maxW - artFull) / 2, p)
      val artY = lerp(10.dp, 96.dp, p)
      val onBg = if (p > .5f) Color.White else c.onSurface

      // Capa: o mesmo elemento cresce do mini até o tamanho cheio.
      val artAlpha by animateFloatAsState(if (showLyrics && p > .9f) 0f else 1f, tween(300), label = "art")
      // Capa que respira: cheia no play, recua no pause, sempre com mola.
      val breath by animateFloatAsState(if (now.playing || p < .5f) 1f else 0.84f, spring(dampingRatio = 0.45f, stiffness = 260f), label = "breath")
      // Capa arremessável: arrasta na horizontal; forte o bastante troca de faixa, fraco volta com mola.
      val throwX = remember { Animatable(0f) }
      val maxWpx = with(density) { maxW.toPx() }
      val artColor = Color(0xFFBEBEC4)
      if (p > .5f && artAlpha > 0f) AudioRing(Modifier.offset(artX, artY).size(artSize)
        .graphicsLayer { translationX = throwX.value; val sc = breath; scaleX = sc; scaleY = sc }, artColor, now.playing)
      var tapHint by remember { mutableStateOf<Pair<Long, Int>?>(null) }
      Box(Modifier.offset(artX, artY).size(artSize).alpha(artAlpha)
          .then(if (p > .9f && !showLyrics) Modifier.pointerInput(now.key) { detectTapGestures(onDoubleTap = { o ->
            val dir = if (o.x < size.width / 2) -1 else 1
            a.seek((now.position + dir * 10_000).coerceIn(0, now.duration.coerceAtLeast(0))); tapHint = System.nanoTime() to dir }) } else Modifier)
          .graphicsLayer { translationX = throwX.value; rotationZ = throwX.value / maxWpx * 14f
            val sc = lerp(1f, breath, p) * (1f - kotlin.math.abs(throwX.value) / maxWpx * .15f); scaleX = sc; scaleY = sc }
          .then(if (p > .9f && !showLyrics) Modifier.draggable(rememberDraggableState { d -> scope.launch { throwX.snapTo(throwX.value + d) } },
            Orientation.Horizontal, onDragStopped = { v ->
              val go = if (throwX.value < -maxWpx / 3 || v < -1800f) 1 else if (throwX.value > maxWpx / 3 || v > 1800f) -1 else 0
              val canGo = (go == 1 && now.hasNext) || (go == -1 && now.hasPrev)
              if (go != 0 && canGo) {
                throwX.animateTo(-go * maxWpx * 1.3f, spring(stiffness = 500f))
                if (go == 1) a.next() else a.prev()
                throwX.snapTo(go * maxWpx * 1.1f)
              }
              throwX.animateTo(0f, spring(dampingRatio = 0.55f, stiffness = 320f))
            }) else Modifier).shadow(lerp(0.dp, 24.dp, p), RoundedCornerShape(lerp(12.dp, 28.dp, p)))
          .clip(RoundedCornerShape(lerp(12.dp, 28.dp, p))).background(c.outline), contentAlignment = Alignment.Center) {
        if (now.art.isNotBlank()) AsyncImage(now.art, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        else Icon(Icons.Rounded.MusicNote, null, tint = c.onSurfaceVariant, modifier = Modifier.fillMaxSize(.4f))
        tapHint?.let { (id, dir) -> SeekRipple(id, dir) }
      }

      // Linha do mini player (some ao expandir).
      if (p < .6f) Row(Modifier.fillMaxWidth().height(68.dp).alpha(1f - p / .6f).padding(start = 70.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
          Text(now.title, color = c.onSurface, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
          Text(if (now.isPreview) "Prévia · ${now.artist}" else now.artist, color = c.onSurfaceVariant, fontSize = 12.sp, maxLines = 1)
        }
        PlayButton(now, a, 44.dp, c.primary, c.onPrimary)
        if (!now.isPreview && now.hasNext) IconButton(a.next) { Icon(Icons.Rounded.SkipNext, "Próxima", tint = c.onSurface) }
        IconButton(a.closePlayer) { Icon(Icons.Rounded.Close, "Fechar", tint = c.onSurfaceVariant) }
      }

      // Conteúdo do player cheio (surge ao expandir).
      if (p > .4f) Column(Modifier.fillMaxSize().safeDrawingPadding().alpha((p - .4f) / .6f)
          .offset(y = lerp(40.dp, 0.dp, p)).padding(horizontal = 28.dp)) {
        Row(Modifier.fillMaxWidth().height(56.dp), verticalAlignment = Alignment.CenterVertically) {
          IconButton({ scope.launch { f.animateTo(0f, bouncy) } }) { Icon(Icons.Rounded.KeyboardArrowDown, "Recolher", tint = Color.White) }
          Spacer(Modifier.weight(1f))
          Box(Modifier.size(width = 36.dp, height = 5.dp).clip(CircleShape).background(Color.White.copy(.35f)))
          Spacer(Modifier.weight(1f)); SleepButton(now, a)
        }
        Box(Modifier.fillMaxWidth().height(artFull + 64.dp)) {
          if (showLyrics) LyricsView(now, a, Modifier.fillMaxSize().padding(top = 40.dp, bottom = 16.dp))
        }
        Text(now.title, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(if (now.isPreview) "Prévia de 30 s · ${now.artist}" else now.artist, color = Color.White.copy(.65f), fontSize = 16.sp, maxLines = 1)
        Spacer(Modifier.height(20.dp))
        var drag by remember { mutableStateOf<Float?>(null) }
        val dur = now.duration.coerceAtLeast(1)
        Slider(drag ?: (now.position.toFloat() / dur), { drag = it }, onValueChangeFinished = { drag?.let { a.seek((it * dur).toLong()) }; drag = null },
          colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = Color.White, inactiveTrackColor = Color.White.copy(.2f)))
        Row { Text(fmtMs(now.position), color = Color.White.copy(.6f), fontSize = 12.sp); Spacer(Modifier.weight(1f))
          Text("-" + fmtMs((now.duration - now.position).coerceAtLeast(0)), color = Color.White.copy(.6f), fontSize = 12.sp) }
        Spacer(Modifier.height(24.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
          SideButton(Icons.Rounded.SkipPrevious, "Anterior", Modifier.weight(1f), a.prev)
          PlayButton(now, a, 84.dp, Color.White, Color.Black, Modifier.weight(2f), pill = true)
          SideButton(Icons.Rounded.SkipNext, "Próxima", Modifier.weight(1f), enabled = now.hasNext, on = a.next)
        }
        Spacer(Modifier.height(18.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
          ToggleIcon(Icons.Rounded.Shuffle, "Aleatório", now.shuffle, enabled = !now.isPreview, onClick = a.shuffle)
          ToggleIcon(if (now.repeat == 1) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
            when (now.repeat) { 1 -> "Repetir faixa"; 2 -> "Repetir fila"; else -> "Repetição desligada" }, now.repeat != 0, enabled = !now.isPreview, onClick = a.repeat)
          ToggleIcon(Icons.Rounded.QueueMusic, "Fila de reprodução", false, enabled = !now.isPreview) { a.showQueue(true) }
          Spacer(Modifier.weight(1f))
          if (now.count > 1) Text("${now.index + 1} de ${now.count}", color = Color.White.copy(.5f), fontSize = 12.sp)
          Spacer(Modifier.weight(1f))
          ToggleIcon(Icons.Rounded.Lyrics, "Letra", showLyrics, enabled = !now.isPreview) { showLyrics = !showLyrics }
        }
      }
    }
  }
}

@Composable private fun PlayButton(now: NowPlaying, a: Actions, h: Dp, bg: Color, fg: Color, m: Modifier = Modifier, pill: Boolean = false) {
  val src = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
  Box(m.then(if (pill) Modifier.height(h) else Modifier.size(h)).springPress(src).clip(CircleShape).background(bg)
      .clickable(src, null, onClick = a.playPause), contentAlignment = Alignment.Center) {
    if (now.loading) CircularProgressIndicator(Modifier.size(h * .4f), color = fg, strokeWidth = 2.dp)
    else AnimatedContent(now.playing, label = "pp") { pl ->
      Icon(if (pl) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, if (pl) "Pausar" else "Tocar", tint = fg, modifier = Modifier.size(h * .45f)) }
  }
}

@Composable private fun ToggleIcon(icon: androidx.compose.ui.graphics.vector.ImageVector, desc: String, on: Boolean, enabled: Boolean = true, onClick: () -> Unit) {
  val src = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
  Box(Modifier.size(44.dp).springPress(src).clip(CircleShape).background(if (on) Color.White.copy(.9f) else Color.Transparent)
      .alpha(if (enabled) 1f else .3f).clickable(src, null, enabled = enabled, onClick = onClick), contentAlignment = Alignment.Center) {
    Icon(icon, desc, tint = if (on) Color.Black else Color.White.copy(.8f), modifier = Modifier.size(22.dp)) }
}

/** Letra rolando: a linha atual fica em destaque e centralizada; tocar numa linha pula pra ela. */
@Composable private fun LyricsView(now: NowPlaying, a: Actions, m: Modifier) {
  val lines = now.lyrics
  if (lines == null) { Box(m, contentAlignment = Alignment.Center) {
    Text(when (now.lyricsState) { "loading" -> "Procurando a letra…"; else -> "Letra sincronizada não encontrada" },
      color = Color.White.copy(.6f), fontSize = 16.sp) }; return }
  val cur = lines.indexOfLast { it.ms <= now.position + 250 }.coerceAtLeast(0)
  val list = androidx.compose.foundation.lazy.rememberLazyListState()
  LaunchedEffect(cur) { list.animateScrollToItem((cur - 2).coerceAtLeast(0)) }
  androidx.compose.foundation.lazy.LazyColumn(m, state = list, verticalArrangement = Arrangement.spacedBy(14.dp)) {
    items(lines.size) { i -> val l = lines[i]
      val alpha by animateFloatAsState(if (i == cur) 1f else .35f, tween(250), label = "l")
      Text(l.text.ifBlank { "♪" }, Modifier.alpha(alpha).clickable(indication = null,
        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }) { a.seek(l.ms) },
        color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold, lineHeight = 30.sp)
    }
  }
}

@Composable private fun SideButton(icon: androidx.compose.ui.graphics.vector.ImageVector, desc: String, m: Modifier, on: () -> Unit) =
  SideButton(icon, desc, m, true, on)
@Composable private fun SideButton(icon: androidx.compose.ui.graphics.vector.ImageVector, desc: String, m: Modifier, enabled: Boolean, on: () -> Unit) {
  val src = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
  Box(m.height(84.dp).alpha(if (enabled) 1f else .35f).springPress(src).clip(CircleShape).background(Color.White.copy(.12f)).clickable(src, null, enabled = enabled, onClick = on),
    contentAlignment = Alignment.Center) { Icon(icon, desc, tint = Color.White, modifier = Modifier.size(30.dp)) }
}

/** Cor de destaque da capa, extraída com Palette. */
@Composable fun rememberArtColor(url: String): Color {
  val ctx = androidx.compose.ui.platform.LocalContext.current
  var color by remember(url) { mutableStateOf(Color.White) }
  LaunchedEffect(url) {
    if (url.isBlank()) return@LaunchedEffect
    try {
    val res = coil.ImageLoader(ctx).execute(ImageRequest.Builder(ctx).data(url).size(128).allowHardware(false).build())
    val bmp = (res.drawable as? android.graphics.drawable.BitmapDrawable)?.bitmap ?: return@LaunchedEffect
    val pal = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) { androidx.palette.graphics.Palette.from(bmp).generate() }
    val sw = pal.vibrantSwatch ?: pal.lightVibrantSwatch ?: pal.dominantSwatch ?: return@LaunchedEffect
    // clareia um pouco pra ficar legível sobre o fundo escuro, sem saturar demais
    color = androidx.compose.ui.graphics.lerp(Color(sw.rgb), Color.White, .25f)
    } catch (e: CancellationException) { throw e } catch (_: Exception) { color = Color.White }
  }
  return color
}

/** Ondas no ritmo: barras em anel em volta da capa, puxadas pelas 8 faixas de energia do áudio tocando. */
@Composable private fun AudioRing(m: Modifier, color: Color, playing: Boolean) {
  val bands by com.rise.musique.AudioLevels.bands.collectAsState()
  val fade by animateFloatAsState(if (playing) 1f else 0f, tween(400), label = "ring")
  val t = rememberInfiniteTransition(label = "spin").animateFloat(0f, 360f, infiniteRepeatable(tween(24_000, easing = LinearEasing)), label = "a")
  Canvas(m) {
    if (fade <= 0.01f) return@Canvas
    val cx = size.width / 2; val cy = size.height / 2
    val baseR = size.minDimension / 2 * 1.04f
    val count = 72
    for (i in 0 until count) {
      // espelha as faixas pelo anel: graves embaixo, agudos em cima, sem emenda
      val pos = i.toFloat() / count; val m2 = if (pos < .5f) pos * 2 else (1 - pos) * 2
      val f = m2 * 7; val lo = f.toInt().coerceIn(0, 7); val hi = (lo + 1).coerceAtMost(7)
      val lvl = (bands[lo] * (1 - (f - lo)) + bands[hi] * (f - lo)).coerceIn(0f, 1f)
      val len = (6f + lvl * size.minDimension * 0.16f) * fade
      val ang = Math.toRadians((pos * 360f + t.value + 90f).toDouble())
      val cs = kotlin.math.cos(ang).toFloat(); val sn = kotlin.math.sin(ang).toFloat()
      drawLine(color.copy(alpha = (0.35f + lvl * 0.65f) * fade), Offset(cx + cs * baseR, cy + sn * baseR),
        Offset(cx + cs * (baseR + len), cy + sn * (baseR + len)), strokeWidth = 5f, cap = StrokeCap.Round)
    }
  }
}

/** Onda que sai do lado tocado com "−10 s" ou "+10 s". */
@Composable private fun BoxScope.SeekRipple(id: Long, dir: Int) {
  val v = remember(id) { Animatable(0f) }
  LaunchedEffect(id) { v.animateTo(1f, tween(650, easing = FastOutSlowInEasing)) }
  if (v.value >= 1f) return
  Box(Modifier.align(if (dir < 0) Alignment.CenterStart else Alignment.CenterEnd).fillMaxHeight().fillMaxWidth(.5f)
      .graphicsLayer { alpha = 1f - v.value }.background(Brush.horizontalGradient(
        if (dir < 0) listOf(Color.White.copy(.35f), Color.Transparent) else listOf(Color.Transparent, Color.White.copy(.35f)))),
    contentAlignment = Alignment.Center) {
    Text(if (dir < 0) "−10 s" else "+10 s", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold,
      modifier = Modifier.graphicsLayer { val s = .8f + v.value * .4f; scaleX = s; scaleY = s; translationX = dir * v.value * 40f })
  }
}

/** Temporizador pra dormir: 15, 30 ou 60 min, com o tempo restante no botão. */
@Composable private fun SleepButton(now: NowPlaying, a: Actions) {
  var open by remember { mutableStateOf(false) }
  Box {
    val left = LocalSleep.current.let { at -> if (at > 0) ((at - System.currentTimeMillis()) / 60_000 + 1).coerceAtLeast(1) else 0 }
    Row(Modifier.clip(CircleShape).background(if (left > 0) Color.White.copy(.9f) else Color.Transparent).clickable { open = true }
        .padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
      Icon(Icons.Rounded.Bedtime, "Temporizador", tint = if (left > 0) Color.Black else Color.White, modifier = Modifier.size(20.dp))
      if (left > 0) { Spacer(Modifier.width(6.dp)); Text("$left min", color = Color.Black, fontSize = 12.sp) }
    }
    DropdownMenu(open, { open = false }, shape = RoundedCornerShape(18.dp)) {
      listOf(15, 30, 60).forEach { m -> DropdownMenuItem({ Text("Parar em $m min") }, { open = false; a.sleep(m) }) }
      if (left > 0) DropdownMenuItem({ Text("Cancelar temporizador") }, { open = false; a.sleep(0) })
    }
  }
}
val LocalSleep = androidx.compose.runtime.compositionLocalOf { 0L }

