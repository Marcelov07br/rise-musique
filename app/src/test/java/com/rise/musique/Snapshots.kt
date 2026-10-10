package com.rise.musique
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
class Library { @get:Rule val p = Paparazzi(deviceConfig = DeviceConfig.PIXEL_6.copy(nightMode = NightMode.NIGHT))
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
