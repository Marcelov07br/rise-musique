package com.rise.musique.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onSizeChanged
import kotlinx.coroutines.launch
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex

internal val HeaderHeight = 470.dp
internal val TopBarHeight = 64.dp

/** 0 = header fully visible, 1 = scrolled past the header (collapsed top bar). Always clamped. */
@Composable fun collapseOf(list: LazyListState): Float {
  // Header height is measured (it wraps its content), falling back to the nominal height before the first layout.
  val fallback = with(LocalDensity.current) { (HeaderHeight - TopBarHeight).toPx() }
  val c by remember(list) { derivedStateOf {
    val px = list.layoutInfo.visibleItemsInfo.firstOrNull { it.index == 0 }?.size?.toFloat()?.takeIf { it > 0f } ?: fallback
    if (list.firstVisibleItemIndex > 0) 1f else (list.firstVisibleItemScrollOffset / (px * .8f)).coerceIn(0f, 1f) } }
  return c
}

/** Fixed full-screen backdrop: living artwork fading into the base color downwards, and fully as the list scrolls. */
@Composable fun CollectionBackdrop(art: String, collapse: Float, modifier: Modifier) {
  val bg = MaterialTheme.colorScheme.background
  Box(modifier) {
    LivingArtwork(art, Modifier.fillMaxSize(), dim = .25f)
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to Color.Transparent, .30f to Color.Transparent,
      .55f to bg.copy(alpha = .85f), .75f to bg)))
    Box(Modifier.fillMaxSize().background(bg.copy(alpha = (collapse * .9f).coerceIn(0f, 1f))))
  }
}

/** Album or playlist: collapsing hero header, glass actions and a numbered list, over [CollectionBackdrop]. */
@Composable fun CollectionScreen(s: UiState, a: Actions, list: LazyListState, modifier: Modifier = Modifier, onBack: () -> Unit = {},
                                 dismiss: androidx.compose.animation.core.Animatable<Float, androidx.compose.animation.core.AnimationVector1D> = remember { androidx.compose.animation.core.Animatable(0f) },
                                 smart: SmartFolder? = null, src: String = "") {
  val c = MaterialTheme.colorScheme
  val tracks = smart?.tracks ?: visibleHistory(s)
  val isAlbum = smart == null && s.albumFilter.isNotBlank()
  val title = smart?.value ?: if (isAlbum) s.albumFilter else s.historyView
  val playlist = if (smart == null && !isAlbum) s.library.playlist(title) else null
  val reorder = remember(title) { PlaylistReorder(title) }
  var adding by remember { mutableStateOf(false) }
  var editMenu by remember { mutableStateOf(false) }
  var renaming by remember { mutableStateOf(false) }
  var confirmDelete by remember { mutableStateOf(false) }
  val rowPx = with(LocalDensity.current) { 69.dp.toPx() }
  val hero = tracks.firstOrNull { it.art.isNotBlank() }
  val artist = if (isAlbum) tracks.map { it.artist }.filter(String::isNotBlank).distinct().singleOrNull().orEmpty() else ""
  val totalMs = tracks.sumOf { it.duration }
  val meta = listOf(smart?.kind?.uppercase() ?: if (isAlbum) "ÁLBUM" else "PLAYLIST", artist.uppercase(), "${tracks.size} ${if (tracks.size == 1) "FAIXA" else "FAIXAS"}",
    if (totalMs > 0) "${(totalMs / 60_000).coerceAtLeast(1)} MIN" else "").filter(String::isNotBlank).joinToString(" · ")
  val haptics = rememberHaptics()
  val back = { haptics.tick(); onBack() }
  val collapseState = rememberUpdatedState(collapseOf(list)); val collapse = collapseState.value
  val anchor = remember { BoundsHolder() }
  Box(modifier.dragToDismiss(dismiss, onBack)) { CompositionLocalProvider(LocalCoverAnchor provides if (isAlbum) anchor else null) {
    LazyColumn(Modifier.fillMaxSize(), state = list, contentPadding = PaddingValues(top = TopBarHeight, bottom = bottomSpace(s))) {
      item(key = "header") {
        Column(Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, bottom = 24.dp)
          .graphicsLayer { alpha = (1f - collapse * 1.2f).coerceIn(0f, 1f) }, horizontalAlignment = Alignment.CenterHorizontally) {
          val corner = morphCorner(14.dp, 8.dp)?.value ?: 8.dp
          DisposableEffect(Unit) { onDispose { anchor.rect = null } }
          Box(Modifier.size(208.dp).trackBounds(anchor).graphicsLayer { val sc = 1f - .15f * collapseState.value.coerceIn(0f, 1f); scaleX = sc; scaleY = sc }
            .sharedCover(smart?.key ?: if (isAlbum) "cover-al-${if (src.isNotBlank()) "$src-" else ""}$title" else "cover-pl-$title")) {
            if (playlist != null && (playlist.cover.isNotBlank() || playlistArts(s, playlist).size >= 4)) PlaylistArt(s, playlist, Modifier.fillMaxSize(), corner)
            else if (hero != null) { val settledIn by produceState(false) { kotlinx.coroutines.delay(700); value = true } // estática durante a transição
              MotionCover(artist, title, active = isAlbum && settledIn && collapse < .5f, priority = 2, modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(corner))) { on ->
                Cover(hero, Modifier.fillMaxSize().kenBurns(hero.art.isNotBlank() && !on), corner) } }
            else Box(Modifier.fillMaxSize().clip(RoundedCornerShape(16.dp)).background(Color.White.copy(.12f)), contentAlignment = Alignment.Center) {
              Icon(if (isAlbum) Icons.Rounded.Album else if (smart != null) Icons.Rounded.AutoAwesome else Icons.Rounded.QueueMusic, null, tint = Color.White.copy(.75f), modifier = Modifier.size(64.dp)) }
          }
          Spacer(Modifier.height(16.dp))
          Column(Modifier.enterRise(), horizontalAlignment = Alignment.CenterHorizontally) {
          Text(title, color = Color.White, fontSize = 24.sp, lineHeight = 28.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
          Spacer(Modifier.height(4.dp))
          if (artist.isNotBlank()) Text(artist, Modifier.clip(CircleShape).clickable { haptics.tick(); a.openArtist(artist) }.padding(horizontal = 12.dp, vertical = 4.dp),
            color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Medium, maxLines = 1)
          Text(if (artist.isBlank()) meta else meta.replace(" · " + artist.uppercase(), ""), color = Color.White.copy(.7f), fontSize = 12.sp, letterSpacing = 1.sp, textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis)
          Spacer(Modifier.height(16.dp))
          Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            GlassIcon(Icons.Rounded.Shuffle, "Aleatório", { haptics.press(); tracks.randomOrNull()?.let(a.playShuffled) }, enabled = tracks.isNotEmpty())
            GlassBox(Modifier.height(48.dp).width(144.dp), CircleShape) {
              Row(Modifier.matchParentSize().clickable(enabled = tracks.isNotEmpty()) { haptics.press(); tracks.firstOrNull()?.let(a.play) },
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                Icon(Icons.Rounded.PlayArrow, null, tint = Color.White); Spacer(Modifier.width(8.dp))
                Text("Tocar", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 16.sp) }
            }
            GlassIcon(Icons.Rounded.PlaylistAdd, "Adicionar tudo à fila", { haptics.tick(); a.queueAll(tracks) }, enabled = tracks.isNotEmpty())
          }
          if (playlist != null) { Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
              GlassIcon(Icons.Rounded.Add, "Adicionar músicas", { haptics.tick(); adding = true })
              Box { GlassIcon(Icons.Rounded.Edit, "Editar playlist", { haptics.tick(); editMenu = true })
                DropdownMenu(editMenu, { editMenu = false }) {
                  DropdownMenuItem({ Text("Renomear") }, { editMenu = false; renaming = true }, leadingIcon = { Icon(Icons.Rounded.Edit, null) })
                  DropdownMenuItem({ Text("Escolher capa") }, { editMenu = false; a.pickCover(title) }, leadingIcon = { Icon(Icons.Rounded.Image, null) })
                  if (playlist.cover.isNotBlank()) DropdownMenuItem({ Text("Capa automática") }, { editMenu = false; a.resetCover(title) }, leadingIcon = { Icon(Icons.Rounded.GridView, null) })
                  DropdownMenuItem({ Text("Apagar playlist", color = DangerRed) }, { editMenu = false; confirmDelete = true }, leadingIcon = { Icon(Icons.Rounded.DeleteOutline, null, tint = DangerRed) })
                } }
            }
            if (tracks.size > 1) Text("Segure 1 s para arrastar · deslize ← para remover", Modifier.padding(top = 10.dp), color = Color.White.copy(.6f), fontSize = 12.sp)
          }
          if (isAlbum) { val id = "local:$title"; val saved = s.library.albums.any { it.id == id }; Spacer(Modifier.height(12.dp))
            GlassBox(Modifier.height(40.dp), CircleShape) { Row(Modifier.clickable { haptics.tick()
                a.toggleAlbum(com.rise.musique.LibAlbum(id, title, artist, hero?.art.orEmpty())) }.padding(horizontal = 18.dp).fillMaxHeight(), verticalAlignment = Alignment.CenterVertically) {
              Icon(if (saved) Icons.Rounded.Check else Icons.Rounded.Add, null, tint = Color.White, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(6.dp))
              Text(if (saved) "Salvo" else "Salvar álbum", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium) } } }
          }
        }
      }
      if (tracks.isEmpty()) item(key = "empty") {
        Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
          Text(if (isAlbum) "Nenhuma faixa deste álbum" else "Esta playlist está vazia", style = MaterialTheme.typography.titleMedium)
          Text("Toque e segure uma faixa e escolha “Adicionar à playlist”.", color = c.onSurfaceVariant, fontSize = 13.sp, textAlign = TextAlign.Center)
        }
      }
      if (playlist == null) itemsIndexed(tracks, key = { _, h -> h.uri }) { i, h ->
        Box(Modifier.padding(horizontal = 16.dp).enterRise()) { TrackRow(h, s, a, number = i + 1, queue = tracks) }
        if (i < tracks.lastIndex) HorizontalDivider(Modifier.padding(start = 64.dp, end = 16.dp), color = c.outlineVariant)
      }
      else itemsIndexed(reorder.shown(tracks), key = { _, h -> h.uri }) { i, h -> PlaylistTrack(s, a, reorder, title, tracks, h, i, rowPx) }
    }
    DetailTopBar(title, collapse, back) { haptics.press(); tracks.firstOrNull()?.let { a.playList(tracks, it) } }
  } }
  if (adding) AddTracksSheet(s, title, { adding = false }) { a.addAllToPlaylist(title, it) }
  if (renaming) { var name by remember { mutableStateOf(title) }
    AlertDialog({ renaming = false }, shape = RoundedCornerShape(28.dp), title = { Text("Renomear playlist") }, text = { OutlinedTextField(name, { name = it.take(80) }, singleLine = true, shape = CircleShape) },
      confirmButton = { TextButton({ a.renamePlaylist(title, name); renaming = false; if (name.trim() != title) onBack() }, enabled = name.isNotBlank()) { Text("Salvar") } },
      dismissButton = { TextButton({ renaming = false }) { Text("Cancelar") } }) }
  if (confirmDelete) AlertDialog({ confirmDelete = false }, shape = RoundedCornerShape(28.dp), title = { Text("Apagar $title?") }, text = { Text("As músicas continuam na biblioteca.") },
    confirmButton = { TextButton({ confirmDelete = false; onBack(); a.deletePlaylist(title) }) { Text("Apagar playlist", color = DangerRed) } },
    dismissButton = { TextButton({ confirmDelete = false }) { Text("Cancelar") } })
}

/** A playlist row: tap plays, hold 1 s and drag to reorder, swipe ← removes (with Desfazer). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun androidx.compose.foundation.lazy.LazyItemScope.PlaylistTrack(s: UiState, a: Actions, r: PlaylistReorder, name: String, tracks: List<HistoryItem>,
                                                                                     h: HistoryItem, i: Int, rowPx: Float) {
  val haptics = rememberHaptics()
  val elevated = r.elevated(h.uri)
  val lift by androidx.compose.animation.core.animateFloatAsState(if (h.uri == r.dragKey) 1f else 0f, QueueMotion.lift(), label = "lift")
  val dismiss = rememberSwipeToDismissBoxState(confirmValueChange = { v ->
    if (v == SwipeToDismissBoxValue.EndToStart) { haptics.press(); a.removeFromList(h, name); true } else false })
  val latest by rememberUpdatedState(tracks)
  Box(Modifier.zIndex(if (elevated) 1f else 0f).animateItem(placementSpec = if (elevated) null else QueueMotion.place)
      .graphicsLayer { translationY = r.offset(h.uri, i, rowPx) }.padding(horizontal = 16.dp)) {
    SwipeToDismissBox(dismiss, backgroundContent = {
        if (dismiss.dismissDirection == SwipeToDismissBoxValue.EndToStart) Row(Modifier.fillMaxSize().clip(RoundedCornerShape(16.dp)).background(DangerRed).padding(horizontal = 24.dp),
          horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Rounded.DeleteOutline, null, tint = Color.White); Spacer(Modifier.width(8.dp)); Text("Remover", color = Color.White, fontWeight = FontWeight.SemiBold) } },
      enableDismissFromStartToEnd = false, enableDismissFromEndToStart = r.dragKey == null) {
      TrackRow(h, s, a, number = i + 1, queue = tracks, lift = lift, gesture = Modifier.reorderable(r, h, { r.shown(latest) }, rowPx,
        tap = { if (h.video) a.playLocalVideo(h) else a.playList(latest, h) }, drop = { from, to -> a.movePlaylist(name, from, to) }))
    }
  }
}

/** Barra superior das telas de detalhe: voltar sempre visível; título e tocar aparecem quando o cabeçalho sai. */
@Composable internal fun DetailTopBar(title: String, collapse: Float, back: () -> Unit, onPlay: (() -> Unit)?) {
  val c = MaterialTheme.colorScheme
  val barT = ((collapse - .6f) / .4f).coerceIn(0f, 1f)
  Row(Modifier.fillMaxWidth().height(TopBarHeight).background(c.background.copy(alpha = (barT * .92f).coerceIn(0f, 1f))).padding(horizontal = 8.dp),
    verticalAlignment = Alignment.CenterVertically) {
    GlassIcon(Icons.Rounded.ArrowBack, "Voltar", back, light = barT > .5f)
    Text(title, Modifier.weight(1f).padding(horizontal = 12.dp).graphicsLayer { alpha = barT }, style = MaterialTheme.typography.titleMedium,
      color = c.onBackground, maxLines = 1, overflow = TextOverflow.Ellipsis)
    if (barT > 0f && onPlay != null) Box(Modifier.size(48.dp).graphicsLayer { alpha = barT }.clip(CircleShape).clickable(enabled = barT > .5f, onClick = onPlay),
      contentAlignment = Alignment.Center) { Icon(Icons.Rounded.PlayCircle, "Tocar", tint = c.onBackground, modifier = Modifier.size(32.dp)) }
  }
}

@Composable internal fun GlassIcon(icon: ImageVector, desc: String, onClick: () -> Unit, enabled: Boolean = true, light: Boolean = false) {
  val tint = if (light) MaterialTheme.colorScheme.onBackground else Color.White
  GlassBox(Modifier.size(48.dp), CircleShape, dark = !light || MaterialTheme.colorScheme.background.red < .5f) {
    Box(Modifier.matchParentSize().clickable(enabled = enabled, onClick = onClick), contentAlignment = Alignment.Center) {
      Icon(icon, desc, tint = tint.copy(alpha = if (enabled) 1f else .4f)) }
  }
}

/** Arrastar para fechar: puxando para baixo no topo da lista, a tela segue o dedo 1:1 (escala/fade proporcionais)
 * e ao soltar decide pela distância (25%) ou pela velocidade. */
@Composable fun Modifier.dragToDismiss(dismiss: androidx.compose.animation.core.Animatable<Float, androidx.compose.animation.core.AnimationVector1D>, onBack: () -> Unit): Modifier {
  val haptics = rememberHaptics()
  val scope = rememberCoroutineScope()
  var heightPx by remember { mutableFloatStateOf(1f) }
  LaunchedEffect(Unit) { dismiss.snapTo(0f) }
  val drag = remember(dismiss) { object : androidx.compose.ui.input.nestedscroll.NestedScrollConnection {
    override fun onPreScroll(available: androidx.compose.ui.geometry.Offset, source: androidx.compose.ui.input.nestedscroll.NestedScrollSource) =
      if (dismiss.value > 0f && available.y < 0f) { val d = (available.y / heightPx).coerceAtLeast(-dismiss.value)
        scope.launch { dismiss.snapTo((dismiss.value + d).coerceIn(0f, 1f)) }; androidx.compose.ui.geometry.Offset(0f, d * heightPx) }
      else androidx.compose.ui.geometry.Offset.Zero
    override fun onPostScroll(consumed: androidx.compose.ui.geometry.Offset, available: androidx.compose.ui.geometry.Offset, source: androidx.compose.ui.input.nestedscroll.NestedScrollSource) =
      if (available.y > 0f && source == androidx.compose.ui.input.nestedscroll.NestedScrollSource.UserInput) {
        scope.launch { dismiss.snapTo((dismiss.value + available.y / heightPx).coerceIn(0f, 1f)) }; available }
      else androidx.compose.ui.geometry.Offset.Zero
    override suspend fun onPreFling(available: androidx.compose.ui.unit.Velocity): androidx.compose.ui.unit.Velocity {
      if (dismiss.value <= 0f) return androidx.compose.ui.unit.Velocity.Zero
      if (dismiss.value > .25f || available.y > Motion.FlingVelocity) { haptics.tick(); onBack() }
      else dismiss.animateTo(0f, Motion.settle(), initialVelocity = available.y / heightPx)
      return available
    }
  } }
  return this.onSizeChanged { heightPx = it.height.toFloat().coerceAtLeast(1f) }.nestedScroll(drag).graphicsLayer {
    val d = dismiss.value.coerceIn(0f, 1f); translationY = d * heightPx * .5f; val sc = 1f - .1f * d; scaleX = sc; scaleY = sc; alpha = 1f - .4f * d }
}
