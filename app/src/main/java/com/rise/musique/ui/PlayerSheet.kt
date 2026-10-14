package com.rise.musique.ui
import androidx.compose.ui.draw.drawBehind
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.SideEffect
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
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import androidx.compose.ui.util.lerp
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import coil.compose.AsyncImage
import coil.request.ImageRequest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

// 0.25: a mola única (ζ0.8, quique travado); os limites 0..1 do Animatable cortam qualquer passada. Carrega a velocidade do dedo.
private val bouncy = Motion.spring<Float>(247f)
private fun fmtMs(ms: Long) = (ms / 1000).let { "%d:%02d".format(it / 60, it % 60) }

/**
 * Mini player que vira player em tela cheia. `f` vai de 0 (mini) a 1 (cheio) e controla tudo:
 * tamanho e posição da capa, fundo desfocado, cantos e controles. Arrastar move `f` junto com o dedo;
 * soltar assenta com mola. O voltar com prévia (Android 14+) encolhe a tela seguindo o gesto.
 */
@Composable fun PlayerSheet(now: NowPlaying, a: Actions, initialExpanded: Boolean = false, initialLyrics: Boolean = false,
                            queue: List<HistoryItem> = emptyList(), local: Boolean = false, snippetBusy: Boolean = false, initialShare: Boolean = false, initialFraction: Float? = null, liked: Boolean = false) {
  val reduce = rememberReduceMotion()
  val c = MaterialTheme.colorScheme
  var share by remember { mutableStateOf(initialShare) }
  if (share) ShareSheet(now, local, snippetBusy, a) { share = false }
  val scope = rememberCoroutineScope()
  // Limites 0..1: a mola para no limite em vez de oscilar em volta dele (sem cruzar limiares várias vezes).
  val f = remember { Animatable(initialFraction ?: if (initialExpanded) 1f else 0f).apply { updateBounds(0f, 1f) } }
  var back by remember { mutableFloatStateOf(0f) }
  val density = LocalDensity.current
  val haptics = rememberHaptics()
  var showLyrics by remember { mutableStateOf(initialLyrics) }

  // Jank-free: o layout é fixo (tela cheia) e nunca muda durante a expansão. Tudo que anima (posição, recorte, cantos,
  // escala da capa, opacidades) é lido só dentro de graphicsLayer/draw, então os quadros da animação não recompõem
  // nem remedem nada. Só os limiares (mostrar/esconder partes) viram estado derivado e recompõem uma vez ao cruzar.
  val pf = { f.value.coerceIn(0f, 1f) } // nunca sai de 0..1, nem em rebote
  val expanded by remember { derivedStateOf { f.value > 0.5f } }
  val collapsed by remember { derivedStateOf { f.value < .05f } }
  val showMini by remember { derivedStateOf { f.value < .6f } }
  val showGlass by remember { derivedStateOf { f.value < .99f } }
  val showFull by remember { derivedStateOf { f.value > .4f } }
  val showBg by remember { derivedStateOf { f.value > 0.01f } }
  val settled by remember { derivedStateOf { f.value > .9f } }
  SideEffect { PlayerProbe.sheet++ }

  // 0.25: capa tocada numa lista voa até o player (CoverFlight). Troca de faixa esquece a origem.
  var flightKey by remember { mutableStateOf<String?>(null) }
  LaunchedEffect(CoverFlight.request) { val r = CoverFlight.request
    if (r != 0L && r != CoverFlight.handled && CoverFlight.origin != null) { CoverFlight.handled = r; flightKey = null; f.animateTo(1f, Motion.coverOpen()) } }
  LaunchedEffect(now.key) { if (CoverFlight.origin != null) { if (flightKey == null) flightKey = now.key else if (flightKey != now.key) CoverFlight.forget() } }
  val collapse: () -> Unit = { scope.launch { f.animateTo(0f, if (CoverFlight.origin != null) Motion.coverClose() else bouncy); if (f.value <= 0f) CoverFlight.forget() } }

  PredictiveBackHandler(enabled = expanded) { progress ->
    try { progress.collect { back = it.progress }; collapse(); back = 0f }
    catch (e: CancellationException) { animate(back, 0f, animationSpec = bouncy) { v, _ -> back = v } }
  }

  BoxWithConstraints(Modifier.fillMaxSize()) {
    SideEffect { PlayerProbe.body++ }
    val fullH = constraints.maxHeight.toFloat()
    val fullW = constraints.maxWidth.toFloat()
    val miniH = with(density) { 68.dp.toPx() }
    val margin = with(density) { 16.dp.toPx() }
    val dragState = rememberDraggableState { d -> scope.launch { f.snapTo((f.value - d / (fullH - miniH)).coerceIn(0f, 1f)) } }
    // Mini: cartão compacto (miniH) com margens iguais dos lados, logo acima da dock (~82dp) e da barra de gestos.
    val navBottom = WindowInsets.navigationBars.getBottom(density).toFloat()
    val dockH = with(density) { GlassDock.Height.toPx() }
    val miniTop = fullH - miniH - margin - navBottom - dockH
    val surface = c.surface

    Box(Modifier.fillMaxSize()
        .graphicsLayer {
          val p = pf(); val b = back
          translationY = lerp(miniTop, 0f, p)
          val sc = 1f - b * 0.12f; scaleX = sc; scaleY = sc
          transformOrigin = androidx.compose.ui.graphics.TransformOrigin(.5f, 0f)
          shape = SheetShape(lerp(margin, 0f, p), lerp(miniH, fullH, p).coerceIn(miniH, fullH), (lerp(34f, 0f, p) + b * 36f) * density.density)
          clip = true }
        .drawBehind { drawRect(surface.copy(alpha = (pf() * 1.6f).coerceIn(0f, 1f))) }
        .draggable(dragState, Orientation.Vertical, onDragStarted = { CoverFlight.forget(); haptics.tick() }, onDragStopped = { v ->
          val target = when { v < -Motion.FlingVelocity -> 1f; v > Motion.FlingVelocity -> 0f; else -> if (f.value > .5f) 1f else 0f }
          haptics.confirm()
          f.animateTo(target, bouncy, initialVelocity = -v / (fullH - miniH)) })) {
      SideEffect { PlayerProbe.sheetBody++ }

      // Mini player de vidro: cópia desfocada da capa + véu translúcido + borda de luz (só a faixa do mini, sem relayout).
      if (showGlass) Box(Modifier.padding(horizontal = 16.dp).fillMaxWidth().height(68.dp).graphicsLayer { alpha = (1f - pf()).coerceIn(0f, 1f) }
        .glassTilt(LocalTilt.current).liquidGlass(RoundedCornerShape(34.dp), c.background.luminance() < .5f))
      // Fundo vivo: degradê em malha com as cores da capa, se movendo devagar.
      if (showBg) Box(Modifier.fillMaxSize().graphicsLayer { alpha = pf() }) {
        LivingArtwork(now.art, Modifier.fillMaxSize(), dim = .2f)
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(.15f), Color.Black.copy(.55f)))))
      }

      val maxW = this@BoxWithConstraints.maxWidth
      val maxH = this@BoxWithConstraints.maxHeight
      // Paisagem / tablet deitado: capa à esquerda, letra ou fila à direita.
      val wide = maxW > maxH && maxW >= 560.dp
      // Retrato: a capa é quadrada e centrada no espaço entre a barra de cima e os controles (medidos), com margens iguais.
      val insetTop = with(density) { WindowInsets.safeDrawing.getTop(density).toDp() }
      val insetBottom = with(density) { WindowInsets.safeDrawing.getBottom(density).toDp() }
      var controlsH by remember { mutableStateOf(340.dp) }
      val artSpace = (maxH - insetTop - 56.dp - controlsH - insetBottom - 12.dp).coerceAtLeast(120.dp)
      val artFull = if (wide) minOf(maxH - 120.dp, maxW / 2 - 64.dp).coerceIn(120.dp, 420.dp) else PlayerLayout.art(maxW, artSpace)
      // A capa tem SEMPRE o tamanho cheio no layout; mini = mesma capa reduzida/transladada via graphicsLayer.
      val fullX = if (wide) (maxW / 2 - artFull) / 2 else (maxW - artFull) / 2
      val fullY = if (wide) (maxH - artFull) / 2 else insetTop + 56.dp + (artSpace - artFull) / 2
      val artFullPx = with(density) { artFull.toPx() }; val fullXPx = with(density) { fullX.toPx() }; val fullYPx = with(density) { fullY.toPx() }
      val miniArt = with(density) { 48.dp.toPx() }; val miniX = margin + with(density) { 10.dp.toPx() }; val miniY = with(density) { 10.dp.toPx() }
      val onBg = if (expanded) Color.White else c.onSurface

      // Capa: o mesmo elemento cresce do mini até o tamanho cheio.
      val artAlphaS = animateFloatAsState(if (showLyrics && !wide && settled) 0f else 1f, Motion.standard(), label = "art")
      // Capa que respira: cheia no play, recua no pause, sempre com mola.
      val breathS = animateFloatAsState(if (now.playing || !expanded || reduce) 1f else 0.84f, Motion.spring(169f), label = "breath")
      // Capa arremessável: arrasta na horizontal; forte o bastante troca de faixa, fraco volta com mola.
      val throwX = remember { Animatable(0f) }
      // Profundidade da troca: a capa nova sobe de trás (0 = afundada, 1 = no lugar).
      val depth = remember { Animatable(1f) }
      val flying by remember { derivedStateOf { CoverFlight.origin != null } }
      val maxWpx = fullW
      val artColor = Color(0xFFBEBEC4)
      /** Shared mini→full transform for the cover and its ring: translation + scale only (origin top-left). */
      fun androidx.compose.ui.graphics.GraphicsLayerScope.artMove(extra: Float) {
        val p = pf(); val k = lerp(miniArt / artFullPx, 1f, p)
        transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0f)
        // Escala extra (respiração/arremesso) em torno do centro da capa já reduzida.
        val s = k * extra; val shift = artFullPx * k * (1f - extra) / 2f
        translationX = lerp(miniX, fullXPx, p) - fullXPx + shift + throwX.value
        translationY = lerp(miniY, fullYPx, p) - fullYPx + shift
        scaleX = s; scaleY = s
      }
      if (expanded && !(showLyrics && !wide)) AudioRing(Modifier.offset(fullX, fullY).size(artFull)
        .graphicsLayer { artMove(breathS.value); alpha = if (flying) 0f else artAlphaS.value }, artColor, now.playing)
      var tapHint by remember { mutableStateOf<Pair<Long, Int>?>(null) }
      val interactive = settled && !showLyrics
      // Capa seguinte/anterior visível atrás enquanto a atual é arrastada (profundidade).
      if (interactive && !reduce) for (dir in intArrayOf(1, -1)) {
        val behind = queue.getOrNull(now.index + dir)?.art.orEmpty()
        if (behind.isNotBlank()) Box(Modifier.offset(fullX, fullY).size(artFull).graphicsLayer {
            val d = (-dir * throwX.value / (maxWpx * .5f)).coerceIn(0f, 1f); artMove(.86f + .08f * d); alpha = d * artAlphaS.value
            shape = RoundedCornerShape((28f * density.density / (.86f + .08f * d)).coerceAtLeast(0f)); clip = true }) {
          AsyncImage(behind, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
      }
      Box(Modifier.offset(fullX, fullY).size(artFull)
          .graphicsLayer {
            val p = pf(); val k = lerp(miniArt / artFullPx, 1f, p)
            val dp = depth.value
            artMove(lerp(1f, breathS.value, p) * (1f - kotlin.math.abs(throwX.value) / maxWpx * .15f) * lerp(.86f, 1f, dp))
            rotationZ = throwX.value / maxWpx * 10f; rotationY = -throwX.value / maxWpx * 16f; cameraDistance = 16f * density.density
            alpha = if (flying) 0f else artAlphaS.value * lerp(.3f, 1f, dp)
            // Cantos e sombra compensados pela escala, pra parecerem 12dp no mini e 28dp no cheio.
            shape = RoundedCornerShape((lerp(12f, 28f, p) * density.density / k.coerceAtLeast(.05f)).coerceAtLeast(0f))
            clip = true; shadowElevation = lerp(0f, 24f, p) * density.density }
          .then(if (interactive) Modifier.pointerInput(now.key) { detectTapGestures(onDoubleTap = { o ->
            val dir = if (o.x < size.width / 2) -1 else 1
            a.seek((now.position + dir * 10_000).coerceIn(0, now.duration.coerceAtLeast(0))); tapHint = System.nanoTime() to dir }) } else Modifier)
          .then(if (interactive) Modifier.draggable(rememberDraggableState { d -> scope.launch { throwX.snapTo(throwX.value + d) } },
            Orientation.Horizontal, onDragStopped = { v ->
              val go = if (throwX.value < -maxWpx / 3 || v < -1800f) 1 else if (throwX.value > maxWpx / 3 || v > 1800f) -1 else 0
              val canGo = (go == 1 && now.hasNext) || (go == -1 && now.hasPrev)
              if (go != 0 && canGo) {
                haptics.press(); throwX.animateTo(-go * maxWpx * 1.3f, Motion.spring(325f, 1f))
                if (go == 1) a.next() else a.prev()
                if (reduce) throwX.snapTo(0f) else { depth.snapTo(0f); throwX.snapTo(0f); depth.animateTo(1f, Motion.spring(Motion.SpringSoft)); return@draggable }
              }
              throwX.animateTo(0f, Motion.spring(208f))
            }) else Modifier).background(c.outline), contentAlignment = Alignment.Center) {
        // 0.26: capa animada (mesmo elemento no mini e no cheio = um decodificador só); estática durante o voo da capa e na letra.
        if (now.art.isNotBlank()) MotionCover(now.artist, now.album, active = now.playing && !flying && !(showLyrics && !wide) && !now.isPreview,
            priority = if (expanded) 3 else 1, modifier = Modifier.fillMaxSize()) { videoOn ->
          AsyncImage(now.art, null, Modifier.fillMaxSize().kenBurns(now.playing && interactive && !reduce && !videoOn), contentScale = ContentScale.Crop) }
        else Icon(Icons.Rounded.MusicNote, null, tint = c.onSurfaceVariant, modifier = Modifier.fillMaxSize(.4f))
        tapHint?.let { (id, dir) -> SeekRipple(id, dir) }
      }

      // Linha do mini player (some ao expandir).
      // O toque para abrir fica só na faixa do mini (a camada é do tamanho da tela; semântica/acessibilidade com os limites certos).
      if (showMini) Row(Modifier.fillMaxWidth().height(68.dp)
        .clickable(enabled = collapsed, indication = null, interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }) {
          scope.launch { f.animateTo(1f, Motion.coverOpen()) } }
        .graphicsLayer { alpha = (1f - pf() / .6f).coerceIn(0f, 1f) }.padding(start = 86.dp, end = 24.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
          Text(now.title, color = c.onSurface, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
          Text(if (now.isPreview) "Prévia · ${now.artist}" else if (now.streaming) "${now.artist} · streaming" else now.artist, color = c.onSurfaceVariant, fontSize = 12.sp, maxLines = 1)
        }
        PlayButton(now, a, 44.dp, c.primary, c.onPrimary)
        if (!now.isPreview && now.hasNext) IconButton(a.next) { Icon(PhIcons.Next, "Próxima", tint = c.onSurface, modifier = Modifier.size(22.dp)) }
        IconButton(a.closePlayer) { Icon(PhIcons.Close, "Fechar", tint = c.onSurfaceVariant, modifier = Modifier.size(18.dp)) }
      }

      // Capa em voo (lista → player e de volta): fora do recorte do cartão, desenhada em cima.
      if (flying && showBg) { val o = CoverFlight.origin
        if (o != null) Box(Modifier.offset(fullX, fullY).size(artFull).graphicsLayer {
            val p = pf(); val k = lerp(o.width / artFullPx, 1f, p).coerceAtLeast(.05f)
            transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0f)
            translationX = lerp(o.left, fullXPx, p) - fullXPx; translationY = lerp(o.top - lerp(miniTop, 0f, p), fullYPx, p) - fullYPx
            scaleX = k; scaleY = k
            shape = RoundedCornerShape((lerp(14f, 28f, p) * density.density / k).coerceAtLeast(0f)); clip = true; shadowElevation = lerp(0f, 24f, p) * density.density }
          .background(c.outline)) { AsyncImage(CoverFlight.art.ifBlank { now.art }, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
      }

      // Conteúdo do player cheio (surge ao expandir).
      val rise = with(density) { 40.dp.toPx() }
      if (showFull) Column(Modifier.fillMaxSize().safeDrawingPadding().padding(start = if (wide) maxW / 2 - 12.dp else 0.dp)
          .graphicsLayer { val p = pf(); alpha = ((p - .4f) / .6f).coerceIn(0f, 1f); translationY = lerp(rise, 0f, p) }.padding(horizontal = 28.dp)) {
        Row(Modifier.fillMaxWidth().height(56.dp), verticalAlignment = Alignment.CenterVertically) {
          IconButton(collapse) { Icon(Icons.Rounded.KeyboardArrowDown, "Recolher", tint = Color.White) }
          Spacer(Modifier.weight(1f))
          Box(Modifier.size(width = 36.dp, height = 5.dp).clip(CircleShape).background(Color.White.copy(.35f)))
          Spacer(Modifier.weight(1f))
          IconButton({ share = true }, enabled = !now.isPreview) { Icon(Icons.Rounded.IosShare, "Compartilhar trecho", tint = Color.White) }
          SleepButton(now, a)
        }
        if (wide) Box(Modifier.fillMaxWidth().weight(1f)) {
          if (showLyrics || now.isPreview) LyricsView(now, a, Modifier.fillMaxSize().padding(vertical = 8.dp))
          else UpNext(now, queue, a, Modifier.fillMaxSize())
        } else Box(Modifier.fillMaxWidth().height(artSpace)) {
          if (showLyrics) LyricsView(now, a, Modifier.fillMaxSize().padding(top = 8.dp, bottom = 8.dp))
        }
        Column(Modifier.wrapContentHeight(Alignment.Top, unbounded = true).onSizeChanged { val h = with(density) { it.height.toDp() }; if (!wide && kotlin.math.abs((h - controlsH).value) > 1f) controlsH = h }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Column(Modifier.weight(1f)) {
            Text(now.title, color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold, lineHeight = 32.sp, letterSpacing = (-.5f).sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(if (now.isPreview) "Trecho · ${now.artist}" else now.artist, color = Color.White.copy(.62f), fontSize = 20.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
          }
          if (!now.isPreview) { Spacer(Modifier.width(12.dp)); LikeButton(liked, a.likeNow, size = 52.dp) }
        }
        if (now.streaming) { Spacer(Modifier.height(10.dp)); StreamBar(now, a) }
        Spacer(Modifier.height(18.dp))
        var drag by remember { mutableStateOf<Float?>(null) }
        val dur = now.duration.coerceAtLeast(1)
        ThinSlider(drag ?: (now.position.toFloat() / dur), { drag = it }, { drag?.let { a.seek((it * dur).toLong()) }; drag = null })
        val shownPos = drag?.let { (it * dur).toLong() } ?: now.position
        Row(verticalAlignment = Alignment.CenterVertically) { RollingText(fmtMs(shownPos), Color.White.copy(.6f), 12.sp)
          Spacer(Modifier.weight(1f))
          if (now.quality.isNotBlank()) Text(now.quality, color = Color.White.copy(.55f), fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 8.dp))
          Spacer(Modifier.weight(1f))
          RollingText("-" + fmtMs((now.duration - shownPos).coerceAtLeast(0)), Color.White.copy(.6f), 12.sp, forward = false) }
        Spacer(Modifier.height(24.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
          SideButton(PhIcons.Prev, "Anterior", Modifier.weight(1f), a.prev)
          PlayButton(now, a, 84.dp, Color.White, Color.Black, Modifier.weight(2f), pill = true)
          SideButton(PhIcons.Next, "Próxima", Modifier.weight(1f), enabled = now.hasNext, on = a.next)
        }
        Spacer(Modifier.height(18.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
          ToggleIcon(PhIcons.Shuffle, "Aleatório", now.shuffle, enabled = !now.isPreview, onClick = a.shuffle)
          ToggleIcon(if (now.repeat == 1) PhIcons.RepeatOne else PhIcons.Repeat,
            when (now.repeat) { 1 -> "Repetir faixa"; 2 -> "Repetir fila"; else -> "Repetição desligada" }, now.repeat != 0, enabled = !now.isPreview, onClick = a.repeat)
          ToggleIcon(Icons.Rounded.QueueMusic, "Fila de reprodução", false, enabled = !now.isPreview) { a.showQueue(true) }
          Spacer(Modifier.weight(1f))
          if (now.count > 1) Text("${now.index + 1} de ${now.count}", color = Color.White.copy(.5f), fontSize = 12.sp)
          Spacer(Modifier.weight(1f))
          ToggleIcon(Icons.Rounded.Lyrics, "Letra", showLyrics, enabled = !now.isPreview) { showLyrics = !showLyrics }
        }
        Spacer(Modifier.height(12.dp))
        }
      }
    }
  }
}

@Composable private fun PlayButton(now: NowPlaying, a: Actions, h: Dp, bg: Color, fg: Color, m: Modifier = Modifier, pill: Boolean = false) {
  val src = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
  val haptics = rememberHaptics()
  Box(m.then(if (pill) Modifier.height(h) else Modifier.size(h)).springPress(src).clip(CircleShape).background(bg)
      .clickable(src, null) { haptics.press(); a.playPause() }, contentAlignment = Alignment.Center) {
    if (now.loading) CircularProgressIndicator(Modifier.size(h * .4f), color = fg, strokeWidth = 2.dp)
    else PlayPauseMorph(now.playing, fg, Modifier.size(h * .45f).semantics { contentDescription = if (now.playing) "Pausar" else "Tocar" })
  }
}

@Composable private fun ToggleIcon(icon: androidx.compose.ui.graphics.vector.ImageVector, desc: String, on: Boolean, enabled: Boolean = true, onClick: () -> Unit) {
  val src = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
  val haptics = rememberHaptics()
  Box(Modifier.size(48.dp).springPress(src).clip(CircleShape).background(if (on) Color.White.copy(.9f) else Color.White.copy(.08f))
      .alpha(if (enabled) 1f else .3f).clickable(src, null, enabled = enabled) { haptics.tick(); onClick() }, contentAlignment = Alignment.Center) {
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
  val reduce = rememberReduceMotion()
  // A rolagem segue a música com a mola única: centraliza a linha atual (salta direto se ela estiver fora da tela).
  LaunchedEffect(cur) {
    val info = list.layoutInfo.visibleItemsInfo.firstOrNull { it.index == cur }
    if (info == null || reduce) list.scrollToItem((cur - 2).coerceAtLeast(0))
    else { val vp = list.layoutInfo.viewportEndOffset - list.layoutInfo.viewportStartOffset
      list.animateScrollBy((info.offset + info.size / 2f) - vp * .38f, Motion.spring(Motion.SpringSoft, 1f)) }
  }
  androidx.compose.foundation.lazy.LazyColumn(m, state = list, verticalArrangement = Arrangement.spacedBy(18.dp)) {
    items(lines.size, key = { it }, contentType = { "line" }) { i -> val l = lines[i]
      val dist = kotlin.math.abs(i - cur).coerceAtMost(4)
      // Linha ativa: maior e nítida; as outras menores, apagadas e desfocadas conforme a distância.
      val focus by animateFloatAsState(if (i == cur) 1f else 0f, Motion.spring(169f), label = "focus")
      val fz = focus.coerceIn(0f, 1f)
      val blurDp = if (Build.VERSION.SDK_INT >= 31) (dist * 1.6f * (1f - fz)).dp else 0.dp
      Text(l.text.ifBlank { "♪" }, Modifier.fillMaxWidth()
        .graphicsLayer { val sc = .84f + .2f * fz; scaleX = sc; scaleY = sc; alpha = .32f + .68f * fz
          transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, .5f) }
        .then(if (blurDp > 0.dp) Modifier.blur(blurDp, androidx.compose.ui.draw.BlurredEdgeTreatment.Unbounded) else Modifier)
        .clickable(indication = null, interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }) { a.seek(l.ms) },
        color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold, lineHeight = 34.sp,
        // Brilho da linha atual: sombra branca difusa que acende com o foco.
        style = if (fz > .01f) androidx.compose.ui.text.TextStyle(shadow = Shadow(Color.White.copy(alpha = .55f * fz), blurRadius = 28f * fz)) else androidx.compose.ui.text.TextStyle.Default)
    }
  }
}

@Composable private fun SideButton(icon: androidx.compose.ui.graphics.vector.ImageVector, desc: String, m: Modifier, on: () -> Unit) =
  SideButton(icon, desc, m, true, on)
@Composable private fun SideButton(icon: androidx.compose.ui.graphics.vector.ImageVector, desc: String, m: Modifier, enabled: Boolean, on: () -> Unit) {
  val src = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
  val haptics = rememberHaptics()
  GlassBox(m.height(84.dp).alpha(if (enabled) 1f else .35f).springPress(src), CircleShape) {
    Box(Modifier.matchParentSize().clickable(src, null, enabled = enabled) { haptics.press(); on() }, contentAlignment = Alignment.Center) {
      Icon(icon, desc, tint = Color.White, modifier = Modifier.size(30.dp)) } }
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
  val fade by animateFloatAsState(if (playing) 1f else 0f, Motion.standard(Motion.Long), label = "ring")
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
  LaunchedEffect(id) { v.animateTo(1f, Motion.enter(Motion.ExtraLong)) }
  if (v.value >= 1f) return
  Box(Modifier.align(if (dir < 0) Alignment.CenterStart else Alignment.CenterEnd).fillMaxHeight().fillMaxWidth(.5f)
      .graphicsLayer { alpha = 1f - v.value }.background(Brush.horizontalGradient(
        if (dir < 0) listOf(Color.White.copy(.35f), Color.Transparent) else listOf(Color.Transparent, Color.White.copy(.35f)))),
    contentAlignment = Alignment.Center) {
    Text(if (dir < 0) "−10 s" else "+10 s", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold,
      modifier = Modifier.graphicsLayer { val s = .8f + v.value * .4f; scaleX = s; scaleY = s; translationX = dir * v.value * 40f })
  }
}

/** Temporizador pra dormir: minutos ou fim da faixa, com o tempo restante no botão; o volume desce aos poucos antes de parar. */
@Composable private fun SleepButton(now: NowPlaying, a: Actions) {
  var open by remember { mutableStateOf(false) }
  Box {
    val at = LocalSleep.current
    val endOfTrack = at == com.rise.musique.PlaybackService.END_OF_TRACK
    val left = if (at > 0) ((at - System.currentTimeMillis()) / 60_000 + 1).coerceAtLeast(1) else 0
    val active = endOfTrack || left > 0
    Row(Modifier.clip(CircleShape).background(if (active) Color.White.copy(.9f) else Color.Transparent).clickable { open = true }
        .padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
      Icon(Icons.Rounded.Bedtime, "Temporizador", tint = if (active) Color.Black else Color.White, modifier = Modifier.size(20.dp))
      if (active) { Spacer(Modifier.width(6.dp)); Text(if (endOfTrack) "Fim da faixa" else "$left min", color = Color.Black, fontSize = 12.sp) }
    }
    DropdownMenu(open, { open = false }, shape = RoundedCornerShape(18.dp)) {
      DropdownMenuItem({ Text("No fim desta faixa") }, { open = false; a.sleep(-1) }, leadingIcon = { Icon(Icons.Rounded.MusicNote, null) })
      listOf(15, 30, 45, 60, 90).forEach { m -> DropdownMenuItem({ Text("Parar em $m min") }, { open = false; a.sleep(m) }) }
      if (active) DropdownMenuItem({ Text("Cancelar temporizador") }, { open = false; a.sleep(0) }, leadingIcon = { Icon(Icons.Rounded.Close, null) })
    }
  }
}
val LocalSleep = androidx.compose.runtime.compositionLocalOf { 0L }


/** Próximas da fila (painel da direita em paisagem). */
@Composable private fun UpNext(now: NowPlaying, queue: List<HistoryItem>, a: Actions, m: Modifier) {
  val next = queue.drop(now.index + 1).take(30)
  Column(m) {
    Text("A seguir", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(8.dp))
    if (next.isEmpty()) Text("Nada na fila", color = Color.White.copy(.6f))
    androidx.compose.foundation.lazy.LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
      items(next.size, key = { i -> next[i].uri + "#" + i }) { i -> val h = next[i]
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable { a.playQueue(now.index + 1 + i) }.padding(6.dp),
          verticalAlignment = Alignment.CenterVertically) {
          Box(Modifier.size(44.dp).clip(RoundedCornerShape(10.dp)).background(Color.White.copy(.1f))) {
            if (h.art.isNotBlank()) AsyncImage(h.art, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
          Spacer(Modifier.width(12.dp))
          Column(Modifier.weight(1f)) { Text(h.title, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(h.artist, color = Color.White.copy(.6f), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        }
      }
    }
  }
}

/** Streaming items: where it's coming from + "Guardar" (keeps what already played, else downloads). */
@Composable fun StreamBar(now: NowPlaying, a: Actions) {
  val haptics = rememberHaptics()
  Row(verticalAlignment = Alignment.CenterVertically) {
    Icon(Icons.Rounded.Podcasts, null, tint = Color.White.copy(.55f), modifier = Modifier.size(16.dp)); Spacer(Modifier.width(6.dp))
    Text("STREAMING · YOUTUBE", color = Color.White.copy(.55f), fontSize = 11.sp, letterSpacing = 1.sp, modifier = Modifier.weight(1f))
    val (label, icon) = when (now.saveState) { "saving" -> "Guardando…" to Icons.Rounded.Downloading; "cached", "download" -> "Na biblioteca em breve" to Icons.Rounded.DownloadDone
      else -> "Guardar" to Icons.Rounded.Download }
    GlassBox(Modifier.height(40.dp), CircleShape) {
      Row(Modifier.clickable(enabled = now.saveState.isEmpty()) { haptics.press(); a.saveStream() }.padding(horizontal = 14.dp).fillMaxHeight(), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = Color.White, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(6.dp))
        Text(label, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold) }
    }
  }
}

object PlayerLayout {
  /** Square cover: full width minus 28dp margins (max 420dp), shrunk to fit the space between top bar and controls with 8dp air. */
  fun art(maxW: androidx.compose.ui.unit.Dp, space: androidx.compose.ui.unit.Dp) =
    minOf(maxW - 56.dp, space - 16.dp, 420.dp).coerceAtLeast(120.dp)
}

/** Recorte do cartão: faixa [inset, w-inset] x [0, height] com cantos; recriado por quadro só dentro do graphicsLayer. */
private data class SheetShape(val inset: Float, val height: Float, val radius: Float) : androidx.compose.ui.graphics.Shape {
  override fun createOutline(size: androidx.compose.ui.geometry.Size, layoutDirection: androidx.compose.ui.unit.LayoutDirection, density: androidx.compose.ui.unit.Density) =
    androidx.compose.ui.graphics.Outline.Rounded(androidx.compose.ui.geometry.RoundRect(inset, 0f, (size.width - inset).coerceAtLeast(inset), height.coerceAtMost(size.height),
      androidx.compose.ui.geometry.CornerRadius(radius.coerceAtLeast(0f))))
}

/** Contadores de composição do player (testes verificam que a expansão não recompõe por quadro). */
object PlayerProbe { var sheet = 0; var body = 0; var sheetBody = 0
  fun total() = sheet + body + sheetBody; fun reset() { sheet = 0; body = 0; sheetBody = 0 } }
