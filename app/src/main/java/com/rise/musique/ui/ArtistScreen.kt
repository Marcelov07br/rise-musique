package com.rise.musique.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import androidx.compose.animation.animateContentSize
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.layout
import kotlinx.coroutines.launch

/** Local tracks credited to [name] (any list view / filter ignored). */
fun artistTracks(s: UiState, name: String) =
  s.history.filter { it.uri !in s.hidden && it.artist.equals(name, ignoreCase = true) }

/** Hero image: the largest-looking cover among their tracks, else the first YouTube result's thumbnail. */
fun artistArt(s: UiState, name: String): String = s.artistPhotos[name]?.takeIf { it.isNotBlank() } ?:
  artistTracks(s, name).firstOrNull { it.art.isNotBlank() }?.art ?: s.popular[name]?.firstOrNull { it.thumb.isNotBlank() }?.thumb.orEmpty()

/** Card adapter: remote results drawn with the same Cover/CoverCard as local items. */
internal fun SearchResult.asItem() = HistoryItem(title, subtitle, url, thumb, channel, duration.toLong() * 1000, album)

/** Hero height: square-ish, never more than ~62% of the screen (Apple Music-like). */
@Composable internal fun artistHeroHeight(): androidx.compose.ui.unit.Dp {
  val cfg = androidx.compose.ui.platform.LocalConfiguration.current
  return minOf(cfg.screenWidthDp.dp * 1.08f, cfg.screenHeightDp.dp * .62f)
}

/**
 * 0.20 artist profile: full-bleed photo under the status bar (Ken Burns drift, parallax while scrolling and a stretch on
 * overscroll), big name over a gradient, Play/Shuffle/Follow, then the YouTube Music sections (top songs, albums, singles,
 * videos), the local library, similar artists and the bio. Falls back to the previous local/YouTube-search page offline.
 */
@Composable fun ArtistScreen(s: UiState, a: Actions, name: String, list: LazyListState, modifier: Modifier = Modifier, onBack: () -> Unit = {},
                             dismiss: androidx.compose.animation.core.Animatable<Float, androidx.compose.animation.core.AnimationVector1D> = remember { androidx.compose.animation.core.Animatable(0f) },
                             src: String = "") {
  val c = MaterialTheme.colorScheme
  val density = androidx.compose.ui.platform.LocalDensity.current
  val tracks = artistTracks(s, name)
  val localAlbums = tracks.filter { !it.video && it.album.isNotBlank() }.groupBy { it.album }.entries.toList()
  val page = s.artistPages[name]
  val inspection = androidx.compose.ui.platform.LocalInspectionMode.current
  val loadingPage = name in s.artistLoading || (page == null && name !in s.artistFailed && !inspection)
  LaunchedEffect(name) { a.artistPage(name) }
  // Sem página do YouTube Music (offline/erro): a lista "Populares" antiga continua como plano B.
  LaunchedEffect(name, s.artistFailed) { if (name in s.artistFailed) a.artistPopular(name) }
  val popular = page?.top?.takeIf { it.isNotEmpty() } ?: s.popular[name].orEmpty()
  val art = page?.image?.takeIf { it.isNotBlank() } ?: artistArt(s, name)
  val haptics = rememberHaptics()
  val back = { haptics.tick(); onBack() }
  val heroH = artistHeroHeight()
  val insetTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
  val heroPx = with(density) { (heroH + insetTop).toPx() }
  // Colapso próprio: 0 com a foto inteira, 1 quando ela passou por baixo da barra.
  val collapse by remember(list) { derivedStateOf {
    if (list.firstVisibleItemIndex > 0) 1f else (list.firstVisibleItemScrollOffset / (heroPx - with(density) { (TopBarHeight + insetTop).toPx() }).coerceAtLeast(1f)).coerceIn(0f, 1f) } }
  // Puxar além do topo estica a foto (escala) e volta com mola.
  val pull = remember { androidx.compose.animation.core.Animatable(0f) }
  val scope = rememberCoroutineScope()
  val stretch = remember(list) { object : androidx.compose.ui.input.nestedscroll.NestedScrollConnection {
    override fun onPreScroll(available: androidx.compose.ui.geometry.Offset, source: androidx.compose.ui.input.nestedscroll.NestedScrollSource): androidx.compose.ui.geometry.Offset {
      if (pull.value > 0f && available.y < 0f) { val d = maxOf(available.y, -pull.value); scope.launch { pull.snapTo(pull.value + d) }; return androidx.compose.ui.geometry.Offset(0f, d) }
      return androidx.compose.ui.geometry.Offset.Zero }
    override fun onPostScroll(consumed: androidx.compose.ui.geometry.Offset, available: androidx.compose.ui.geometry.Offset, source: androidx.compose.ui.input.nestedscroll.NestedScrollSource): androidx.compose.ui.geometry.Offset {
      if (available.y > 0f && source == androidx.compose.ui.input.nestedscroll.NestedScrollSource.UserInput && dismiss.value == 0f && pull.value < heroPx * .5f) {
        val d = available.y * .5f; scope.launch { pull.snapTo((pull.value + d).coerceAtMost(heroPx * .5f)) }; return androidx.compose.ui.geometry.Offset(0f, available.y * .3f) }
      return androidx.compose.ui.geometry.Offset.Zero }
    override suspend fun onPreFling(available: androidx.compose.ui.unit.Velocity): androidx.compose.ui.unit.Velocity {
      if (pull.value > 0f) { pull.animateTo(0f, Motion.settle()); return available }; return androidx.compose.ui.unit.Velocity.Zero }
  } }
  val albumRow = remember(name) { LazyListState() }; val singleRow = remember(name) { LazyListState() }; val videoRow = remember(name) { LazyListState() }
  val localRow = remember(name) { LazyListState() }; val similarRow = remember(name) { LazyListState() }
  val playAll = { haptics.press(); if (page?.top?.isNotEmpty() == true) a.playStreams(page.top, 0, false) else tracks.firstOrNull()?.let(a.play) ?: Unit }
  val shuffleAll = { haptics.press(); if (page?.top?.isNotEmpty() == true) a.playStreams(page.top, 0, true) else tracks.randomOrNull()?.let(a.playShuffled) ?: Unit }
  val canPlay = page?.top?.isNotEmpty() == true || tracks.isNotEmpty()
  val bg = c.background

  // A tela inteira sobe por baixo da status bar (foto de ponta a ponta); a barra de cima recebe o recuo de volta.
  Box(modifier.dragToDismiss(dismiss, onBack).bleedTop(insetTop).nestedScroll(stretch)) {
    // Foto: camada fixa atrás da lista (parallax 0.5x ao rolar, escala no esticão).
    Box(Modifier.fillMaxWidth().height(heroH + insetTop).graphicsLayer {
        val first = list.firstVisibleItemIndex == 0
        val off = if (first) list.firstVisibleItemScrollOffset.toFloat() else heroPx
        translationY = -off * .5f
        val k = 1f + pull.value / heroPx; scaleX = k; scaleY = k; transformOrigin = androidx.compose.ui.graphics.TransformOrigin(.5f, 0f)
        alpha = (1f - collapse * 1.1f).coerceIn(0f, 1f) }
      .sharedCover("cover-ar-${if (src.isNotBlank()) "$src-" else ""}$name").clip(RectangleShape).background(Color(0xFF202024))) {
      if (art.isNotBlank()) AsyncImage(art, null, Modifier.fillMaxSize().kenBurns(true), contentScale = ContentScale.Crop)
      else Icon(Icons.Rounded.Person, null, tint = Color.White.copy(.5f), modifier = Modifier.size(96.dp).align(Alignment.Center))
      Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to Color.Black.copy(.35f), .18f to Color.Transparent, .55f to Color.Transparent, .82f to bg.copy(alpha = .75f), 1f to bg)))
    }
    LazyColumn(Modifier.fillMaxSize(), state = list, contentPadding = PaddingValues(bottom = bottomSpace(s))) {
      item(key = "hero") {
        Box(Modifier.fillMaxWidth().height(heroH + insetTop).graphicsLayer { translationY = pull.value }) {
          Column(Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 8.dp).enterRise()) {
            if (page?.listeners?.isNotBlank() == true) Text(page.listeners.uppercase(), color = Color.White.copy(.75f), fontSize = 12.sp, letterSpacing = 1.sp, maxLines = 1)
            Text(name, color = Color.White, fontSize = 40.sp, lineHeight = 44.sp, fontWeight = FontWeight.Black, letterSpacing = (-1).sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
              Row(Modifier.height(48.dp).clip(CircleShape).background(Color.White).clickable(enabled = canPlay, onClick = playAll).padding(horizontal = 22.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.PlayArrow, null, tint = Color.Black); Spacer(Modifier.width(6.dp))
                Text("Tocar", color = Color.Black, fontWeight = FontWeight.SemiBold, fontSize = 16.sp) }
              GlassIcon(Icons.Rounded.Shuffle, "Aleatório", shuffleAll, enabled = canPlay)
              GlassIcon(Icons.Rounded.Radio, "Rádio do artista", { haptics.tick(); tracks.firstOrNull { !it.video }?.let(a.radioFor) ?: page?.top?.firstOrNull()?.let(a.radio) ?: Unit },
                enabled = tracks.any { !it.video } || page?.top?.isNotEmpty() == true)
              Spacer(Modifier.weight(1f))
              val following = name in s.followed
              GlassBox(Modifier.height(48.dp), CircleShape) {
                Row(Modifier.clickable { haptics.press(); a.follow(name) }.padding(horizontal = 16.dp).fillMaxHeight(), verticalAlignment = Alignment.CenterVertically) {
                  Icon(if (following) Icons.Rounded.NotificationsActive else Icons.Rounded.NotificationAdd, null, tint = Color.White, modifier = Modifier.size(18.dp))
                  Spacer(Modifier.width(6.dp))
                  Text(if (following) "Seguindo" else "Seguir", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp) }
              }
            }
          }
        }
      }
      // Top músicas (YouTube Music) — ou, sem conexão, as mais tocadas da busca.
      item(key = "top-title") { Section("Top músicas") }
      when {
        popular.isEmpty() && (loadingPage || name in s.popularLoading) -> item(key = "top-loading") { SkeletonRows("Buscando o artista no YouTube Music…") }
        popular.isEmpty() -> item(key = "top-empty") { EmptyState(Icons.Rounded.CloudOff, "Nada do YouTube agora", "Confira a conexão e abra o artista de novo.") }
        else -> itemsIndexed(popular.take(10), key = { _, r -> "p:" + r.url }) { i, r ->
          var menu by remember { mutableStateOf(false) }
          PopularRow(i + 1, r, s.now?.key == "stream:" + r.url, false, { haptics.tick(); a.playStreams(popular, i, false) }, { a.downloadItem(r) }) { haptics.press(); menu = true }
          if (menu) StreamActionSheet(r, a, s) { menu = false } }
      }
      if (page != null && page.albums.isNotEmpty()) {
        item(key = "albums-title") { Section("Álbuns") }
        item(key = "albums") { Box(Modifier.padding(horizontal = 24.dp)) { Carousel(albumRow) {
          items(page.albums, key = { "a:" + it.url }) { r -> CoverCard(r.asItem(), r.title, r.subtitle, "cover-ra-${r.url.substringAfterLast('/')}") { a.openRemoteAlbum(r, "") } } } } }
      }
      if (page != null && page.singles.isNotEmpty()) {
        item(key = "singles-title") { Section("Singles & EPs") }
        item(key = "singles") { Box(Modifier.padding(horizontal = 24.dp)) { Carousel(singleRow) {
          items(page.singles, key = { "s:" + it.url }) { r -> CoverCard(r.asItem(), r.title, r.subtitle, "cover-ra-${r.url.substringAfterLast('/')}") { a.openRemoteAlbum(r, "") } } } } }
      }
      if (page != null && page.videos.isNotEmpty()) {
        item(key = "videos-title") { Section("Vídeos") }
        item(key = "videos") { Box(Modifier.padding(horizontal = 24.dp)) { Carousel(videoRow) {
          items(page.videos, key = { "v:" + it.url }) { r -> VideoCard(r) { haptics.tick(); a.playVideo(r) } } } } }
      }
      if (tracks.isNotEmpty()) {
        item(key = "local-title") { Section("Na sua biblioteca") }
        if (localAlbums.isNotEmpty()) item(key = "local-albums") { Box(Modifier.padding(horizontal = 24.dp, vertical = 4.dp)) { Carousel(localRow) {
          items(localAlbums, key = { it.key }) { e -> val h = e.value.first()
            CoverCard(h, e.key, "${e.value.size} ${if (e.value.size == 1) "faixa" else "faixas"}", "cover-al-${e.key}") { a.openAlbum(e.key) } } } } }
        itemsIndexed(tracks.take(10), key = { _, h -> "t:" + h.uri }) { _, h -> Box(Modifier.padding(horizontal = 16.dp)) { TrackRow(h, s, a) } }
      }
      if (page != null && page.similar.isNotEmpty()) {
        item(key = "similar-title") { Section("Artistas parecidos") }
        item(key = "similar") { Box(Modifier.padding(horizontal = 24.dp)) { Carousel(similarRow) {
          items(page.similar, key = { "r:" + it.url }) { r -> ArtistBubble(r) { haptics.tick(); a.openArtistFrom(r.title, "similar") } } } } }
      }
      if (page != null && page.about.isNotBlank()) {
        item(key = "about-title") { Section("Sobre") }
        item(key = "about") { var open by remember { mutableStateOf(false) }
          Text(page.about, Modifier.fillMaxWidth().padding(horizontal = 24.dp).clip(RoundedCornerShape(12.dp)).clickable { open = !open }.animateContentSize(Motion.standard()),
            color = c.onSurfaceVariant, fontSize = 15.sp, lineHeight = 22.sp, maxLines = if (open) Int.MAX_VALUE else 6, overflow = TextOverflow.Ellipsis) }
      }
    }
    // Fundo sólido da barra (inclui a status bar) quando a foto já passou por baixo dela.
    Box(Modifier.fillMaxWidth().height(insetTop + TopBarHeight).graphicsLayer { alpha = ((collapse - .6f) / .4f).coerceIn(0f, 1f) }.background(bg))
    Box(Modifier.padding(top = insetTop)) { DetailTopBar(name, collapse, back, if (canPlay) playAll else null) }
  }
}

/** Lets a detail screen draw under the status bar: measured taller by [top] and moved up by it. */
internal fun Modifier.bleedTop(top: androidx.compose.ui.unit.Dp) = layout { m, cs ->
  val t = top.roundToPx()
  val p = m.measure(cs.copy(minHeight = (cs.minHeight + t).coerceAtLeast(0), maxHeight = if (cs.hasBoundedHeight) cs.maxHeight + t else cs.maxHeight))
  layout(p.width, (p.height - t).coerceAtLeast(0)) { p.place(0, -t) } }

@Composable private fun VideoCard(r: SearchResult, onClick: () -> Unit) {
  val src = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
  Column(Modifier.width(248.dp).weightedPress(src, 16.dp).clip(RoundedCornerShape(16.dp)).clickable(src, null, onClick = onClick)) {
    Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surfaceVariant)) {
      AsyncImage(r.thumb, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
      Icon(Icons.Rounded.PlayCircle, null, Modifier.align(Alignment.Center).size(40.dp), tint = Color.White.copy(.9f)) }
    Spacer(Modifier.height(8.dp))
    Text(r.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
    Text(r.subtitle.substringAfter(" • ", r.subtitle), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
  }
}

@Composable private fun ArtistBubble(r: SearchResult, onClick: () -> Unit) {
  val src = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
  Column(Modifier.width(112.dp).weightedPress(src, 56.dp).clip(RoundedCornerShape(16.dp)).clickable(src, null, onClick = onClick), horizontalAlignment = Alignment.CenterHorizontally) {
    Box(Modifier.size(112.dp).sharedCover("cover-ar-similar-${r.title}").clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant)) {
      AsyncImage(r.thumb, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
    Spacer(Modifier.height(8.dp))
    Text(r.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
  }
}

@Composable private fun Section(t: String) =
  Text(t, Modifier.padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 8.dp), style = MaterialTheme.typography.titleLarge)

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable private fun PopularRow(n: Int, r: SearchResult, playing: Boolean, loading: Boolean, preview: () -> Unit, download: () -> Unit, more: () -> Unit) {
  val c = MaterialTheme.colorScheme
  val src = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
  Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp).weightedPress(src, 16.dp).clip(RoundedCornerShape(16.dp)).combinedClickable(src, null, onLongClick = more, onClick = preview)
      .padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
    Text("$n", Modifier.width(36.dp), color = c.onSurfaceVariant, fontSize = 15.sp, textAlign = TextAlign.Center)
    AsyncImage(r.thumb, null, Modifier.size(56.dp).clip(RoundedCornerShape(8.dp)).background(c.surfaceVariant), contentScale = ContentScale.Crop)
    Spacer(Modifier.width(12.dp))
    Column(Modifier.weight(1f)) {
      Text(r.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
      Text(listOf(r.channel, if (r.duration > 0) "%d:%02d".format(r.duration / 60, r.duration % 60) else "").filter(String::isNotBlank).joinToString(" · "),
        color = c.onSurfaceVariant, fontSize = 13.sp, maxLines = 1)
    }
    IconButton(preview) { if (loading) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = c.onSurface)
      else Icon(if (playing) Icons.Rounded.Stop else Icons.Rounded.PlayArrow, if (playing) "Parar" else "Tocar") }
    IconButton(download) { Icon(Icons.Rounded.Download, "Baixar") }
  }
}

/** Guided empty state used by every list. */
@Composable fun EmptyState(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, body: String, action: String? = null, onAction: () -> Unit = {}) {
  val c = MaterialTheme.colorScheme
  Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
    Icon(icon, null, Modifier.size(40.dp), tint = c.onSurfaceVariant)
    Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
    Text(body, color = c.onSurfaceVariant, fontSize = 14.sp, textAlign = TextAlign.Center)
    if (action != null) PillButton(action, onAction, filled = false)
  }
}
