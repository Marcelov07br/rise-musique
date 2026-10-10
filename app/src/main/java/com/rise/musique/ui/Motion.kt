package com.rise.musique.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/**
 * Tokens de movimento do app inteiro. Curvas "emphasized" (entra desacelerando, sai acelerando),
 * durações padrão e molas: sem quique para layout, quique baixo só para toques pequenos.
 */
object Motion {
  val EmphasizedDecelerate = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
  val EmphasizedAccelerate = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)
  val Standard = CubicBezierEasing(0.2f, 0f, 0f, 1f)
  // 0.14: ~35% mais longas a pedido do Marce (150→200, 300→400, 400→540, 650→850).
  const val Short = 200; const val Medium = 400; const val Long = 540; const val ExtraLong = 850; const val PressDown = 130
  fun <T> enter(ms: Int = Medium) = tween<T>(ms, easing = EmphasizedDecelerate)
  fun <T> exit(ms: Int = Short) = tween<T>(ms, easing = EmphasizedAccelerate)
  fun <T> standard(ms: Int = Medium) = tween<T>(ms, easing = Standard)
  /** Layout / settle after a gesture: critically damped, carries velocity, never overshoots. */
  fun <T> settle(stiffness: Float = 260f) = spring<T>(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = stiffness)
  /** Small presses only: stiff with a hint of bounce. */
  fun <T> press() = spring<T>(dampingRatio = 0.8f, stiffness = Spring.StiffnessHigh)
  /** Fling threshold for drag-to-dismiss, px/s. */
  const val FlingVelocity = 1200f
  val pageEnter: EnterTransition = fadeIn(enter(Medium))
  val pageExit: ExitTransition = fadeOut(exit(Short))
}

/** Toque com peso: afunda para 95% e escurece de leve; volta com mola firme. Lido só na fase de desenho. */
@Composable fun Modifier.weightedPress(src: InteractionSource, corner: Dp = 0.dp): Modifier {
  val p = remember { Animatable(0f) }
  LaunchedEffect(src) {
    src.interactions.collect { i -> val t = if (i is PressInteraction.Press) 1f else 0f
      launch { p.animateTo(t, if (t > 0f) Motion.enter(Motion.PressDown) else Motion.press()) } }
  }
  return graphicsLayer { val v = p.value.coerceIn(0f, 1f); scaleX = 1f - .05f * v; scaleY = scaleX }
    .drawWithContent { drawContent(); val v = p.value.coerceIn(0f, 1f)
      if (v > 0f) drawRoundRect(Color.Black.copy(alpha = .12f * v), cornerRadius = androidx.compose.ui.geometry.CornerRadius(corner.toPx())) }
}

@OptIn(ExperimentalSharedTransitionApi::class)
val LocalShared = staticCompositionLocalOf<SharedTransitionScope?> { null }
val LocalAnimScope = compositionLocalOf<AnimatedVisibilityScope?> { null }

/** Shared element: the cover flies between its grid slot and the collection header (no-op in previews). */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable fun Modifier.sharedCover(key: String): Modifier {
  val s = LocalShared.current ?: return this; val v = LocalAnimScope.current ?: return this
  return with(s) { this@sharedCover.sharedElement(rememberSharedContentState(key), v, boundsTransform = { _, _ -> Motion.enter(Motion.Long) }) }
}

/** Corner radius that morphs while this screen enters/leaves (radius [from] outside → [to] when settled). */
@Composable fun morphCorner(from: Dp, to: Dp): State<Dp>? {
  val v = LocalAnimScope.current ?: return null
  return v.transition.animateDp(transitionSpec = { Motion.enter(Motion.Long) }, label = "corner") { if (it == EnterExitState.Visible) to else from }
}

/** Title/controls: fade + slight rise on enter, quick fade on exit. */
@Composable fun Modifier.enterRise(): Modifier {
  val v = LocalAnimScope.current ?: return this
  return with(v) { this@enterRise.animateEnterExit(enter = fadeIn(Motion.enter(Motion.Long)) + slideInVertically(Motion.enter(Motion.Long)) { it / 8 },
    exit = fadeOut(Motion.exit())) }
}
