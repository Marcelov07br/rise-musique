package com.rise.musique.ui
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.animation.animateColorAsState
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

/** Liga nos testes de print pra mostrar o estado final das entradas. */
object StaticPreview { @JvmField var on = false }

/** Entrada em cascata: cada item chega com atraso proporcional à posição e assenta com quique. */
@Composable fun Modifier.cascade(index: Int): Modifier {
  if (StaticPreview.on || androidx.compose.ui.platform.LocalInspectionMode.current) return this
  val a = remember { Animatable(0f) }
  LaunchedEffect(Unit) { kotlinx.coroutines.delay((index.coerceAtMost(12) * 45L)); a.animateTo(1f, Motion.enter(Motion.Long)) }
  return graphicsLayer { val v = a.value; alpha = v.coerceIn(0f, 1f); translationY = (1f - v) * 60f; scaleX = 0.94f + 0.06f * v; scaleY = scaleX }
}

/** Afunda ao tocar e volta com mola. */
@Composable fun Modifier.springPress(src: MutableInteractionSource): Modifier {
  val pressed by src.collectIsPressedAsState()
  val s = animateFloatAsState(if (pressed) 0.94f else 1f, Motion.press(), label = "press")
  return graphicsLayer { scaleX = s.value; scaleY = s.value } // leitura adiada: só redesenha
}

@Composable fun PillButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier,
  icon: ImageVector? = null, filled: Boolean = true, enabled: Boolean = true) {
  val src = remember { MutableInteractionSource() }
  val c = MaterialTheme.colorScheme
  val bg = if (filled) c.primary else c.surfaceVariant
  val fg = if (filled) c.onPrimary else c.onSurface
  Row(modifier.springPress(src).alpha(if (enabled) 1f else .4f).clip(CircleShape).background(bg)
      .clickable(src, null, enabled = enabled, onClick = onClick).padding(horizontal = 22.dp, vertical = 14.dp),
    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
    if (icon != null) { Icon(icon, null, tint = fg, modifier = Modifier.size(20.dp)); Spacer(Modifier.width(10.dp)) }
    Text(text, color = fg, style = MaterialTheme.typography.labelLarge)
  }
}

@Composable fun <T> PillSelector(options: List<T>, selected: T, label: (T) -> String,
  onSelect: (T) -> Unit, modifier: Modifier = Modifier) {
  val c = MaterialTheme.colorScheme
  val haptics = rememberHaptics()
  val idx = options.indexOf(selected).coerceAtLeast(0).toFloat()
  // As duas bordas do destaque andam com molas diferentes: a da frente chega rápido, a de trás atrasa,
  // então a pílula estica no caminho e assenta com quique.
  val goingRight = remember { mutableStateOf(true) }
  val last = remember { mutableFloatStateOf(idx) }
  if (idx != last.floatValue) { goingRight.value = idx > last.floatValue; last.floatValue = idx }
  val fast = Motion.settle<Float>(455f); val slow = Motion.settle<Float>(169f)
  val left by animateFloatAsState(idx, if (goingRight.value) slow else fast, label = "l")
  val right by animateFloatAsState(idx + 1, if (goingRight.value) fast else slow, label = "r")
  val n = options.size.coerceAtLeast(1)
  Box(modifier.clip(CircleShape).background(c.surfaceVariant).padding(4.dp)) {
    Box(Modifier.matchParentSize().drawBehind {
      val w = size.width / n
      val l = (left.coerceIn(-0.2f, n.toFloat()) * w); val r = (right.coerceIn(0f, n + 0.2f) * w)
      if (r > l) drawRoundRect(c.primary, androidx.compose.ui.geometry.Offset(l, 0f), androidx.compose.ui.geometry.Size(r - l, size.height),
        androidx.compose.ui.geometry.CornerRadius(size.height / 2))
    })
    Row {
      options.forEachIndexed { i, o ->
        val src = remember { MutableInteractionSource() }
        val on = o == selected
        val fg by animateColorAsState(if (on) c.onPrimary else c.onSurfaceVariant, label = "fg")
        Box(Modifier.weight(1f).springPress(src).clip(CircleShape)
            .clickable(src, null) { if (o != selected) haptics.tick(); onSelect(o) }.padding(vertical = 10.dp), contentAlignment = Alignment.Center) {
          Text(label(o), color = fg, style = MaterialTheme.typography.labelLarge)
        }
      }
    }
  }
}

@Composable fun RoundIconButton(icon: ImageVector, desc: String, onClick: () -> Unit, filled: Boolean = false, container: androidx.compose.ui.graphics.Color? = null) {
  val src = remember { MutableInteractionSource() }
  val c = MaterialTheme.colorScheme
  val haptics = rememberHaptics()
  val edge = androidx.compose.ui.graphics.Brush.verticalGradient(listOf(c.onSurface.copy(alpha = .22f), c.onSurface.copy(alpha = .03f)))
  Box(Modifier.size(48.dp).springPress(src).clip(CircleShape).background(if (filled) c.primary else (container ?: c.surfaceVariant).copy(alpha = .72f))
      .then(if (filled) Modifier else Modifier.border(1.dp, edge, CircleShape))
      .clickable(src, null) { haptics.tick(); onClick() }, contentAlignment = Alignment.Center) {
    Icon(icon, desc, tint = if (filled) c.onPrimary else c.onSurface, modifier = Modifier.size(20.dp))
  }
}
