package com.rise.musique
import app.cash.paparazzi.*
import com.android.resources.*
import com.rise.musique.ui.*
import org.junit.*
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
  @Test fun album() = p.snapshot { RiseTheme(true) { RiseApp(UiState(history = albumHistory, albumFilter = "Disco de Exemplo"), Actions()) } }
  @Test fun playlistWithMini() = p.snapshot { RiseTheme(true) { RiseApp(UiState(history = albumHistory, playlists = mapOf("Treino" to listOf("album:2", "album:0")),
    historyView = "Treino", now = np), Actions()) } } }
