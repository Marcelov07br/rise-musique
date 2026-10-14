package com.rise.musique.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import com.rise.musique.LibAlbum
import com.rise.musique.LibPlaylist
import com.rise.musique.LibSource
import kotlinx.coroutines.launch

/** Biblioteca tabs (0.23). "Coleção" is the old all-files view; the other four read the unified library. */
val LibraryTabs = listOf("Coleção", "Curtidas", "Playlists", "Álbuns", "Artistas")

/** Covers of a playlist for its mosaic: its own tracks' art, first four distinct. */
fun playlistArts(s: UiState, p: LibPlaylist): List<String> = libraryItems(s, p.tracks).map { it.art }.filter(String::isNotBlank).distinct().take(4)

/** Custom cover when set, else a 2×2 mosaic (one cover fills; none → icon). */
@Composable fun PlaylistArt(s: UiState, p: LibPlaylist?, modifier: Modifier, radius: Dp = 14.dp) {
  val c = MaterialTheme.colorScheme
  val arts = remember(p, s.history, s.library) { if (p == null) emptyList() else playlistArts(s, p) }
  Box(modifier.clip(RoundedCornerShape(radius)).background(c.surfaceVariant), contentAlignment = Alignment.Center) {
    when {
      p != null && p.cover.isNotBlank() -> AsyncImage(p.cover, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
      arts.size >= 4 -> Column(Modifier.fillMaxSize()) { arts.chunked(2).forEach { row -> Row(Modifier.weight(1f)) {
        row.forEach { AsyncImage(it, null, Modifier.weight(1f).fillMaxHeight(), contentScale = ContentScale.Crop) } } } }
      arts.isNotEmpty() -> AsyncImage(arts.first(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
      else -> Icon(Icons.Rounded.QueueMusic, null, tint = c.onSurfaceVariant)
    }
  }
}

/** Liked tracks (files, streams, Spotify), newest like first. */
fun LazyListScope.likedTab(s: UiState, a: Actions) {
  val liked = libraryItems(s, s.library.likes.map { it.key })
  if (liked.isEmpty()) { item(key = "liked-empty") { EmptyState(Icons.Rounded.FavoriteBorder, "Suas curtidas ficam aqui",
    "Toque no coração no player, ou segure uma faixa (também online) e escolha Curtir.") }; return }
  item(key = "liked-head") { Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
    PillButton("Tocar", { a.playList(liked, liked.first()) }, Modifier.weight(1f), Icons.Rounded.PlayArrow)
    PillButton("Aleatório", { a.playList(liked.shuffled(), liked.random()) }, Modifier.weight(1f), Icons.Rounded.Shuffle, filled = false)
  } }
  item(key = "liked-count") { Text("${liked.size} ${if (liked.size == 1) "faixa curtida" else "faixas curtidas"}", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp) }
  items(liked, key = { "lk:" + it.uri }) { h -> TrackRow(h, s, a, queue = liked) }
}

fun LazyListScope.playlistsTab(s: UiState, a: Actions, create: () -> Unit, spotify: () -> Unit, rename: (String) -> Unit, delete: (String) -> Unit) {
  item(key = "pl-actions") { Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
    PillButton("Nova playlist", create, Modifier.weight(1f), Icons.Rounded.Add)
    PillButton("Do Spotify", spotify, Modifier.weight(1f), Icons.Rounded.Link, filled = false)
  } }
  if (s.library.playlists.isEmpty()) { item(key = "pl-empty") { EmptyState(Icons.Rounded.QueueMusic, "Nenhuma playlist ainda",
    "Crie uma, salve a fila ou importe um link do Spotify.") }; return }
  itemsIndexed(s.library.playlists, key = { _, p -> "plr:" + p.id }) { i, p ->
    PlaylistRow(s, a, p, i, s.library.playlists.size, rename, delete)
  }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable private fun PlaylistRow(s: UiState, a: Actions, p: LibPlaylist, index: Int, count: Int, rename: (String) -> Unit, delete: (String) -> Unit) {
  val c = MaterialTheme.colorScheme
  var menu by remember(p.id) { mutableStateOf(false) }
  val haptics = rememberHaptics()
  Box {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).combinedClickable(onLongClick = { haptics.press(); menu = true }) { haptics.tick(); a.openPlaylist(p.name) }
        .padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
      PlaylistArt(s, p, Modifier.size(64.dp).sharedCover("cover-pl-${p.name}"), 10.dp)
      Spacer(Modifier.width(14.dp))
      Column(Modifier.weight(1f)) {
        Text(p.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(listOfNotNull("${p.tracks.size} ${if (p.tracks.size == 1) "faixa" else "faixas"}", if (p.source == LibSource.SPOTIFY) "Spotify" else null).joinToString(" · "),
          color = c.onSurfaceVariant, fontSize = 13.sp)
      }
      IconButton({ menu = true }) { Icon(Icons.Rounded.MoreVert, "Opções de ${p.name}", tint = c.onSurfaceVariant) }
    }
    DropdownMenu(menu, { menu = false }) {
      DropdownMenuItem({ Text("Renomear") }, { menu = false; rename(p.name) }, leadingIcon = { Icon(Icons.Rounded.Edit, null) })
      DropdownMenuItem({ Text("Escolher capa") }, { menu = false; a.pickCover(p.name) }, leadingIcon = { Icon(Icons.Rounded.Image, null) })
      if (p.cover.isNotBlank()) DropdownMenuItem({ Text("Capa automática") }, { menu = false; a.resetCover(p.name) }, leadingIcon = { Icon(Icons.Rounded.GridView, null) })
      if (index > 0) DropdownMenuItem({ Text("Mover para cima") }, { menu = false; a.reorderPlaylists(index, index - 1) }, leadingIcon = { Icon(Icons.Rounded.KeyboardArrowUp, null) })
      if (index < count - 1) DropdownMenuItem({ Text("Mover para baixo") }, { menu = false; a.reorderPlaylists(index, index + 1) }, leadingIcon = { Icon(Icons.Rounded.KeyboardArrowDown, null) })
      DropdownMenuItem({ Text("Apagar playlist", color = DangerRed) }, { menu = false; delete(p.name) }, leadingIcon = { Icon(Icons.Rounded.DeleteOutline, null, tint = DangerRed) })
    }
  }
}

/** Saved albums (YouTube Music and local), then albums made of local files. */
fun LazyListScope.albumsTab(s: UiState, a: Actions, files: List<HistoryItem>) {
  val saved = s.library.albums
  val savedLocal = saved.filter { it.id.startsWith("local:") }.map { it.title }.toSet()
  val local = files.filter { !it.video && it.album.isNotBlank() && it.album !in savedLocal }.groupBy { it.album }.entries.toList()
  if (saved.isEmpty() && local.isEmpty()) { item(key = "al-empty") { EmptyState(Icons.Rounded.Album, "Nenhum álbum salvo",
    "Abra um álbum e toque em Salvar para guardá-lo aqui.") }; return }
  if (saved.isNotEmpty()) item(key = "al-saved-title") { SectionTitle("Salvos") }
  items(saved, key = { "als:" + it.id }) { al -> AlbumRow(al.title, al.artist.ifBlank { if (al.source == LibSource.LOCAL) "Na biblioteca" else "Álbum" }, al.art) {
    if (al.id.startsWith("local:")) a.openAlbum(al.title) else a.openRemoteAlbum(SearchResult(al.id, al.title, al.artist, 0, al.art, "album"), "lib") } }
  if (local.isNotEmpty()) item(key = "al-local-title") { SectionTitle("Dos seus arquivos") }
  items(local, key = { "all:" + it.key }) { (name, t) -> val h = t.firstOrNull { it.art.isNotBlank() } ?: t.first()
    AlbumRow(name, h.artist.ifBlank { "${t.size} faixas" }, h.art) { a.openAlbum(name) } }
}

@Composable private fun AlbumRow(title: String, sub: String, art: String, circle: Boolean = false, trailing: @Composable () -> Unit = {}, onClick: () -> Unit) {
  val haptics = rememberHaptics()
  Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable { haptics.tick(); onClick() }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
    Cover(HistoryItem(title, "", art = art), Modifier.size(64.dp), if (circle) 32.dp else 10.dp); Spacer(Modifier.width(14.dp))
    Column(Modifier.weight(1f)) {
      Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
      Text(sub, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
    trailing()
  }
}

/** Followed artists first (with Deixar de seguir), then artists of local files. */
fun LazyListScope.artistsTab(s: UiState, a: Actions, files: List<HistoryItem>) {
  val followed = s.library.artists
  val names = followed.map { it.name.lowercase() }.toSet()
  val local = artistsOf(files).filter { it.first.lowercase() !in names }
  if (followed.isEmpty() && local.isEmpty()) { item(key = "ar-empty") { EmptyState(Icons.Rounded.Person, "Nenhum artista seguido",
    "No perfil de um artista, toque em Seguir.") }; return }
  if (followed.isNotEmpty()) item(key = "ar-f-title") { SectionTitle("Seguindo") }
  items(followed, key = { "arf:" + it.name }) { ar ->
    AlbumRow(ar.name, "Seguindo", s.artistPhotos[ar.name] ?: ar.art, circle = true, trailing = {
      TextButton({ a.follow(ar.name) }) { Text("Deixar de seguir", fontSize = 13.sp) } }) { a.openArtist(ar.name) } }
  if (local.isNotEmpty()) item(key = "ar-l-title") { SectionTitle("Dos seus arquivos") }
  items(local, key = { "arl:" + it.first }) { (name, h, n) ->
    AlbumRow(name, "$n ${if (n == 1) "faixa" else "faixas"}", s.artistPhotos[name] ?: h.art, circle = true) { a.openArtist(name) } }
}

@Composable fun NewPlaylistDialog(taken: Set<String>, dismiss: () -> Unit, create: (String) -> Unit) {
  var name by remember { mutableStateOf("") }
  val clash = name.trim() in taken || name.trim() in com.rise.musique.LibrarySchema.RESERVED
  AlertDialog(dismiss, shape = RoundedCornerShape(28.dp), title = { Text("Nova playlist") },
    text = { OutlinedTextField(name, { name = it.take(80) }, Modifier.fillMaxWidth(), singleLine = true, shape = CircleShape,
      placeholder = { Text("Nome da playlist") }, isError = clash, supportingText = { if (clash) Text("Escolha outro nome") }) },
    confirmButton = { TextButton({ create(name.trim()) }, enabled = name.isNotBlank() && !clash) { Text("Criar") } },
    dismissButton = { TextButton(dismiss) { Text("Cancelar") } })
}

/** Pick tracks from the library (files + likes) to add to [playlist]; already-added ones are marked. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable fun AddTracksSheet(s: UiState, playlist: String, close: () -> Unit, add: (List<HistoryItem>) -> Unit) {
  val c = MaterialTheme.colorScheme
  val inList = s.playlists[playlist].orEmpty().toSet()
  val pool = remember(s.history, s.library) { (s.history.filter { it.uri !in s.hidden } + libraryItems(s, s.library.likes.map { it.key })).distinctBy { it.uri } }
  var q by remember { mutableStateOf("") }
  var picked by remember { mutableStateOf(emptySet<String>()) }
  val shown = pool.filter { q.isBlank() || it.title.contains(q, true) || it.artist.contains(q, true) || it.album.contains(q, true) }
  ModalBottomSheet(close, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
    Column(Modifier.fillMaxWidth().navigationBarsPadding()) {
      Row(Modifier.padding(horizontal = 24.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("Adicionar a $playlist", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
        TextButton({ add(pool.filter { it.uri in picked }); close() }, enabled = picked.isNotEmpty()) { Text(if (picked.isEmpty()) "Adicionar" else "Adicionar ${picked.size}") }
      }
      OutlinedTextField(q, { q = it }, Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp), singleLine = true, shape = RoundedCornerShape(20.dp),
        placeholder = { Text("Buscar na biblioteca") }, leadingIcon = { Icon(Icons.Rounded.Search, null) })
      LazyColumn(Modifier.fillMaxWidth().heightIn(max = 520.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
        items(shown, key = { "add:" + it.uri }) { h -> val already = h.uri in inList; val on = h.uri in picked
          Row(Modifier.fillMaxWidth().clickable(enabled = !already) { picked = if (on) picked - h.uri else picked + h.uri }.padding(horizontal = 24.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Cover(h, Modifier.size(48.dp), 8.dp); Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
              Text(h.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 15.sp)
              Text(h.artist.ifBlank { h.meta }, color = c.onSurfaceVariant, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Icon(when { already -> Icons.Rounded.CheckCircle; on -> Icons.Rounded.CheckCircle; else -> Icons.Rounded.AddCircleOutline },
              if (already) "Já na playlist" else if (on) "Selecionada" else "Selecionar", tint = if (already) c.onSurfaceVariant.copy(.4f) else if (on) c.primary else c.onSurfaceVariant)
          }
        }
      }
    }
  }
}

/**
 * Drag-to-reorder for playlist rows, same feel as the 0.22 queue: hold ~1 s lifts (spring ζ0.6 on graphicsLayer only),
 * the order is local while dragging and the library gets a single move on drop; a quick tap plays.
 */
class PlaylistReorder(private val name: String) {
  var dragList by mutableStateOf<List<HistoryItem>?>(null)
  var dragKey by mutableStateOf<String?>(null)
  var dragStart by mutableIntStateOf(0)
  var travel by mutableFloatStateOf(0f)
  var settlingKey by mutableStateOf<String?>(null)
  val settle = Animatable(0f)
  fun shown(tracks: List<HistoryItem>) = dragList ?: tracks
  fun offset(key: String, index: Int, rowPx: Float) = when { key == dragKey -> travel - (index - dragStart) * rowPx; key == settlingKey -> settle.value; else -> 0f }
  fun elevated(key: String) = key == dragKey || key == settlingKey
}

@Composable fun Modifier.reorderable(r: PlaylistReorder, h: HistoryItem, tracks: () -> List<HistoryItem>, rowPx: Float,
                                     tap: () -> Unit, drop: (Int, Int) -> Unit): Modifier {
  val haptics = rememberHaptics(); val scope = rememberCoroutineScope()
  return pointerInput(h.uri) {
    awaitEachGesture {
      val down = awaitFirstDown(requireUnconsumed = false)
      val early = withTimeoutOrNull(QueueMotion.LiftMs) {
        while (true) { val ch = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: return@withTimeoutOrNull "gone"
          if (!ch.pressed) return@withTimeoutOrNull if ((ch.position - down.position).getDistance() < viewConfiguration.touchSlop) "tap" else "gone"
          if (ch.isConsumed || (ch.position - down.position).getDistance() > viewConfiguration.touchSlop) return@withTimeoutOrNull "gone" }
        @Suppress("UNREACHABLE_CODE") "gone"
      }
      if (early == "tap") { haptics.tick(); tap(); return@awaitEachGesture }
      if (early != null) return@awaitEachGesture
      val list0 = tracks(); val start = list0.indexOfFirst { it.uri == h.uri }.takeIf { it >= 0 } ?: return@awaitEachGesture
      haptics.press(); r.dragList = list0; r.dragStart = start; r.travel = 0f; r.dragKey = h.uri
      drag(down.id) { ch -> ch.consume(); r.travel += ch.positionChange().y
        val list = r.dragList ?: return@drag
        val from = list.indexOfFirst { it.uri == h.uri }; val to = QueueMotion.target(r.dragStart, r.travel, rowPx, list.size)
        if (from >= 0 && to != from) { r.dragList = list.toMutableList().apply { add(to, removeAt(from)) }; haptics.tick() } }
      val to = r.dragList.orEmpty().indexOfFirst { it.uri == h.uri }
      val rest = r.travel - (to - r.dragStart) * rowPx
      r.settlingKey = h.uri; r.dragKey = null
      if (to >= 0 && to != r.dragStart) drop(r.dragStart, to)
      r.dragList = null
      scope.launch { r.settle.snapTo(rest); r.settle.animateTo(0f, QueueMotion.settle()); if (r.settlingKey == h.uri) r.settlingKey = null }
    }
  }
}
