package com.rise.musique.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rise.musique.Station
import com.rise.musique.Stations

/** Hero card content: what it is ("Volte a ouvir"…), its art (track cover or generated station cover), action and menu. */
data class HeroItem(val key: String, val label: String, val title: String, val subtitle: String, val track: HistoryItem?, val station: Station?, val album: String = "", val open: () -> Unit) {
  /** Album heroes share their cover with the album header under a hero-only key (rows use "cover-al-<album>"), so both can be on screen. */
  val sharedKey get() = if (album.isNotBlank()) "cover-al-hero-$album" else "hero-$key"
}

/** Up to five heroes: back-to (most played), made-for-you stations, recently added album. */
fun heroItems(s: UiState, shown: List<HistoryItem>, stations: List<Station>, a: Actions): List<HeroItem> {
  val out = mutableListOf<HeroItem>()
  val topName = s.stats?.topTracks?.firstOrNull()?.name
  val back = shown.firstOrNull { it.title == topName } ?: shown.firstOrNull { !it.video }
  back?.let { h -> out += HeroItem("back:${h.uri}", "Volte a ouvir", if (h.album.isNotBlank()) h.album else h.title, h.artist.ifBlank { h.meta }, h, null, h.album) {
    if (h.album.isNotBlank()) a.openAlbumFrom(h.album, "hero") else a.play(h) } }
  stations.take(2).forEach { st -> out += HeroItem("st:${st.id}", "Feito pra você", st.name, st.subtitle, st.seeds.firstOrNull(), st) { a.playStation(st) } }
  shown.firstOrNull { !it.video && it.album.isNotBlank() && it.album != back?.album }?.let { h ->
    out += HeroItem("new:${h.album}", "Adicionado recentemente", h.album, h.artist, h, null, h.album) { a.openAlbumFrom(h.album, "hero") } }
  return out.take(5)
}

@Composable fun SectionTitle(t: String) = Text(t, fontSize = 22.sp, fontWeight = FontWeight.Bold, letterSpacing = (-.3).sp, modifier = Modifier.padding(top = 6.dp))

/** Readable ink over a soft tile. */
fun contentFor(bg: Color): Color = if (.2126f * bg.red + .7152f * bg.green + .0722f * bg.blue > .55f) Color(0xFF16161A) else Color.White

@Composable fun HeroRow(items: List<HeroItem>, state: LazyListState, s: UiState, a: Actions) {
  if (items.isEmpty()) return
  val w = (LocalConfiguration.current.screenWidthDp * .85f).dp.coerceAtMost(420.dp)
  var menuFor by remember { mutableStateOf<HistoryItem?>(null) }
  var pick by remember { mutableStateOf<HistoryItem?>(null) }
  Carousel(state) { items(items, key = { it.key }) { h -> HeroCard(h, w, s.stationLoading != null && s.stationLoading == h.station?.id) { menuFor = h.track } } }
  menuFor?.let { t -> TrackActionSheet(t, s, a, { menuFor = null }) { pick = t } }
  pick?.let { t -> PlaylistPicker(s.playlists.keys.sorted(), { pick = null }) { name -> a.addToPlaylist(t, name); pick = null } }
}

@Composable private fun HeroCard(h: HeroItem, width: androidx.compose.ui.unit.Dp, loading: Boolean, menu: () -> Unit) {
  val src = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
  val haptics = rememberHaptics()
  val dom = if (h.station != null) Tint.seeded(h.station.id).let { Color(androidx.core.graphics.ColorUtils.HSLToColor(floatArrayOf(h.station.hue, .5f, .4f))) }
    else rememberDominant(h.track?.art.orEmpty(), h.key)
  val scrim = Tint.scrim(dom)
  Box(Modifier.width(width).aspectRatio(1f / 1.15f).weightedPress(src, 22.dp).clip(RoundedCornerShape(22.dp)).background(scrim)
      .clickable(src, null) { haptics.press(); h.open() }) {
    if (h.station != null) StationArt(h.station, Modifier.fillMaxSize())
    else if (h.track != null && h.track.art.isBlank()) Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(Tint.scrim(dom).copy(alpha = 1f), Tint.soft(dom, dark = true), Tint.scrim(dom)))), contentAlignment = Alignment.Center) {
      Icon(Icons.Rounded.MusicNote, null, Modifier.size(72.dp).offset(y = (-40).dp), tint = Color.White.copy(.35f)) }
    else if (h.track != null) Cover(h.track, Modifier.fillMaxSize().sharedCover(h.sharedKey), 0.dp)
    // Bottom scrim tinted with the cover's own color: art stays visible on top, text always readable below.
    Box(Modifier.fillMaxWidth().fillMaxHeight(.5f).align(Alignment.BottomCenter)
      .background(Brush.verticalGradient(0f to scrim.copy(alpha = 0f), .45f to scrim.copy(alpha = .78f), 1f to scrim.copy(alpha = .96f))))
    Column(Modifier.align(Alignment.BottomStart).padding(start = 18.dp, end = 18.dp, bottom = 18.dp)) {
      Text(h.label, color = Color.White.copy(.72f), fontSize = 13.sp, maxLines = 1)
      Text(h.title, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 24.sp)
      Text(h.subtitle, color = Color.White.copy(.72f), fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
    if (loading) CircularProgressIndicator(Modifier.align(Alignment.Center).size(40.dp), color = Color.White, strokeWidth = 3.dp)
    if (h.track != null) GlassBox(Modifier.align(Alignment.TopEnd).padding(12.dp).size(40.dp), CircleShape) {
      Box(Modifier.matchParentSize().clickable { haptics.tick(); menu() }, contentAlignment = Alignment.Center) {
        Icon(Icons.Rounded.MoreHoriz, "Mais opções", tint = Color.White) } }
  }
}

private val StationArtCache = java.util.Collections.synchronizedMap(HashMap<String, ImageBitmap>())

/** Generated station cover (Canvas → bitmap, cached in memory and on disk). */
@Composable fun StationArt(st: Station, m: Modifier) {
  val ctx = LocalContext.current
  val img = remember(st.id, st.name) { StationArtCache.getOrPut(st.id + st.name) { Stations.cover(ctx.takeIf { !StaticPreview.on }, st).asImageBitmap() } }
  Image(img, st.name, m, contentScale = ContentScale.Crop)
}

@Composable fun StationCard(st: Station, loading: Boolean, onClick: () -> Unit) {
  val c = MaterialTheme.colorScheme
  val src = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
  val haptics = rememberHaptics()
  Column(Modifier.width(156.dp).weightedPress(src, 16.dp).clickable(src, null) { haptics.tick(); onClick() }) {
    Box(Modifier.size(156.dp).clip(RoundedCornerShape(14.dp))) {
      StationArt(st, Modifier.fillMaxSize())
      if (loading) Box(Modifier.matchParentSize().background(Color.Black.copy(.35f)), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(Modifier.size(32.dp), color = Color.White, strokeWidth = 3.dp) }
    }
    Spacer(Modifier.height(8.dp))
    Text(st.name, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleMedium)
    Text("Estação · ${st.subtitle}", color = c.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 13.sp)
  }
}
