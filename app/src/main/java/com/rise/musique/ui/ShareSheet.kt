package com.rise.musique.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rise.musique.Snippet

/** Pick a 15 s range, preview the 9:16 card, share as image (always) or MP4 with audio (local files only). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable fun ShareSheet(now: NowPlaying, local: Boolean, busy: Boolean, a: Actions, dismiss: () -> Unit) {
  val c = MaterialTheme.colorScheme
  val ctx = LocalContext.current
  var start by remember { mutableLongStateOf(Snippet.clampStart(now.position, now.duration)) }
  var cover by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
  LaunchedEffect(now.art) { if (now.art.isBlank() || StaticPreview.on) return@LaunchedEffect
    cover = runCatching { val r = coil.ImageLoader(ctx).execute(coil.request.ImageRequest.Builder(ctx).data(if (now.art.startsWith("/")) java.io.File(now.art) else now.art)
      .size(480).allowHardware(false).build()); (r.drawable as? android.graphics.drawable.BitmapDrawable)?.bitmap }.getOrNull() }
  ModalBottomSheet(dismiss) { ShareContent(now, local, busy, a, cover, start) { start = it } }
}

@Composable fun ShareContent(now: NowPlaying, local: Boolean, busy: Boolean, a: Actions, cover: android.graphics.Bitmap?, start: Long, setStart: (Long) -> Unit) {
  val c = MaterialTheme.colorScheme
  val lines = remember(start, now.lyrics) { Snippet.linesIn(now.lyrics, start) }
  val card = remember(lines, cover, now.title) { Snippet.card(now.title, now.artist, cover, lines.map { it.text }, w = 540, h = 960).asImageBitmap() }
  run {
    Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
      Text("Compartilhar trecho", style = MaterialTheme.typography.titleLarge, modifier = Modifier.fillMaxWidth())
      Image(card, "Prévia do cartão", Modifier.height(300.dp).aspectRatio(9f / 16f).clip(RoundedCornerShape(16.dp)))
      val max = (now.duration - Snippet.LENGTH_MS).coerceAtLeast(0)
      if (max > 0) Column(Modifier.fillMaxWidth()) {
        Slider(start.toFloat(), { setStart(it.toLong()) }, valueRange = 0f..max.toFloat())
        Text("${fmtMs(start)} – ${fmtMs(start + Snippet.LENGTH_MS)}", color = c.onSurfaceVariant, fontSize = 13.sp)
      }
      if (!local) Text("Em streaming dá pra compartilhar só a imagem: o áudio do YouTube não pode ser recortado em arquivo.", color = c.onSurfaceVariant, fontSize = 12.sp)
      Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        PillButton("Imagem", { a.shareSnippet(false, start) }, Modifier.weight(1f), Icons.Rounded.Image, filled = !local, enabled = !busy)
        if (local) PillButton(if (busy) "Gerando…" else "Vídeo 15 s", { a.shareSnippet(true, start) }, Modifier.weight(1f), Icons.Rounded.Movie, enabled = !busy)
      }
    }
  }
}

private fun fmtMs(ms: Long) = "%d:%02d".format(ms / 60000, ms / 1000 % 60)
