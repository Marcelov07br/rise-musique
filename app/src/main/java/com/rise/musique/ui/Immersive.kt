package com.rise.musique.ui

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.compose.ui.layout.boundsInWindow
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.launch

/**
 * 0.25 "Reduzir movimento": desliga parallax, partículas e movimentos grandes. Também vale quando a escala de animação
 * do sistema é 0 (remover animações). Guardado em prefs `rise/reduceMotion`.
 */
object ReduceMotion {
  var user by mutableStateOf(false); private set
  private var loaded = false
  fun load(ctx: Context) { if (loaded) return; loaded = true; user = ctx.getSharedPreferences("rise", 0).getBoolean("reduceMotion", false) }
  fun set(ctx: Context, on: Boolean) { user = on; loaded = true; ctx.getSharedPreferences("rise", 0).edit().putBoolean("reduceMotion", on).apply() }
  fun systemOff(ctx: Context) = runCatching { Settings.Global.getFloat(ctx.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) }.getOrDefault(1f) == 0f
  /** Pure rule (tested): reduce if the user asked or the system animator scale is 0. */
  fun reduced(user: Boolean, animatorScale: Float) = user || animatorScale == 0f
}

@Composable fun rememberReduceMotion(): Boolean {
  val ctx = LocalContext.current
  ReduceMotion.load(ctx)
  val sys = remember(ctx) { ReduceMotion.systemOff(ctx) }
  return ReduceMotion.user || sys
}

// ---------------------------------------------------------------- Like + particles

/** Particle burst geometry, fixed at class load (draw never allocates). */
object LikeBurst {
  const val Count = 10
  val angle = FloatArray(Count) { (it * 360f / Count + if (it % 2 == 0) 0f else 14f) * (Math.PI.toFloat() / 180f) }
  val reach = FloatArray(Count) { if (it % 2 == 0) 1f else .72f }
  /** Distance (fraction of radius) and alpha of a particle at burst progress t 0..1: eases out, fades in the last 60%. */
  fun dist(t: Float) = 1f - (1f - t.coerceIn(0f, 1f)).let { it * it * it }
  fun alpha(t: Float) = if (t <= .4f) 1f else (1f - (t - .4f) / .6f).coerceIn(0f, 1f)
}

/** Botão de curtir do player: coração com pop de mola, partículas discretas ao curtir e háptico leve. */
@Composable fun LikeButton(liked: Boolean, onToggle: () -> Unit, modifier: Modifier = Modifier, size: Dp = 44.dp, tint: Color = Color.White) {
  val reduce = rememberReduceMotion()
  val haptics = rememberHaptics()
  val scope = rememberCoroutineScope()
  var shown by remember { mutableStateOf(liked) }
  LaunchedEffect(liked) { shown = liked }
  val pop = remember { Animatable(1f) }
  val burst = remember { Animatable(1f) }
  val accent = Color(0xFFFF5C8A)
  Box(modifier.size(size).clickable(remember { MutableInteractionSource() }, null, role = Role.Button) {
      val on = !shown; shown = on; onToggle(); haptics.tick()
      scope.launch { pop.snapTo(.78f); pop.animateTo(1f, Motion.spring(Motion.SpringFirm, Motion.PopBounce)) }
      if (on && !reduce) scope.launch { burst.snapTo(0f); burst.animateTo(1f, Motion.enter(Motion.Long)) }
    }, contentAlignment = Alignment.Center) {
    Canvas(Modifier.matchParentSize()) {
      val t = burst.value; if (t >= 1f) return@Canvas
      val r0 = this.size.minDimension * .28f; val span = this.size.minDimension * .5f
      val d = LikeBurst.dist(t); val al = LikeBurst.alpha(t)
      for (i in 0 until LikeBurst.Count) {
        val rr = r0 + span * d * LikeBurst.reach[i]
        val x = center.x + kotlin.math.cos(LikeBurst.angle[i]) * rr; val y = center.y + kotlin.math.sin(LikeBurst.angle[i]) * rr
        drawCircle(if (i % 2 == 0) accent.copy(alpha = al) else tint.copy(alpha = al * .8f), radius = (2.6f - 1.4f * d).dp.toPx(), center = Offset(x, y))
      }
    }
    Icon(if (shown) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, if (shown) "Remover das curtidas" else "Curtir",
      tint = if (shown) accent else tint.copy(alpha = .85f), modifier = Modifier.size(size * .58f).graphicsLayer { val s = pop.value; scaleX = s; scaleY = s })
  }
}

// ---------------------------------------------------------------- Play/pause morph

/** Morph points: pause = two bars, play = triangle split in two halves. p=0 pause, p=1 play. Pure for tests. */
object PlayMorph {
  // 2 quads × 4 points × (x,y) in a unit box.
  private val pause = floatArrayOf(.22f, .15f, .42f, .15f, .42f, .85f, .22f, .85f,   .58f, .15f, .78f, .15f, .78f, .85f, .58f, .85f)
  private val play = floatArrayOf(.26f, .12f, .555f, .31f, .555f, .69f, .26f, .88f,   .555f, .31f, .85f, .5f, .85f, .5f, .555f, .69f)
  fun point(p: Float, i: Int) = pause[i] + (play[i] - pause[i]) * p.coerceIn(0f, 1f)
}

@Composable fun PlayPauseMorph(playing: Boolean, color: Color, modifier: Modifier) {
  val reduce = rememberReduceMotion()
  val p by animateFloatAsState(if (playing) 0f else 1f, if (reduce) androidx.compose.animation.core.snap() else Motion.spring(Motion.SpringMedium), label = "pp")
  val path = remember { Path() }
  Canvas(modifier) {
    val w = size.minDimension; val ox = (size.width - w) / 2; val oy = (size.height - w) / 2
    path.reset()
    for (q in 0 until 2) { val b = q * 8
      path.moveTo(ox + PlayMorph.point(p, b) * w, oy + PlayMorph.point(p, b + 1) * w)
      for (k in 1 until 4) path.lineTo(ox + PlayMorph.point(p, b + 2 * k) * w, oy + PlayMorph.point(p, b + 2 * k + 1) * w)
      path.close() }
    drawPath(path, color)
  }
}

// ---------------------------------------------------------------- Odometer time

/** Tempo que rola como odômetro: só os dígitos que mudam deslizam (para cima ao avançar). */
@Composable fun RollingText(text: String, color: Color, fontSize: TextUnit, modifier: Modifier = Modifier, forward: Boolean = true) {
  val reduce = rememberReduceMotion()
  if (reduce) { Text(text, modifier, color = color, fontSize = fontSize); return }
  Row(modifier) {
    // Pad from the right so seconds keep their slot when minutes gain a digit.
    text.forEachIndexed { i, ch -> key(text.length - i) {
      if (!ch.isDigit()) Text(ch.toString(), color = color, fontSize = fontSize)
      else AnimatedContent(ch, Modifier.clipToBounds(), transitionSpec = {
        val d = if (forward) 1 else -1
        (slideInVertically(Motion.spring(Motion.SpringMedium, threshold = androidx.compose.ui.unit.IntOffset.VisibilityThreshold)) { it * d } + fadeIn(Motion.enter(Motion.Short))) togetherWith
          (slideOutVertically(Motion.spring(Motion.SpringMedium, threshold = androidx.compose.ui.unit.IntOffset.VisibilityThreshold)) { -it * d } + fadeOut(Motion.exit()))
      }, label = "odo") { c -> Text(c.toString(), color = color, fontSize = fontSize) }
    } }
  }
}

// ---------------------------------------------------------------- Gyroscope parallax

/** Tilt of the phone, smoothed, each axis in -1..1. Read only in graphicsLayer/draw. */
class Tilt { val x = Animatable(0f); val y = Animatable(0f) }
val LocalTilt = staticCompositionLocalOf<Tilt?> { null }

object TiltMath {
  /** Max layer rotation, degrees. */
  const val MaxDeg = 2.2f
  /** Gravity vector → tilt relative to the resting pose (base), clamped. ~4.5 m/s² away from the base = full tilt. */
  fun axis(g: Float, base: Float) = ((g - base) / 4.5f).coerceIn(-1f, 1f)
}

/**
 * Listens to TYPE_GRAVITY at ~16 Hz only while the app is resumed, not in battery saver and motion isn't reduced;
 * the 120 Hz smoothness comes from the spring between samples, not from the sensor rate.
 */
@Composable fun rememberTilt(active: Boolean): Tilt? {
  val ctx = LocalContext.current
  val reduce = rememberReduceMotion()
  if (StaticPreview.on || reduce || !active) return null
  val saver = remember { (ctx.getSystemService(Context.POWER_SERVICE) as? PowerManager)?.isPowerSaveMode == true }
  if (saver) return null
  val sm = remember { ctx.getSystemService(Context.SENSOR_SERVICE) as? SensorManager }
  val sensor = remember { sm?.getDefaultSensor(Sensor.TYPE_GRAVITY) ?: sm?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) } ?: return null
  val tilt = remember { Tilt() }
  val scope = rememberCoroutineScope()
  val owner = androidx.lifecycle.compose.LocalLifecycleOwner.current
  DisposableEffect(owner, sensor) {
    var bx = Float.NaN; var by = Float.NaN
    val l = object : SensorEventListener {
      override fun onSensorChanged(e: SensorEvent) {
        val gx = e.values[0]; val gy = e.values[1]
        if (bx.isNaN()) { bx = gx; by = gy }
        // A base segue a pose devagar: só inclinações recentes contam (parallax, não nível de bolha).
        bx += (gx - bx) * .02f; by += (gy - by) * .02f
        val tx = TiltMath.axis(-gx, -bx); val ty = TiltMath.axis(gy, by)
        scope.launch { tilt.x.animateTo(tx, Motion.spring(Motion.SpringSoft, 1f)) }
        scope.launch { tilt.y.animateTo(ty, Motion.spring(Motion.SpringSoft, 1f)) }
      }
      override fun onAccuracyChanged(s: Sensor?, a: Int) {}
    }
    var on = false
    fun start() { if (!on) { on = sm?.registerListener(l, sensor, 60_000) == true } }
    fun stop() { if (on) { sm?.unregisterListener(l); on = false; bx = Float.NaN; scope.launch { tilt.x.animateTo(0f, Motion.spring()) }; scope.launch { tilt.y.animateTo(0f, Motion.spring()) } } }
    val obs = LifecycleEventObserver { _, ev -> if (ev == Lifecycle.Event.ON_RESUME) start() else if (ev == Lifecycle.Event.ON_PAUSE) stop() }
    owner.lifecycle.addObserver(obs)
    if (owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) start()
    onDispose { owner.lifecycle.removeObserver(obs); stop() }
  }
  return tilt
}

/** Glass tilt: tiny 3D rotation, read in the layer only (no recomposition). */
fun Modifier.glassTilt(t: Tilt?): Modifier = if (t == null) this else graphicsLayer {
  rotationY = t.x.value * TiltMath.MaxDeg; rotationX = -t.y.value * TiltMath.MaxDeg; cameraDistance = 24f * density }

// ---------------------------------------------------------------- Cover → player flight

/**
 * Origin of the cover the user tapped (window coordinates). The player flies its cover from here when opening
 * and back here when closed by button/back; a drag (gesture to the mini) or a track change forgets it.
 */
object CoverFlight {
  var origin by mutableStateOf<Rect?>(null); var key by mutableStateOf(""); var art by mutableStateOf("")
  var request by mutableLongStateOf(0L); var handled = 0L
  fun launch(bounds: Rect?, k: String, artUrl: String) {
    if (bounds == null || bounds.width <= 0f || ReduceMotion.user) return
    origin = bounds; key = k; art = artUrl; request = System.nanoTime() }
  fun forget() { origin = null; key = "" }
}

/** Remembers this cover's window bounds without recomposing (plain holder updated on placement). */
class BoundsHolder { var rect: Rect? = null }
fun Modifier.trackBounds(h: BoundsHolder) = this.then(Modifier.onGloballyPositionedCompat(h))
private fun Modifier.onGloballyPositionedCompat(h: BoundsHolder) =
  onGloballyPositioned { h.rect = it.boundsInWindowSafe() }
private fun androidx.compose.ui.layout.LayoutCoordinates.boundsInWindowSafe(): Rect? =
  if (!isAttached) null else this.boundsInWindow()

/** A collection header can register its cover so numbered rows (no cover of their own) fly from it. */
val LocalCoverAnchor = staticCompositionLocalOf<BoundsHolder?> { null }
