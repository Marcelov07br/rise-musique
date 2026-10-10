package com.rise.musique.ui

import android.app.Activity
import android.media.AudioManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.AsyncImage
import com.rise.musique.VideoHolder
import com.rise.musique.VideoQuality
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private fun t(ms: Long): String { val s = (ms / 1000).coerceAtLeast(0); return if (s >= 3600) "%d:%02d:%02d".format(s / 3600, s / 60 % 60, s % 60) else "%d:%02d".format(s / 60, s % 60) }

/** Full video experience: portrait (video on top + details) or landscape/full screen (video fills, glass overlay). In PiP only the picture. */
@Composable fun VideoPlayerScreen(v: VideoUi, a: VideoActions, pip: Boolean, modifier: Modifier = Modifier) {
  if (pip) { Box(modifier.fillMaxSize().background(Color.Black)) { VideoSurface(v, Modifier.fillMaxSize(), 1f) }; return }
  BackHandler { if (v.fullscreen) a.fullscreen(false) else a.close() }
  val haptics = rememberHaptics()
  var shown by remember { mutableStateOf(true) }
  val alpha = remember { Animatable(1f) }
  LaunchedEffect(shown) { alpha.animateTo(if (shown) 1f else 0f, if (shown) Motion.enter(Motion.Short) else Motion.exit(Motion.Medium)) }
  LaunchedEffect(shown, v.playing) { if (shown && v.playing) { delay(3500); shown = false } }
  BoxWithConstraints(modifier.fillMaxSize().background(Color.Black)) {
    val landscape = maxWidth > maxHeight
    val immersive = landscape || v.fullscreen
    var zoom by remember { mutableFloatStateOf(1f) }
    val videoBox = @Composable { m: Modifier ->
      Box(m.clip(RoundedCornerShape(0.dp))) {
        VideoSurface(v, Modifier.fillMaxSize(), zoom)
        Gestures(v, a, { shown = !shown }, { z -> zoom = (zoom * z).coerceIn(1f, 3f) }, Modifier.matchParentSize())
        Overlay(v, a, immersive, Modifier.matchParentSize().graphicsLayer { this.alpha = alpha.value.coerceIn(0f, 1f) }, enabled = shown, haptics = haptics)
      }
    }
    if (immersive) videoBox(Modifier.fillMaxSize())
    else Column(Modifier.fillMaxSize().statusBarsPadding()) {
      videoBox(Modifier.fillMaxWidth().aspectRatio(v.aspect.coerceIn(.5f, 2.4f)))
      Details(v, a, haptics)
    }
  }
}

/** The actual picture (PlayerView renders video + subtitles); thumbnail in previews/tests or while there's no player. */
@androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])
@Composable fun VideoSurface(v: VideoUi, m: Modifier, zoom: Float) {
  val player = VideoHolder.player
  Box(m.graphicsLayer { val z = zoom.coerceIn(1f, 3f); scaleX = z; scaleY = z; clip = true }, contentAlignment = Alignment.Center) {
    if (LocalInspectionMode.current || player == null) AsyncImage(v.thumb, null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
    else AndroidView({ ctx -> androidx.media3.ui.PlayerView(ctx).apply { useController = false; setShutterBackgroundColor(android.graphics.Color.BLACK)
        setKeepContentOnPlayerReset(true); this.player = player } }, Modifier.fillMaxSize(), update = { it.player = player })
  }
}

/** Double-tap left/right = ∓10 s; vertical swipe left = brightness, right = volume; pinch = zoom. Single tap toggles the controls. */
@Composable private fun Gestures(v: VideoUi, a: VideoActions, toggle: () -> Unit, zoom: (Float) -> Unit, m: Modifier) {
  val ctx = LocalContext.current
  val haptics = rememberHaptics()
  var hint by remember { mutableStateOf<Pair<ImageVector, String>?>(null) }
  val scope = rememberCoroutineScope()
  fun flash(icon: ImageVector, text: String) { hint = icon to text; scope.launch { delay(700); if (hint?.second == text) hint = null } }
  Box(m.pointerInput(Unit) { detectTapGestures(onTap = { toggle() }, onDoubleTap = { o ->
        haptics.tick(); if (o.x < size.width / 2) { a.seekBy(-10_000); flash(Icons.Rounded.Replay10, "−10 s") } else { a.seekBy(10_000); flash(Icons.Rounded.Forward10, "+10 s") } }) }
    .pointerInput(Unit) { detectTransformGestures { _, _, z, _ -> if (z != 1f) zoom(z) } }
    .pointerInput(Unit) {
      var left = true; var acc = 0f
      detectVerticalDragGestures(onDragStart = { o -> left = o.x < size.width / 2; acc = 0f }) { ch, dy -> ch.consume()
        acc -= dy / size.height
        if (left) { val w = (ctx as? Activity)?.window ?: return@detectVerticalDragGestures
          val cur = w.attributes.screenBrightness.takeIf { it >= 0 } ?: .5f
          val nv = (cur - dy / size.height).coerceIn(.02f, 1f); w.attributes = w.attributes.apply { screenBrightness = nv }
          flash(Icons.Rounded.LightMode, "Brilho ${(nv * 100).toInt()}%")
        } else { val am = ctx.getSystemService(AudioManager::class.java); val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
          if (kotlin.math.abs(acc) * max >= 1f) { val step = if (acc > 0) 1 else -1; acc = 0f
            val nv = (am.getStreamVolume(AudioManager.STREAM_MUSIC) + step).coerceIn(0, max); am.setStreamVolume(AudioManager.STREAM_MUSIC, nv, 0); haptics.tick()
            flash(Icons.Rounded.VolumeUp, "Volume ${nv * 100 / max.coerceAtLeast(1)}%") } }
      } }, contentAlignment = Alignment.Center) {
    hint?.let { (i, txt) -> Row(Modifier.clip(CircleShape).background(Color.Black.copy(.55f)).padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
      Icon(i, null, tint = Color.White); Spacer(Modifier.width(8.dp)); Text(txt, color = Color.White, fontWeight = FontWeight.SemiBold) } }
  }
}

@Composable private fun Overlay(v: VideoUi, a: VideoActions, immersive: Boolean, m: Modifier, enabled: Boolean, haptics: Haptics) {
  Box(m.background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Color.Black.copy(.55f), Color.Transparent, Color.Transparent, Color.Black.copy(.6f))))) {
    if (!enabled) return@Box
    Row(Modifier.fillMaxWidth().align(Alignment.TopCenter).then(if (immersive) Modifier.displayCutoutPadding().padding(12.dp) else Modifier.padding(8.dp)),
      verticalAlignment = Alignment.CenterVertically) {
      GlassButton(if (immersive && v.fullscreen) Icons.Rounded.FullscreenExit else Icons.Rounded.KeyboardArrowDown, if (v.fullscreen) "Sair da tela cheia" else "Fechar vídeo") {
        haptics.tick(); if (v.fullscreen) a.fullscreen(false) else a.close() }
      Spacer(Modifier.width(8.dp))
      if (immersive) Column(Modifier.weight(1f)) { Text(v.title, color = Color.White, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(v.channel, color = Color.White.copy(.7f), fontSize = 12.sp, maxLines = 1) } else Spacer(Modifier.weight(1f))
      if (v.subtitles.isNotEmpty()) GlassButton(if (v.subtitle != null) Icons.Rounded.ClosedCaption else Icons.Rounded.ClosedCaptionDisabled, "Legendas") {
        haptics.tick(); a.subtitle(if (v.subtitle != null) null else v.subtitles.first()) }
      Spacer(Modifier.width(8.dp))
      GlassButton(Icons.Rounded.PictureInPictureAlt, "Picture-in-picture") { haptics.tick(); a.pip() }
    }
    Row(Modifier.align(Alignment.Center), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(28.dp)) {
      GlassButton(Icons.Rounded.Replay10, "Voltar 10 segundos") { haptics.tick(); a.seekBy(-10_000) }
      Box(Modifier.size(72.dp), contentAlignment = Alignment.Center) {
        if (v.loading) CircularProgressIndicator(Modifier.size(56.dp), color = Color.White, strokeWidth = 3.dp)
        GlassBox(Modifier.size(64.dp), CircleShape) { Box(Modifier.matchParentSize().clickable { haptics.press(); a.playPause() }, contentAlignment = Alignment.Center) {
          Icon(if (v.playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, if (v.playing) "Pausar" else "Tocar", tint = Color.White, modifier = Modifier.size(36.dp)) } }
      }
      GlassButton(Icons.Rounded.Forward10, "Avançar 10 segundos") { haptics.tick(); a.seekBy(10_000) }
    }
    Column(Modifier.fillMaxWidth().align(Alignment.BottomCenter).then(if (immersive) Modifier.navigationBarsPadding().padding(horizontal = 24.dp, vertical = 12.dp) else Modifier.padding(horizontal = 12.dp, vertical = 4.dp))) {
      var drag by remember { mutableStateOf<Float?>(null) }
      val frac = drag ?: if (v.duration > 0) (v.position.toFloat() / v.duration).coerceIn(0f, 1f) else 0f
      ThinSlider(frac, { drag = it.coerceIn(0f, 1f) }, { drag?.let { a.seek((it * v.duration).toLong()) }; drag = null }, Modifier.fillMaxWidth())
      Row(verticalAlignment = Alignment.CenterVertically) {
        Text("${t((frac * v.duration).toLong())} / ${t(v.duration)}", color = Color.White.copy(.85f), fontSize = 12.sp, modifier = Modifier.weight(1f))
        if (v.hasNext) IconButton({ haptics.tick(); a.next() }, Modifier.size(48.dp)) { Icon(Icons.Rounded.SkipNext, "Próximo vídeo", tint = Color.White) }
        IconButton({ haptics.tick(); a.fullscreen(!v.fullscreen) }, Modifier.size(48.dp)) {
          Icon(if (v.fullscreen) Icons.Rounded.FullscreenExit else Icons.Rounded.Fullscreen, if (v.fullscreen) "Sair da tela cheia" else "Tela cheia", tint = Color.White) }
      }
    }
  }
}

@Composable private fun GlassButton(icon: ImageVector, label: String, click: () -> Unit) =
  GlassBox(Modifier.size(48.dp), CircleShape) { Box(Modifier.matchParentSize().clickable(onClick = click), contentAlignment = Alignment.Center) {
    Icon(icon, label, tint = Color.White) } }

/** Portrait details: title, channel, quality chips and subtitle chips. */
@Composable private fun Details(v: VideoUi, a: VideoActions, haptics: Haptics) {
  Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
    Text(v.title, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold, maxLines = 3, overflow = TextOverflow.Ellipsis)
    Text(v.channel, color = Color.White.copy(.65f), fontSize = 15.sp)
    v.error?.let { Text(it, color = Color(0xFFFF8A80), fontSize = 14.sp) }
    if (v.qualities.isNotEmpty()) {
      Text("Qualidade", color = Color.White.copy(.65f), fontSize = 13.sp)
      Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        v.qualities.sortedDescending().forEach { h -> Pill(VideoQuality.label(h), h == v.quality) { haptics.tick(); a.quality(h) } } }
    }
    if (v.subtitles.isNotEmpty()) {
      Text("Legendas", color = Color.White.copy(.65f), fontSize = 13.sp)
      Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Pill("Desligadas", v.subtitle == null) { haptics.tick(); a.subtitle(null) }
        v.subtitles.forEach { s -> Pill(s, v.subtitle == s) { haptics.tick(); a.subtitle(s) } } }
    }
    if (v.local) Text("Arquivo da biblioteca", color = Color.White.copy(.5f), fontSize = 13.sp)
  }
}

@Composable private fun Pill(text: String, on: Boolean, click: () -> Unit) =
  Box(Modifier.heightIn(min = 40.dp).clip(CircleShape).background(if (on) Color.White else Color.White.copy(.1f)).clickable(onClick = click).padding(horizontal = 16.dp, vertical = 10.dp),
    contentAlignment = Alignment.Center) { Text(text, color = if (on) Color.Black else Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium) }
