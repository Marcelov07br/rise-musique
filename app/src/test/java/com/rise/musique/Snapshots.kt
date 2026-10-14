package com.rise.musique
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.sp
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.material3.Text
import androidx.compose.ui.graphics.asImageBitmap
import app.cash.paparazzi.*
import com.android.resources.*
import com.rise.musique.ui.*
import org.junit.*
import androidx.compose.foundation.layout.*
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.background
private val init = run { StaticPreview.on = true }
private val found = init.let { UiState(query = "música de exemplo", results = sampleResults, selected = setOf("a"), previewing = "a", history = sampleHistory, cutOn = true, cutStart = "0:45", cutEnd = "1:30") }
private val playlist = init.let { _ -> null }.let { UiState(query = "https://youtube.com/playlist?list=x", results = sampleResults, isPlaylist = true, selected = setOf("a", "c"), format = "FLAC") }
private val queued = UiState(history = sampleHistory, queue = listOf(QueueItem("1", "Música de exemplo", .42f, true), QueueItem("2", "Outra música", 0f, false), QueueItem("3", "Vídeo removido", 0f, false, "ERROR: Video unavailable")),
  status = "[download]  42.0% of 8.40MiB")
private val np = NowPlaying("x", "Música de exemplo", "Artista Exemplo", "", playing = true, quality = "FLAC · 1411 kbps · 44.1 kHz · Estéreo", position = 74_000, duration = 214_000, hasNext = true, count = 12, index = 2,
  lyrics = listOf(Lyrics.Line(60_000, "Primeira linha de exemplo"), Lyrics.Line(70_000, "Essa é a linha que está tocando"), Lyrics.Line(80_000, "Próxima linha da letra"), Lyrics.Line(90_000, "E mais uma depois")))
class Player { @get:Rule val p = Paparazzi(deviceConfig = DeviceConfig.PIXEL_6.copy(nightMode = NightMode.NIGHT))
  @Test fun mini() = p.snapshot { RiseTheme(true) { RiseApp(found.copy(now = np), Actions()) } }
  @Test fun lyrics() = p.snapshot { RiseTheme(true) { RiseApp(found.copy(now = np), Actions(), initialExpanded = true, initialLyrics = true) } }
  @Test fun full() = p.snapshot { RiseTheme(true) { RiseApp(found.copy(now = np), Actions(), initialExpanded = true) } } }
class LibrarySnapshots { @get:Rule val p = Paparazzi(deviceConfig = DeviceConfig.PIXEL_6.copy(nightMode = NightMode.NIGHT))
  @Test fun panel() = p.snapshot { StaticPreview.on = true; RiseTheme(true) { RiseApp(UiState(history = sampleHistory.mapIndexed { i, h -> h.copy(uri = "u$i") },
    favorites = setOf("u0"), playlists = mapOf("Treino" to listOf("u0", "u2"), "Referências" to listOf("u1")), historyFilter = ""), Actions(), initialHistoryOpen = true) } }
  @Test fun sleep() = p.snapshot { RiseTheme(true) { RiseApp(found.copy(now = np, sleepAt = System.currentTimeMillis() + 29 * 60_000L), Actions(), initialExpanded = true) } } }
class PhoneDark { @get:Rule val p = Paparazzi(deviceConfig = DeviceConfig.PIXEL_6.copy(nightMode = NightMode.NIGHT))
  @Test fun results() = p.snapshot { RiseTheme(true) { RiseApp(found, Actions()) } }
  @Test fun queue() = p.snapshot { RiseTheme(true) { RiseApp(queued, Actions(), initialHistoryOpen = true) } } }
class PhoneLight { @get:Rule val p = Paparazzi(deviceConfig = DeviceConfig.PIXEL_6)
  @Test fun playlist() = p.snapshot { RiseTheme(false) { RiseApp(playlist, Actions()) } }
  @Test fun results() = p.snapshot { RiseTheme(false) { RiseApp(found, Actions()) } } }
class Tablet { @get:Rule val p = Paparazzi(deviceConfig = DeviceConfig.PIXEL_C)
  @Test fun results() = p.snapshot { RiseTheme(true) { RiseApp(found.copy(queue = queued.queue), Actions()) } } }

class Stats { @get:Rule val p = Paparazzi(deviceConfig = DeviceConfig.PIXEL_6.copy(nightMode = NightMode.NIGHT))
  private val now = System.currentTimeMillis()
  private val data = init.let { PlayStats.summarize(listOf(
    Play("1", "Música de exemplo", "Artista Exemplo", 3_600_000, now), Play("1", "Música de exemplo", "Artista Exemplo", 200_000, now - 40L * 86_400_000),
    Play("2", "Outra faixa", "Canal Exemplo", 900_000, now - 70L * 86_400_000), Play("3", "Faixa ao vivo", "Artista Exemplo", 500_000, now - 100L * 86_400_000),
    Play("2", "Outra faixa", "Canal Exemplo", 700_000, now)), now) }
  @Test fun dark() = p.snapshot { RiseTheme(true) { androidx.compose.material3.Surface { StatsContent(data) } } }
  @Test fun empty() = p.snapshot { RiseTheme(true) { androidx.compose.material3.Surface { StatsContent(StatsSummary()) } } } }
class StatsLight { @get:Rule val p = Paparazzi(deviceConfig = DeviceConfig.PIXEL_6)
  @Test fun light() = p.snapshot { StaticPreview.on = true; RiseTheme(false) { androidx.compose.material3.Surface { StatsContent(PlayStats.summarize(listOf(
    Play("1", "Música de exemplo", "Artista Exemplo", 1_800_000, System.currentTimeMillis())))) } } } }

private val albumHistory = init.let { listOf("Abertura", "Segunda faixa", "Interlúdio", "Final").mapIndexed { i, t ->
  HistoryItem(t, "M4A · 256k", "album:$i", artist = "Artista Exemplo", album = "Disco de Exemplo", duration = 180_000L + i * 20_000) } }
class Collection { @get:Rule val p = Paparazzi(deviceConfig = DeviceConfig.PIXEL_6.copy(nightMode = NightMode.NIGHT))
  @Test fun album() = p.snapshot { RiseTheme(true) { RiseApp(UiState(history = albumHistory), Actions(), initialRoute = com.rise.musique.ui.Route.Album("Disco de Exemplo")) } }
  @Test fun playlistWithMini() = p.snapshot { RiseTheme(true) { RiseApp(UiState(history = albumHistory, playlists = mapOf("Treino" to listOf("album:2", "album:0")),
    now = np), Actions(), initialRoute = com.rise.musique.ui.Route.Playlist("Treino")) } } }

private val queueSample = albumHistory
class Wide { @get:Rule val p = Paparazzi(deviceConfig = DeviceConfig.PIXEL_6.copy(screenWidth = 2400, screenHeight = 1080, orientation = ScreenOrientation.LANDSCAPE, nightMode = NightMode.NIGHT))
  @Test fun playerQueue() = p.snapshot { RiseTheme(true) { RiseApp(found.copy(now = np.copy(index = 0), playbackQueue = queueSample), Actions(), initialExpanded = true) } }
  @Test fun playerLyrics() = p.snapshot { RiseTheme(true) { RiseApp(found.copy(now = np), Actions(), initialExpanded = true, initialLyrics = true) } } }
class TabletPlayer { @get:Rule val p = Paparazzi(deviceConfig = DeviceConfig.PIXEL_C.copy(nightMode = NightMode.NIGHT))
  @Test fun player() = p.snapshot { RiseTheme(true) { RiseApp(found.copy(now = np.copy(index = 0), playbackQueue = queueSample), Actions(), initialExpanded = true) } } }
class Round12 { @get:Rule val p = Paparazzi(deviceConfig = DeviceConfig.PIXEL_6.copy(nightMode = NightMode.NIGHT))
  private val recap = init.let { Recap("Seu outubro", "Outubro de 2026", 1834, 412, Ranked("Música de exemplo", "Artista Exemplo", 31, 0),
    Ranked("Artista Exemplo", "", 120, 0), listOf(Ranked("Música de exemplo", "Artista Exemplo", 31, 0), Ranked("Outra faixa", "Canal Exemplo", 22, 0), Ranked("Faixa ao vivo", "Artista Exemplo", 18, 0))) }
  @Test fun recapCard() = p.snapshot { RiseTheme(true) { androidx.compose.material3.Surface { RecapCard(recap, androidx.compose.ui.Modifier.padding(24.dp).aspectRatio(9f / 16f)) } } }
  @Test fun radio() = p.snapshot { RiseTheme(true) { RiseApp(UiState(page = "Buscar", results = sampleResults, isPlaylist = true, radioOf = "Música de exemplo", selected = setOf("a")), Actions()) } } }

private val longAlbum = init.let { (1..20).map { i -> HistoryItem("Faixa número $i do disco", "M4A · 256k", "long:$i", artist = "Artista Exemplo", album = "Disco Longo", duration = 200_000L) } }
class Polish { @get:Rule val p = Paparazzi(deviceConfig = DeviceConfig.PIXEL_6.copy(nightMode = NightMode.NIGHT))
  @Test fun albumTop() = p.snapshot { RiseTheme(true) { RiseApp(UiState(history = longAlbum, now = np), Actions(), initialRoute = com.rise.musique.ui.Route.Album("Disco Longo")) } }
  @Test fun albumScrolled() = p.snapshot { RiseTheme(true) { RiseApp(UiState(history = longAlbum, now = np), Actions(), initialRoute = com.rise.musique.ui.Route.Album("Disco Longo"), initialCollectionItem = 6) } }
  @Test fun searchCutRow() = p.snapshot { RiseTheme(true) { RiseApp(found.copy(page = "Buscar", choices = DownloadChoices(media = true)), Actions()) } }
  @Test fun cutSheetMusic() = p.snapshot { RiseTheme(true) { androidx.compose.material3.Surface { CutContent(found.copy(
    cutVisual = CutVisual(false, peaks = (0 until 120).map { i -> (kotlin.math.abs(kotlin.math.sin(i / 7.0)) * .8 + (i % 5) * .04).toFloat() })), Actions(),
    androidx.compose.ui.Modifier.padding(24.dp)) } } }
  @Test fun cutSheetLoading() = p.snapshot { RiseTheme(true) { androidx.compose.material3.Surface { CutContent(found.copy(mode = Mode.Video, cutVisual = CutVisual(true)), Actions(),
    androidx.compose.ui.Modifier.padding(24.dp)) } } } }
class PolishLight { @get:Rule val p = Paparazzi(deviceConfig = DeviceConfig.PIXEL_6)
  @Test fun albumTop() = p.snapshot { RiseTheme(false) { RiseApp(UiState(history = longAlbum), Actions(), initialRoute = com.rise.musique.ui.Route.Album("Disco Longo")) } }
  @Test fun albumScrolled() = p.snapshot { RiseTheme(false) { RiseApp(UiState(history = longAlbum), Actions(), initialRoute = com.rise.musique.ui.Route.Album("Disco Longo"), initialCollectionItem = 6) } } }
class MotionHome { @get:Rule val p = Paparazzi(deviceConfig = DeviceConfig.PIXEL_6.copy(nightMode = NightMode.NIGHT, screenHeight = 4000))
  @Test fun carousels() = p.snapshot { RiseTheme(true) { RiseApp(UiState(history = longAlbum + (1..3).map { i -> HistoryItem("Single $i", "M4A", "s:$i", artist = "Outro", album = "Single $i") },
    playlists = mapOf("Treino" to listOf("long:1", "long:2"), "Estrada" to listOf("s:1"), "Noite" to listOf("long:3"))), Actions()) } } }

private val artistLib = longAlbum.take(8) + (1..3).map { i -> HistoryItem("Single $i", "M4A", "s:$i", artist = "Artista Exemplo", album = "Single $i") } +
  listOf(HistoryItem("Outra coisa", "MP3", "o:1", artist = "Outro Artista"))
private val ytPopular = (1..5).map { SearchResult("yt:$it", "Sucesso número $it (Clipe Oficial)", "Artista Exemplo", 180 + it * 7, "") }
class Round14 { @get:Rule val p = Paparazzi(deviceConfig = DeviceConfig.PIXEL_6.copy(nightMode = NightMode.NIGHT))
  private val lib = UiState(history = artistLib, playlists = mapOf("Treino" to listOf("long:1", "long:2"), "Noite" to listOf("s:1")), favorites = setOf("long:2"))
  @Test fun tabHome() = p.snapshot { RiseTheme(true) { RiseApp(lib, Actions()) } }
  @Test fun tabSearch() = p.snapshot { RiseTheme(true) { RiseApp(lib.copy(page = "Buscar", recentSearches = listOf("lofi", "Artista Exemplo", "trilha sonora de filme")), Actions()) } }
  @Test fun tabLibrary() = p.snapshot { RiseTheme(true) { RiseApp(lib.copy(page = "Biblioteca", now = np), Actions()) } }
  @Test fun searchBoth() = p.snapshot { RiseTheme(true) { RiseApp(lib.copy(page = "Buscar", query = "Exemplo", results = ytPopular.take(3)), Actions()) } }
  @Test fun searchNone() = p.snapshot { RiseTheme(true) { RiseApp(lib.copy(page = "Buscar", query = "zzzz", status = "Nada encontrado"), Actions()) } }
  @Test fun artist() = p.snapshot { RiseTheme(true) { RiseApp(lib.copy(page = "Biblioteca", popular = mapOf("Artista Exemplo" to ytPopular)), Actions(),
    initialRoute = com.rise.musique.ui.Route.Artist("Artista Exemplo")) } }
  @Test fun artistScrolled() = p.snapshot { RiseTheme(true) { RiseApp(lib.copy(page = "Biblioteca", popular = mapOf("Artista Exemplo" to ytPopular)), Actions(),
    initialRoute = com.rise.musique.ui.Route.Artist("Artista Exemplo"), initialCollectionItem = 9) } }
  @Test fun actionSheet() = p.snapshot { RiseTheme(true) { androidx.compose.material3.Surface { com.rise.musique.ui.TrackActionContent(artistLib[1],
    lib, Actions(), {}, {}) } } }
  @Test fun undoSnackbar() = p.snapshot { RiseTheme(true) { RiseApp(lib.copy(page = "Biblioteca", now = np, undo = com.rise.musique.ui.UndoInfo(1, "Arquivo apagado")), Actions()) } }
}

private val r15Info = artistLib.mapIndexed { i, h -> h.uri to TrackInfo(if (i % 2 == 0) -9.0 else -15.0, .9, if (i < 6) "Rock" else "Jazz", if (i < 5) 1994 else 2012, if (i % 2 == 0) 128.0 else 84.0) }.toMap()
class Round15 { @get:Rule val p = Paparazzi(deviceConfig = DeviceConfig.PIXEL_6.copy(nightMode = NightMode.NIGHT))
  private val lib = UiState(history = artistLib, trackInfo = r15Info, playlists = mapOf("Treino" to listOf("long:1", "long:2")))
  private val eq = EqSettings(on = true, bands = Eq.PRESETS["Rock"]!!, bass = 3f, preset = "Rock")
  @Test fun eqScreen() = p.snapshot { RiseTheme(true) { androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.background(androidx.compose.ui.graphics.Color(0xFF101012))) {
    EqContent(lib.copy(eq = eq, eqProfile = "bt:Galaxy Buds3 Pro"), Actions()) } } }
  @Test fun smartFolders() = p.snapshot { RiseTheme(true) { RiseApp(lib.copy(page = "Biblioteca"), Actions()) } }
  @Test fun smartFolderOpen() = p.snapshot { RiseTheme(true) { RiseApp(lib.copy(page = "Biblioteca"), Actions(), initialRoute = com.rise.musique.ui.Route.Smart("Clima", "Festa")) } }
  @Test fun artistFollow() = p.snapshot { RiseTheme(true) { RiseApp(lib.copy(page = "Biblioteca", followed = setOf("Artista Exemplo"), popular = mapOf("Artista Exemplo" to ytPopular)), Actions(),
    initialRoute = com.rise.musique.ui.Route.Artist("Artista Exemplo")) } }
  @Test fun playlistHeader() = p.snapshot { RiseTheme(true) { RiseApp(lib, Actions(), initialRoute = com.rise.musique.ui.Route.Playlist("Treino")) } }
}
class Round15Tall { @get:Rule val p = Paparazzi(deviceConfig = DeviceConfig.PIXEL_6.copy(nightMode = NightMode.NIGHT, screenHeight = 4200))
  @Test fun settings() = p.snapshot { RiseTheme(true) { androidx.compose.material3.Surface { SettingsContent(UiState(normalize = true, lockLyrics = true, wifiOnly = true), Actions()) } } }
}

private val songs = listOf(
  SearchResult("s1", "Instant Crush (feat. Julian Casablancas)", "Daft Punk e Julian Casablancas", 338, "", "song", "Random Access Memories", "Daft Punk e Julian Casablancas • Random Access Memories"),
  SearchResult("s2", "Da Funk", "Daft Punk", 329, "", "song", "Homework", "Daft Punk • Homework"),
  SearchResult("s3", "One More Time", "Daft Punk", 320, "", "song", "Discovery", "Daft Punk • Discovery"),
  SearchResult("s4", "Get Lucky (feat. Pharrell Williams & Nile Rodgers)", "Daft Punk", 369, "", "song", "Random Access Memories", "Daft Punk • Random Access Memories"))
private val albumsR = listOf(SearchResult("a1", "Discovery", "Daft Punk", 0, "", "album", "Discovery", "Álbum • Daft Punk • 2001"),
  SearchResult("a2", "Homework", "Daft Punk", 0, "", "album", "Homework", "Álbum • Daft Punk • 1997"),
  SearchResult("ar", "Daft Punk", "Daft Punk", 0, "", "artist", "", "78,2 mi ouvintes mensais"))
private val streamNp = NowPlaying("stream:s1", "Instant Crush (feat. Julian Casablancas)", "Daft Punk", "", playing = true, streaming = true,
  quality = "AAC · 129 kbps · 44.1 kHz · Estéreo", position = 94_000, duration = 338_000, hasNext = true, count = 4, index = 0)
private val vid = VideoUi("Daft Punk - Digital Love (Official Video)", "Daft Punk", "", playing = true, position = 61_000, duration = 302_000,
  qualities = listOf(240, 360, 720, 1080), quality = 1080, subtitles = listOf("pt (auto)"), subtitle = "pt (auto)", hasNext = true)
class Round16 { @get:Rule val p = Paparazzi(deviceConfig = DeviceConfig.PIXEL_6.copy(nightMode = NightMode.NIGHT))
  private val base = UiState(page = "Buscar", query = "daft punk", status = "")
  @Test fun searchFilters() = p.snapshot { RiseTheme(true) { RiseApp(base.copy(results = songs, durationFilter = DurationFilter.Medium, moreResults = true), Actions()) } }
  @Test fun searchAlbums() = p.snapshot { RiseTheme(true) { RiseApp(base.copy(results = albumsR, searchKind = SearchKind.Albums), Actions()) } }
  @Test fun searchSkeleton() = p.snapshot { RiseTheme(true) { RiseApp(base.copy(searching = true, searchKind = SearchKind.Videos, dateFilter = DateFilter.Week), Actions()) } }
  @Test fun voiceSearch() = p.snapshot { RiseTheme(true) { RiseApp(UiState(page = "Buscar", voice = VoiceUi(partial = "get lucky daft", level = .7f)), Actions()) } }
  @Test fun streamingPlayer() = p.snapshot { RiseTheme(true) { RiseApp(base.copy(results = songs, now = streamNp), Actions(), initialExpanded = true) } }
  @Test fun videoPortrait() = p.snapshot { RiseTheme(true) { RiseApp(base.copy(results = songs, video = vid), Actions()) } }
}
class Round16Land { @get:Rule val p = Paparazzi(deviceConfig = DeviceConfig.PIXEL_6.copy(screenWidth = 2400, screenHeight = 1080, orientation = ScreenOrientation.LANDSCAPE, nightMode = NightMode.NIGHT))
  @Test fun videoLandscape() = p.snapshot { RiseTheme(true) { RiseApp(UiState(video = vid.copy(fullscreen = true)), Actions()) } }
}

private val homeLib = (1..10).map { i -> HistoryItem(listOf("Noite clara", "Céu de abril", "Pé na estrada", "Mar aberto", "Luz do dia", "Vento sul", "Rio largo", "Fogo baixo", "Canto novo", "Sal")[i - 1],
  "M4A", "h:$i", artist = listOf("Marina Lima", "Tim Bernardes", "Céu", "Marina Lima", "Liniker")[i % 5], album = listOf("Fullgás", "Mil Coisas Invisíveis", "APKÁ!", "Fullgás", "Caju")[i % 5], duration = 210_000) }
private val homeInfo = homeLib.associate { it.uri to TrackInfo(if (it.uri.last().digitToInt() % 2 == 0) -9.0 else -16.0, bpm = if (it.uri.last().digitToInt() % 2 == 0) 128.0 else 82.0) }
private val homeState = UiState(history = homeLib, trackInfo = homeInfo, playlists = mapOf("Treino" to listOf("h:1", "h:2")),
  stats = StatsSummary(topTracks = listOf(Ranked("Céu de abril", "Tim Bernardes", 12, 1)), topArtists = listOf(Ranked("Marina Lima", "", 20, 1), Ranked("Céu", "", 9, 1))))
class Round17 { @get:Rule val p = Paparazzi(deviceConfig = DeviceConfig.PIXEL_6.copy(nightMode = NightMode.NIGHT))
  @Test fun home() = p.snapshot { RiseTheme(true) { RiseApp(homeState, Actions()) } }
  @Test fun homeScrolled() = p.snapshot { RiseTheme(true) { RiseApp(homeState, Actions(), initialRootItem = 3) } }
  @Test fun libraryFloatingBar() = p.snapshot { RiseTheme(true) { RiseApp(homeState.copy(page = "Biblioteca", now = streamNp), Actions()) } }
  @Test fun playerPixel6() = p.snapshot { RiseTheme(true) { RiseApp(homeState.copy(now = streamNp), Actions(), initialExpanded = true) } }
}
class Round17Light { @get:Rule val p = Paparazzi(deviceConfig = DeviceConfig.PIXEL_6)
  @Test fun home() = p.snapshot { RiseTheme(false) { RiseApp(homeState, Actions()) } } }
class Round17Tall { @get:Rule val p = Paparazzi(deviceConfig = DeviceConfig.PIXEL_6.copy(nightMode = NightMode.NIGHT, screenHeight = 4300))
  @Test fun homeFull() = p.snapshot { RiseTheme(true) { RiseApp(homeState, Actions()) } } }
class Round17Small { @get:Rule val p = Paparazzi(deviceConfig = DeviceConfig.PIXEL_6.copy(screenWidth = 720, screenHeight = 1280, density = Density.XHIGH, xdpi = 320, ydpi = 320, nightMode = NightMode.NIGHT))
  @Test fun player360x640() = p.snapshot { RiseTheme(true) { RiseApp(homeState.copy(now = streamNp), Actions(), initialExpanded = true) } } }
class Round17Long { @get:Rule val p = Paparazzi(deviceConfig = DeviceConfig.PIXEL_6.copy(screenHeight = 2640, nightMode = NightMode.NIGHT))
  @Test fun playerTall() = p.snapshot { RiseTheme(true) { RiseApp(homeState.copy(now = streamNp), Actions(), initialExpanded = true) } } }

private val dlQueue = listOf(QueueItem("q1", "Get Lucky (feat. Pharrell Williams & Nile Rodgers)", .42f, true, stage = "Baixando"),
  QueueItem("q2", "One More Time", 0f, false), QueueItem("q3", "Around the World", 1f, false, completed = true, stage = "Concluído"))
private val npLyrics = streamNp.copy(streaming = false, key = "h:1", lyrics = listOf(com.rise.musique.Lyrics.Line(90_000, "She's up all night 'til the sun"),
  com.rise.musique.Lyrics.Line(95_000, "I'm up all night to get some"), com.rise.musique.Lyrics.Line(100_000, "She's up all night for good fun"), com.rise.musique.Lyrics.Line(105_000, "I'm up all night to get lucky")))
class Round18 { @get:Rule val p = Paparazzi(deviceConfig = DeviceConfig.PIXEL_6.copy(nightMode = NightMode.NIGHT))
  @Test fun searchEmpty() = p.snapshot { RiseTheme(true) { RiseApp(UiState(page = "Buscar", recentSearches = listOf("daft punk", "marina lima", "lofi")), Actions()) } }
  @Test fun searchResults() = p.snapshot { RiseTheme(true) { RiseApp(UiState(page = "Buscar", query = "daft punk", results = songs + songs.map { it.copy(url = it.url + "v", kind = "video") } + albumsR), Actions()) } }
  @Test fun searchScrolled() = p.snapshot { RiseTheme(true) { RiseApp(UiState(page = "Buscar", query = "daft punk", results = songs + songs.map { it.copy(url = it.url + "v", kind = "video") } + albumsR, now = streamNp), Actions(), initialRootItem = 3) } }
  @Test fun downloadPage() = p.snapshot { RiseTheme(true) { RiseApp(UiState(page = "Buscar", dlItems = listOf(songs[0]), queue = dlQueue, history = homeLib.take(3)), Actions(), initialRoute = Route.Downloads) } }
  @Test fun shareCard() = p.snapshot { RiseTheme(true) { androidx.compose.foundation.Image(com.rise.musique.Snippet.card(npLyrics.title, npLyrics.artist, null,
    com.rise.musique.Snippet.linesIn(npLyrics.lyrics, 94_000).map { it.text }, 1).let(::bmpToImage), null, androidx.compose.ui.Modifier.fillMaxSize()) } }
  @Test fun shareSheet() = p.snapshot { RiseTheme(true) { androidx.compose.material3.Surface { androidx.compose.foundation.layout.Column(androidx.compose.ui.Modifier.padding(top = 24.dp)) { ShareContent(npLyrics, true, false, Actions(), null, 94_000) {} } } } }
}
class Round18Light { @get:Rule val p = Paparazzi(deviceConfig = DeviceConfig.PIXEL_6)
  @Test fun downloadPage() = p.snapshot { RiseTheme(false) { RiseApp(UiState(page = "Buscar", dlItems = listOf(songs[1]), queue = dlQueue), Actions(), initialRoute = Route.Downloads) } } }
private fun bmpToImage(b: android.graphics.Bitmap): androidx.compose.ui.graphics.ImageBitmap = b.asImageBitmap()

private val sampleCrash = com.rise.musique.ui.CrashReport.build("0.19.0", 22, "Samsung", "SM-S918B", "14", 34, "main", 1_760_000_000_000,
  RuntimeException("Falha ao abrir", IllegalStateException("An instance of SeekableTransitionState has been used in different Transitions")))
@Composable private fun IconTile(shape: androidx.compose.ui.graphics.Shape, mono: Boolean = false) {
  Box(Modifier.size(110.dp).clip(shape).background(if (mono) androidx.compose.ui.graphics.Color(0xFFDDE3F0) else androidx.compose.ui.graphics.Color(0xFF111113)), contentAlignment = androidx.compose.ui.Alignment.Center) {
    // adaptive icon: 108dp layer, outer 18dp cropped by the mask → scale 110/72
    androidx.compose.foundation.Image(androidx.compose.ui.res.painterResource(if (mono) com.rise.musique.R.drawable.ic_launcher_monochrome else com.rise.musique.R.drawable.ic_launcher_foreground), null,
      Modifier.requiredSize(165.dp), colorFilter = if (mono) androidx.compose.ui.graphics.ColorFilter.tint(androidx.compose.ui.graphics.Color(0xFF2B3A55)) else null)
  }
}
class Round19 { @get:Rule val p = Paparazzi(deviceConfig = DeviceConfig.PIXEL_6.copy(nightMode = NightMode.NIGHT))
  @Test fun launcherIcons() = p.snapshot { RiseTheme(true) { Column(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color(0xFF3A4A5E)).padding(32.dp), verticalArrangement = Arrangement.spacedBy(28.dp)) {
    Text("Ícone (círculo / squircle / quadrado arredondado)", color = androidx.compose.ui.graphics.Color.White)
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) { IconTile(androidx.compose.foundation.shape.CircleShape); IconTile(androidx.compose.foundation.shape.RoundedCornerShape(40.dp)); IconTile(androidx.compose.foundation.shape.RoundedCornerShape(24.dp)) }
    Text("Ícone temático (monocromático)", color = androidx.compose.ui.graphics.Color.White)
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) { IconTile(androidx.compose.foundation.shape.CircleShape, true); IconTile(androidx.compose.foundation.shape.RoundedCornerShape(40.dp), true) }
    Text("Logo da barra superior (referência)", color = androidx.compose.ui.graphics.Color.White)
    Text("rise", style = androidx.compose.material3.MaterialTheme.typography.titleLarge, letterSpacing = (-1).sp, color = androidx.compose.ui.graphics.Color.White)
  } } }
  @Test fun splash() = p.snapshot { Box(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color.Black), contentAlignment = androidx.compose.ui.Alignment.Center) {
    // Android 12+ splash: 240dp icon area, 160dp visible circle
    Box(Modifier.size(160.dp).clip(androidx.compose.foundation.shape.CircleShape), contentAlignment = androidx.compose.ui.Alignment.Center) {
      androidx.compose.foundation.Image(androidx.compose.ui.res.painterResource(com.rise.musique.R.drawable.ic_launcher_foreground), null, Modifier.requiredSize(240.dp)) } } }
  @Test fun about() = p.snapshot { RiseTheme(true) { androidx.compose.material3.Surface { AboutContent("0.19.0", java.io.File("../RELEASE_NOTES.md").takeIf { it.exists() }?.readText() ?: java.io.File("RELEASE_NOTES.md").readText(), {}) {} } } }
  @Test fun crashDialog() = p.snapshot { RiseTheme(true) { Box(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color(0xFF0B0B0D)).padding(24.dp), contentAlignment = androidx.compose.ui.Alignment.Center) {
    CrashCard(sampleCrash, {}, {}, {}) } } }
}

// ---- 0.20.0 ----
private fun res(n: String) = java.io.File(listOf("src/test/resources/$n", "app/src/test/resources/$n").first { java.io.File(it).exists() }).absolutePath
private fun song(i: Int, t: String, alb: String = "Portas") = SearchResult("https://music.youtube.com/watch?v=v$i", t, "Marisa Monte", 160 + i * 17, res("cover.jpg"), "song", alb)
private val artistPage = init.let { ArtistPage("UC1", "Marisa Monte", res("hero.jpg"), "Marisa de Azevedo Monte é uma cantora, compositora e musicista brasileira, conhecida pela entrega vocal e por canções de MPB influenciadas pelo pop. Alcançou a fama em 1989 com o álbum ao vivo MM.",
  "4,2 mi ouvintes mensais",
  top = listOf("Vilarejo", "Amor I Love You", "Ainda Bem", "Beija Eu", "Depois").mapIndexed { i, t -> song(i, t) },
  albums = listOf("Portas", "O Que Você Quer Saber de Verdade", "Infinito Particular").mapIndexed { i, t -> SearchResult("https://music.youtube.com/browse/MPREb_$i", t, "Marisa Monte", 0, res("cover.jpg"), "album", t, listOf("2022", "2011", "2006")[i]) },
  singles = listOf(SearchResult("https://music.youtube.com/browse/MPREb_s", "Calma", "Marisa Monte", 0, res("cover.jpg"), "album", "Calma", "Single • 2021")),
  videos = listOf(SearchResult("https://music.youtube.com/watch?v=vid1", "Portas (Clipe Oficial)", "Marisa Monte", 0, res("cover.jpg"), "video", "", "Marisa Monte • 2,1 mi visualizações")),
  similar = listOf("Adriana Calcanhotto", "Arnaldo Antunes", "Gal Costa").map { SearchResult("https://music.youtube.com/channel/UC$it", it, it, 0, res("hero.jpg"), "artist") }) }
private val artistState = init.let { UiState(history = listOf(HistoryItem("Bem Que Se Quis", "FLAC", "l:1", artist = "Marisa Monte", album = "MM", duration = 260_000)),
  artistPages = mapOf("Marisa Monte" to artistPage)) }
@Composable private fun SyncImages() { val ctx = androidx.compose.ui.platform.LocalContext.current
  androidx.compose.runtime.remember { coil.Coil.setImageLoader(coil.ImageLoader.Builder(ctx).dispatcher(kotlinx.coroutines.Dispatchers.Unconfined).allowHardware(false).build()); true } }
class Round20 { @get:Rule val p = Paparazzi(deviceConfig = DeviceConfig.PIXEL_6.copy(nightMode = NightMode.NIGHT))
  @Test fun artistTop() = p.snapshot { SyncImages(); RiseTheme(true) { RiseApp(artistState, Actions(), initialRoute = Route.Artist("Marisa Monte")) } }
  @Test fun artistScrolled() = p.snapshot { SyncImages(); RiseTheme(true) { RiseApp(artistState, Actions(), initialRoute = Route.Artist("Marisa Monte"), initialCollectionItem = 4) } }
  @Test fun albumFromArtist() = p.snapshot { SyncImages(); RiseTheme(true) { RiseApp(artistState.copy(albumPages = mapOf("MPREb_0" to AlbumPage("MPREb_0", "Portas", "Marisa Monte", "2022", res("cover.jpg"),
    listOf("Portas", "Calma", "Déjà Vu", "Praia Vermelha", "Medo do Perigo", "Pra Melhorar").mapIndexed { i, t -> song(i, t) }))), Actions(),
    initialRoute = Route.RemoteAlbum("MPREb_0", "Portas", res("cover.jpg"), "Marisa Monte")) } }
  @Test fun dsdQuality() = p.snapshot { SyncImages(); RiseTheme(true) { RiseApp(UiState(now = np.copy(quality = AudioQuality.label(HiRes.DSD_LSBF_PLANAR, 0, 352_800, 2), title = "Bem Que Se Quis", artist = "Marisa Monte")), Actions(), initialExpanded = true) } }
  @Test fun playerMidExpansion() = p.snapshot { SyncImages(); RiseTheme(true) { RiseApp(homeState.copy(now = np), Actions(), initialPlayerFraction = .5f) } }
}

// ---- 0.20.1: liquid glass over busy content ----
class Round201 { @get:Rule val p = Paparazzi(deviceConfig = DeviceConfig.PIXEL_6.copy(nightMode = NightMode.NIGHT))
  private val busy = homeState.copy(history = homeLib.mapIndexed { i, h -> h.copy(art = res(if (i % 2 == 0) "cover.jpg" else "hero.jpg")) }, now = np.copy(art = res("cover.jpg")))
  @Test fun glassBarOverCovers() = p.snapshot { SyncImages(); RiseTheme(true) { RiseApp(busy, Actions(), initialRootItem = 2) } }
  @Test fun glassBarLight() = p.snapshot { SyncImages(); RiseTheme(false) { RiseApp(busy, Actions(), initialRootItem = 2) } }
}

// ---- 0.22: fila, Feito pra você, ajustes em seções, histórico ----
class Round22 { @get:Rule val p = Paparazzi(deviceConfig = DeviceConfig.PIXEL_6.copy(nightMode = NightMode.NIGHT))
  private val covered = homeLib.mapIndexed { i, h -> h.copy(art = res(if (i % 2 == 0) "cover.jpg" else "hero.jpg")) }
  private val q = UiState(now = np.copy(index = 0), playbackQueue = covered.take(7), smartQueue = true)
  @Test fun queue() = p.snapshot { SyncImages(); RiseTheme(true) { androidx.compose.material3.Surface { QueueContent(q, Actions()) } } }
  @Test fun queueUndo() = p.snapshot { SyncImages(); RiseTheme(true) { androidx.compose.material3.Surface { Box { QueueContent(q.copy(undo = UndoInfo(1, "Removida da fila")), Actions())
    UndoBar(q.copy(undo = UndoInfo(1, "Removida da fila")), Actions(), Modifier.align(androidx.compose.ui.Alignment.BottomCenter), bottom = 12.dp) } } } }
  @Test fun forYou() = p.snapshot { SyncImages(); RiseTheme(true) { RiseApp(homeState.copy(history = covered,
    forYou = ForYou.build(covered, covered.take(4).map { it.uri }.toSet(), emptyMap(), 1)), Actions()) } }
  @Test fun settingsRoot() = p.snapshot { RiseTheme(true) { androidx.compose.material3.Surface { SettingsContent(UiState(), Actions()) } } }
  @Test fun settingsPlayback() = p.snapshot { RiseTheme(true) { androidx.compose.material3.Surface { SettingsContent(UiState(crossfade = 6, smartQueue = true), Actions(), initialSection = "Reprodução") } } }
  @Test fun settingsSearch() = p.snapshot { RiseTheme(true) { androidx.compose.material3.Surface { SettingsContent(UiState(), Actions(), initialQuery = "qualidade") } } }
  @Test fun history() = p.snapshot { SyncImages(); RiseTheme(true) { val now = System.currentTimeMillis()
    RiseApp(homeState.copy(history = covered, plays = covered.take(6).mapIndexed { i, h -> Play(h.uri, h.title, h.artist, 120_000, now - i * 9 * 3_600_000L) }), Actions(), initialRoute = Route.History) } }
  @Test fun streamMenu() = p.snapshot { SyncImages(); RiseTheme(true) { androidx.compose.material3.Surface { StreamActionContent("Da Funk", "Daft Punk", res("cover.jpg"), {}, {}, {}, {}, {}) } } }
}

class Round23 { @get:Rule val p = Paparazzi(deviceConfig = DeviceConfig.PIXEL_6.copy(nightMode = NightMode.NIGHT))
  private val covered = homeLib.mapIndexed { i, h -> h.copy(art = res(if (i % 2 == 0) "cover.jpg" else "hero.jpg")) }
  private val lib = LibraryData(
    tracks = (covered.map { LibTrack(it.uri, it.title, it.artist, it.album, it.duration, it.art) } +
      LibTrack("rise-stream://dQw4w9WgXcQ", "Faixa online", "Artista online", art = res("cover.jpg"), videoId = "dQw4w9WgXcQ", source = LibSource.YOUTUBE)).associateBy { it.key },
    likes = (listOf("rise-stream://dQw4w9WgXcQ") + covered.take(3).map { it.uri }).map { LibLike(it, 0) },
    playlists = listOf(LibPlaylist("a", "Treino", covered.take(5).map { it.uri } + "rise-stream://dQw4w9WgXcQ"),
      LibPlaylist("b", "Domingo", covered.drop(2).take(1).map { it.uri }), LibPlaylist("c", "Do Spotify", emptyList(), source = LibSource.SPOTIFY)),
    artists = listOf(LibArtist("Marisa Monte")), albums = listOf(LibAlbum("MPREb_0", "Portas", "Marisa Monte", res("cover.jpg"), source = LibSource.YOUTUBE)))
  private fun st(tab: String) = homeState.copy(history = covered, library = lib, favorites = lib.likedKeys, playlists = lib.playlists.associate { it.name to it.tracks },
    page = "Biblioteca", libraryTab = tab, followed = setOf("Marisa Monte"))
  @Test fun tabColecao() = p.snapshot { SyncImages(); RiseTheme(true) { RiseApp(st("Coleção"), Actions()) } }
  @Test fun tabCurtidas() = p.snapshot { SyncImages(); RiseTheme(true) { RiseApp(st("Curtidas"), Actions()) } }
  @Test fun tabPlaylists() = p.snapshot { SyncImages(); RiseTheme(true) { RiseApp(st("Playlists"), Actions()) } }
  @Test fun tabAlbuns() = p.snapshot { SyncImages(); RiseTheme(true) { RiseApp(st("Álbuns"), Actions()) } }
  @Test fun tabArtistas() = p.snapshot { SyncImages(); RiseTheme(true) { RiseApp(st("Artistas"), Actions()) } }
  @Test fun playlistEditor() = p.snapshot { SyncImages(); RiseTheme(true) { RiseApp(st("Playlists"), Actions(), initialRoute = Route.Playlist("Treino")) } }
  @Test fun playlistEditorScrolled() = p.snapshot { SyncImages(); RiseTheme(true) { RiseApp(st("Playlists"), Actions(), initialRoute = Route.Playlist("Treino"), initialCollectionItem = 1) } }
  @Test fun streamMenuLike() = p.snapshot { SyncImages(); RiseTheme(true) { androidx.compose.material3.Surface { StreamActionContent("Da Funk", "Daft Punk", res("cover.jpg"), {}, {}, {}, {}, {}, {}, true, {}) } } }
  @Test fun settingsIntegrations() = p.snapshot { RiseTheme(true) { androidx.compose.material3.Surface { SettingsContent(UiState(), Actions(), initialSection = "Integrações") } } }
}

class Round24 { @get:Rule val p = Paparazzi(deviceConfig = DeviceConfig.PIXEL_6.copy(nightMode = NightMode.NIGHT))
  @Test fun aboutExportLog() = p.snapshot { RiseTheme(true) { androidx.compose.material3.Surface { AboutContent("0.24.0", "- Registro de erro exportável", {}, {}) {} } } }
  @Test fun searchOffline() = p.snapshot { RiseTheme(true) { RiseApp(UiState(page = "Buscar", query = "Marisa Monte",
    status = SearchErrorPrefix + "Sem conexão com a internet. Confira o Wi-Fi ou os dados móveis."), Actions()) } }
  @Test fun skeleton() = p.snapshot { RiseTheme(true) { androidx.compose.material3.Surface { SkeletonRows("Carregando as faixas…") } } }
}

class Round25 { @get:Rule val p = Paparazzi(deviceConfig = DeviceConfig.PIXEL_6.copy(nightMode = NightMode.NIGHT))
  @Test fun playerLiked() = p.snapshot { RiseTheme(true) { RiseApp(found.copy(now = np, favorites = setOf("x")), Actions(), initialExpanded = true) } }
  @Test fun playerPaused() = p.snapshot { RiseTheme(true) { RiseApp(found.copy(now = np.copy(playing = false)), Actions(), initialExpanded = true) } }
  @Test fun settingsAppearance() = p.snapshot { RiseTheme(true) { androidx.compose.material3.Surface { SettingsContent(UiState(), Actions(), initialSection = "Aparência") } } }
  @Test fun microDetails() = p.snapshot { RiseTheme(true) { androidx.compose.material3.Surface { Row(Modifier.padding(24.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
    LikeButton(true, {}); LikeButton(false, {}); PlayPauseMorph(true, androidx.compose.ui.graphics.Color.White, Modifier.size(40.dp))
    PlayPauseMorph(false, androidx.compose.ui.graphics.Color.White, Modifier.size(40.dp)); RollingText("1:14", androidx.compose.ui.graphics.Color.White, 16.sp) } } } }
}

/** 0.27: vidro novo (dock + mini com o mesmo vidro), ícones Phosphor, listas rolando por trás do vidro. */
class Round27 { @get:Rule val p = Paparazzi(deviceConfig = DeviceConfig.PIXEL_6.copy(nightMode = NightMode.NIGHT))
  private val busy = homeState.copy(history = homeLib.mapIndexed { i, h -> h.copy(art = res(if (i % 2 == 0) "cover.jpg" else "hero.jpg")) })
  private val withCover = np.copy(art = res("cover.jpg"), title = "Bem Que Se Quis", artist = "Marisa Monte")
  @Test fun dockMiniDark() = p.snapshot { SyncImages(); RiseTheme(true) { RiseApp(busy.copy(now = withCover), Actions(), initialRootItem = 2) } }
  @Test fun dockMiniLight() = p.snapshot { SyncImages(); RiseTheme(false) { RiseApp(busy.copy(now = withCover), Actions(), initialRootItem = 2) } }
  @Test fun dockOnlySearch() = p.snapshot { SyncImages(); RiseTheme(true) { RiseApp(found, Actions()) } }
  @Test fun playerControls() = p.snapshot { SyncImages(); RiseTheme(true) { RiseApp(busy.copy(now = withCover.copy(shuffle = true, repeat = 1)), Actions(), initialExpanded = true) } }
}

/** 0.28: vidro/dock/mini do Square. Paparazzi não roda RenderEffect/RuntimeShader → mostra o fallback translúcido (filme + aro). */
class Round28 { @get:Rule val p = Paparazzi(deviceConfig = DeviceConfig.PIXEL_6.copy(nightMode = NightMode.NIGHT))
  private val busy = homeState.copy(history = homeLib.mapIndexed { i, h -> h.copy(art = res(if (i % 2 == 0) "cover.jpg" else "hero.jpg")) })
  private val withCover = np.copy(art = res("cover.jpg"), title = "Bem Que Se Quis", artist = "Marisa Monte", position = 70_000, duration = 200_000)
  @Test fun dockMiniDark() = p.snapshot { SyncImages(); RiseTheme(true) { RiseApp(busy.copy(now = withCover), Actions(), initialRootItem = 2) } }
  @Test fun dockMiniLight() = p.snapshot { SyncImages(); RiseTheme(false) { RiseApp(busy.copy(now = withCover), Actions(), initialRootItem = 2) } }
  @Test fun dockOnlyLibrary() = p.snapshot { SyncImages(); RiseTheme(true) { RiseApp(busy, Actions(), initialRootItem = 1) } }
  @Test fun playerLiked() = p.snapshot { SyncImages(); RiseTheme(true) { RiseApp(busy.copy(now = withCover, favorites = setOf(withCover.key)), Actions(), initialExpanded = true) } }
  /** Dock + mini isolados sobre uma capa (no RiseApp a dock só entra no 2º quadro e o Paparazzi grava o 1º). */
  @Composable private fun Chrome(dark: Boolean, tab: String) = RiseTheme(dark) { Box(Modifier.fillMaxSize().background(androidx.compose.material3.MaterialTheme.colorScheme.background)) {
    coil.compose.AsyncImage(res("hero.jpg"), null, Modifier.fillMaxSize(), contentScale = androidx.compose.ui.layout.ContentScale.Crop)
    PlayerSheet(withCover.copy(playing = true), Actions())
    NavigationDock(tab, Actions(), Modifier.align(androidx.compose.ui.Alignment.BottomCenter).fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) } }
  @Test fun chromeDark() = p.snapshot { SyncImages(); Chrome(true, "Início") }
  @Test fun chromeLight() = p.snapshot { SyncImages(); Chrome(false, "Buscar") }
}
