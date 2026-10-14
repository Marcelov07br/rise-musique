package com.rise.musique.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import com.rise.musique.Engine
import com.rise.musique.SpotifyImport
import com.rise.musique.SpotifyPlaylist
import com.rise.musique.SpotifyTrack
import com.rise.musique.Streams
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val SpotifyGreen = Color(0xFF1DB954)

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable fun SpotifyScreen(
  s: UiState,
  a: Actions,
  r: Route.Spotify,
  list: LazyListState,
  modifier: Modifier = Modifier,
  onBack: () -> Unit = {},
  dismiss: Animatable<Float, AnimationVector1D> = remember { Animatable(0f) }
) {
  val c = MaterialTheme.colorScheme
  val playlist = s.spotifyPlaylists[r.id]
  val loading = r.id in s.spotifyLoading
  LaunchedEffect(r.id, r.type) {
    if (playlist == null && !loading) {
      a.loadSpotify(r.type, r.id)
    }
  }

  val tracks = playlist?.tracks.orEmpty()
  LaunchedEffect(playlist?.id) {
    if (playlist != null && tracks.isNotEmpty()) {
      withContext(Dispatchers.IO) {
        tracks.take(6).forEach { t ->
          if (SpotifyImport.getCachedSearchResult(t.uri) == null && SpotifyImport.findLocalMatch(t, s.history) == null) {
            SpotifyImport.resolveToSearchResult(t)
          }
        }
      }
    }
  }
  val title = playlist?.title?.ifBlank { null } ?: r.title.ifBlank { "Playlist do Spotify" }
  val subtitle = playlist?.subtitle?.ifBlank { null } ?: r.subtitle
  val art = playlist?.art?.ifBlank { null } ?: r.art

  val haptics = rememberHaptics()
  val back = { haptics.tick(); onBack() }
  val collapseState = rememberUpdatedState(collapseOf(list))
  val collapse = collapseState.value
  val totalMs = tracks.sumOf { it.durationMs }
  val totalSec = totalMs / 1000
  val meta = listOf(
    if (r.type == "album") "ÁLBUM SPOTIFY" else "SPOTIFY PLAYLIST",
    if (tracks.isNotEmpty()) "${tracks.size} FAIXAS" else "",
    if (totalSec > 0) "${(totalSec / 60).coerceAtLeast(1)} MIN" else ""
  ).filter(String::isNotBlank).joinToString(" · ")

  Box(modifier.dragToDismiss(dismiss, onBack)) {
    LazyColumn(Modifier.fillMaxSize(), state = list, contentPadding = PaddingValues(top = TopBarHeight, bottom = bottomSpace(s))) {
      item(key = "header") {
        Column(
          Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, bottom = 16.dp).graphicsLayer { alpha = (1f - collapse * 1.2f).coerceIn(0f, 1f) },
          horizontalAlignment = Alignment.CenterHorizontally
        ) {
          val corner = morphCorner(18.dp, 8.dp)?.value ?: 18.dp
          Box(
            Modifier.size(208.dp)
              .graphicsLayer { val sc = 1f - .15f * collapseState.value.coerceIn(0f, 1f); scaleX = sc; scaleY = sc }
              .sharedCover("cover-sp-${if (r.src.isNotBlank()) "${r.src}-" else ""}${r.id}")
              .clip(RoundedCornerShape(corner))
              .background(Color.White.copy(.12f))
          ) {
            if (art.isNotBlank()) {
              AsyncImage(art, null, Modifier.fillMaxSize().kenBurns(true), contentScale = ContentScale.Crop)
            } else {
              Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.QueueMusic, null, tint = SpotifyGreen, modifier = Modifier.size(64.dp))
              }
            }
          }
          Spacer(Modifier.height(16.dp))
          Column(Modifier.enterRise(), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
              title,
              color = Color.White,
              fontSize = 24.sp,
              lineHeight = 28.sp,
              fontWeight = FontWeight.Bold,
              textAlign = TextAlign.Center,
              maxLines = 2,
              overflow = TextOverflow.Ellipsis
            )
            if (subtitle.isNotBlank()) {
              Spacer(Modifier.height(4.dp))
              Text(
                subtitle,
                color = Color.White.copy(.8f),
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
              )
            }
            Spacer(Modifier.height(4.dp))
            Text(
              meta,
              color = Color.White.copy(.7f),
              fontSize = 12.sp,
              letterSpacing = 1.sp,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
              textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
              GlassIcon(
                Icons.Rounded.Shuffle,
                "Aleatório",
                { haptics.press(); a.playSpotifyTracks(tracks, 0, true) },
                enabled = tracks.isNotEmpty()
              )
              GlassBox(Modifier.height(48.dp).width(144.dp), CircleShape) {
                Row(
                  Modifier.matchParentSize().clickable(enabled = tracks.isNotEmpty()) {
                    haptics.press()
                    a.playSpotifyTracks(tracks, 0, false)
                  },
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.Center
                ) {
                  Icon(Icons.Rounded.PlayArrow, null, tint = Color.White)
                  Spacer(Modifier.width(8.dp))
                  Text("Tocar", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                }
              }
              GlassIcon(
                Icons.Rounded.Download,
                "Baixar tudo",
                {
                  haptics.press()
                  playlist?.let { a.downloadSpotifyTracks(it, tracks) }
                },
                enabled = tracks.isNotEmpty()
              )
            }
            Spacer(Modifier.height(12.dp))
            playlist?.let { pl ->
              val isSaved = s.savedSpotifyPlaylists.any { it.id == pl.id }
              TextButton(
                onClick = {
                  haptics.press()
                  if (isSaved) a.deleteSpotifyPlaylist(pl.id) else a.saveSpotifyPlaylist(pl)
                },
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)
              ) {
                Icon(if (isSaved) Icons.Rounded.Bookmark else Icons.Rounded.BookmarkBorder, null, tint = SpotifyGreen, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(if (isSaved) "Salva na Biblioteca" else "Salvar na Biblioteca", color = SpotifyGreen, fontSize = 13.sp, fontWeight = FontWeight.Medium)
              }
            }
          }
        }
      }

      if (loading || playlist == null) {
        item(key = "loading") { SkeletonRows("Carregando faixas do Spotify…") }
      }

      itemsIndexed(tracks, key = { _, t -> "st:" + t.uri }) { i, t ->
        val local = remember(t.uri, s.history) { SpotifyImport.findLocalMatch(t, s.history) }
        val resolved = s.spotifyResolvedTracks[t.uri] ?: SpotifyImport.getCachedSearchResult(t.uri)
        val isNowPlaying = (local != null && s.now?.key == local.uri) || (resolved != null && s.now?.key == "stream:" + resolved.url)
        val isResolving = t.uri in s.spotifyResolving
        val src = remember { MutableInteractionSource() }
        var menu by remember { mutableStateOf(false) }
        if (menu) StreamActionSheet(t.title, t.artist, local?.art ?: resolved?.thumb.orEmpty(), { menu = false },
          { a.queueSpotify(t, true) }, { a.queueSpotify(t, false) })

        Row(
          Modifier.fillMaxWidth()
            .padding(horizontal = 16.dp)
            .weightedPress(src, 14.dp)
            .clip(RoundedCornerShape(14.dp))
            .combinedClickable(src, null, onLongClick = { haptics.press(); menu = true }) {
              haptics.tick()
              a.playSpotifyTracks(tracks, i, false)
            }
            .padding(vertical = 10.dp),
          verticalAlignment = Alignment.CenterVertically
        ) {
          if (isNowPlaying) {
            Icon(Icons.Rounded.GraphicEq, null, Modifier.width(36.dp), tint = accentColor())
          } else {
            Text("${i + 1}", Modifier.width(36.dp), color = c.onSurfaceVariant, fontSize = 15.sp, textAlign = TextAlign.Center)
          }
          Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Text(
                t.title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = if (isNowPlaying) accentColor() else c.onSurface,
                modifier = Modifier.weight(1f, fill = false)
              )
              if (local != null) {
                Spacer(Modifier.width(6.dp))
                Icon(Icons.Rounded.CheckCircle, "Já baixada", tint = SpotifyGreen, modifier = Modifier.size(14.dp))
              }
            }
            if (t.artist.isNotBlank()) {
              Text(t.artist, color = c.onSurfaceVariant, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
          }
          if (t.durationMs > 0) {
            val sec = t.durationMs / 1000
            Text("%d:%02d".format(sec / 60, sec % 60), color = c.onSurfaceVariant, fontSize = 13.sp)
          }
          if (isResolving) {
            CircularProgressIndicator(Modifier.size(36.dp).padding(10.dp), strokeWidth = 2.dp, color = c.onSurface)
          } else if (local == null) {
            IconButton({
              haptics.press()
              if (resolved != null) {
                a.downloadItem(resolved)
              } else {
                a.playSpotifyTracks(listOf(t), 0, false)
              }
            }) {
              Icon(Icons.Rounded.Download, "Baixar ${t.title}", tint = c.onSurfaceVariant)
            }
          }
        }
      }
    }

    DetailTopBar(
      title,
      collapse,
      back,
      if (tracks.isEmpty()) null else ({
        haptics.press()
        a.playSpotifyTracks(tracks, 0, false)
      })
    )
  }
}
