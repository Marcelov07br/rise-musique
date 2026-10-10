package com.rise.musique
import android.Manifest
import android.content.*
import android.os.*
import androidx.activity.ComponentActivity
import androidx.activity.compose.*
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.*
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.rise.musique.ui.*
class MainActivity : ComponentActivity() {
  private val vm: MainViewModel by viewModels()
  private val updates: UpdatesViewModel by viewModels()
  private val installPermission = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
    if (packageManager.canRequestPackageInstalls()) installUpdate()
  }
  private fun installUpdate() {
    val apk = updates.ready ?: return
    runCatching {
      if (packageManager.canRequestPackageInstalls()) startActivity(Updates.installIntent(this, apk))
      else installPermission.launch(Updates.permissionIntent(this))
    }.onFailure { updates.installError(it as? Exception ?: Exception(it)) }
  }
  override fun onResume() { super.onResume(); updates.check() }
  override fun onCreate(s: Bundle?) { installSplashScreen(); enableEdgeToEdge(); super.onCreate(s)
    if (s == null) handle(intent)
    Shortcuts.publish(this)
    if (Build.VERSION.SDK_INT >= 33) registerForActivityResult(ActivityResultContracts.RequestPermission()) {}
      .launch(Manifest.permission.POST_NOTIFICATIONS)
    setContent { val sys = isSystemInDarkTheme(); val dark = when (vm.state.theme) { "Escuro" -> true; "Claro" -> false; else -> sys }
      SideEffect { val bars = if (dark) androidx.activity.SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        else androidx.activity.SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
        enableEdgeToEdge(statusBarStyle = bars, navigationBarStyle = bars) }
      val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree(), vm::onFolder)
      val mediaPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments(), vm::importFiles)
      val mediaFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree(), vm::importFolder)
      val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json"), vm::exportBackup)
      val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument(), vm::importBackup)
      var crash by remember { mutableStateOf(java.io.File(filesDir, "crash.txt").takeIf { it.exists() }?.readText()) }
      SideEffect { updatePip() }
      RiseTheme(dark) {
        UpdateDialog(updates, ::installUpdate)
        crash?.let { log -> androidx.compose.material3.AlertDialog(onDismissRequest = { java.io.File(filesDir, "crash.txt").delete(); crash = null },
          title = { androidx.compose.material3.Text("O app fechou sozinho da última vez") },
          text = { androidx.compose.material3.Text("Compartilhe o relatório com o Dev Compose pra ele achar a causa.\n\n" + log.lines().take(6).joinToString("\n"), fontSize = androidx.compose.ui.unit.TextUnit(12f, androidx.compose.ui.unit.TextUnitType.Sp)) },
          confirmButton = { androidx.compose.material3.TextButton({ startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, log), null))
            java.io.File(filesDir, "crash.txt").delete(); crash = null }) { androidx.compose.material3.Text("Compartilhar") } },
          dismissButton = { androidx.compose.material3.TextButton({ java.io.File(filesDir, "crash.txt").delete(); crash = null }) { androidx.compose.material3.Text("Ignorar") } }) }
        RiseApp(vm.state, Actions(vm::onQuery, vm::onMode, vm::onQuality, { picker.launch(null) },
        vm::enqueue, { vm.theme(if (dark) "Claro" else "Escuro") }, { vm.search() }, vm::select, vm::togglePreview,
        selectAll = vm::selectAll, format = vm::onFormat, cut = vm::onCut, cancel = vm::cancel, retry = vm::retry, dismiss = vm::dismiss,
        open = { h -> vm.openIntent(h, false)?.let(::startActivity) }, share = { h -> vm.openIntent(h, true)?.let(::startActivity) },
        delete = vm::delete, play = vm::play, playPause = vm::playPause, seek = vm::seek, closePlayer = vm::closePlayer, next = vm::next, prev = vm::prev, shuffle = vm::shuffle,
        favorite = vm::favorite, addToPlaylist = vm::addToPlaylist, removeFromPlaylist = vm::removeFromPlaylist, deletePlaylist = vm::deletePlaylist,
        historyFilter = vm::historyFilter, historyView = vm::historyView, sleep = vm::sleep,
        exportBackup = { exporter.launch("rise-musique-backup.json") }, importBackup = { importer.launch(arrayOf("application/json", "*/*")) },
        updates = { updates.check(true) }, page = vm::page, repeat = vm::repeat, showQueue = vm::showQueue,
        moveQueue = vm::moveQueue, removeQueue = vm::removeQueue, playQueue = vm::playQueue, playNext = vm::playNext, addQueue = vm::addQueue,
        importFiles = { mediaPicker.launch(arrayOf("audio/*", "video/*")) }, importFolder = { mediaFolder.launch(null) },
        moreResults = vm::moreResults, clearSearches = vm::clearSearches, sort = vm::sort, mediaFilter = vm::mediaFilter, albumFilter = vm::albumFilter,
        renamePlaylist = vm::renamePlaylist, movePlaylist = vm::movePlaylist, refreshMetadata = vm::refreshMetadata,
        theme = vm::theme, settings = vm::settings, removeLocal = vm::removeLocal, crossfade = vm::crossfade, stats = vm::stats, playShuffled = vm::playShuffled, queueAll = vm::queueAll,
        radio = vm::radio, radioFor = vm::radioFor, automix = vm::automix, lastfmLogin = vm::lastfmLogin, lastfmLogout = vm::lastfmLogout,
        lastfmToggle = vm::lastfmToggle, openCut = vm::openCut, eqOpen = vm::eqOpen, eqChange = vm::eqChange, normalize = vm::normalize, normalizeAlbum = vm::normalizeAlbum,
        lockLyrics = vm::lockLyrics, wifiOnly = vm::wifiOnly, follow = vm::follow, undo = vm::undo, removeSearch = vm::removeSearch, searchLive = vm::searchLive, clearQueue = vm::clearQueue,
        searchKind = vm::searchKind, durationFilter = vm::durationFilter, dateFilter = vm::dateFilter, openResult = vm::openResult, saveStream = vm::saveStream,
        playVideo = vm::playVideo, playStation = vm::playStation, shareSnippet = { v, ms -> vm.shareSnippet(v, ms) { startActivity(it) } }, downloadItem = vm::downloadItem, dlRemove = vm::dlRemove, dlQuery = vm::dlQuery, dlLink = vm::dlLink, playLocalVideo = vm::playLocalVideo, voiceStart = { voice() }, voiceStop = { vm.voice.stop() },
        video = com.rise.musique.ui.VideoActions(close = { vm.video.close(); exitFullscreen() }, playPause = vm.video::playPause, seek = vm.video::seek, seekBy = vm.video::seekBy,
          quality = vm.video::quality, subtitle = vm.video::subtitle, fullscreen = { on -> vm.video.fullscreen(on); if (on) enterFullscreen() else exitFullscreen() },
          next = vm.video::next, pip = { enterPip() }),
        artistPopular = vm::artistPopular, prepareDownload = vm::prepareDownload, removeFromList = vm::removeFromList, previewRange = vm::previewRange, recap = vm::recap, shareRecap = { p -> vm.recapIntent(p)?.let(::startActivity) })) } }
  }
  // ---- voice ----
  private val micPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
    if (ok) vm.voice.start() else vm.voiceDenied() }
  private fun voice() { if (androidx.core.content.ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == android.content.pm.PackageManager.PERMISSION_GRANTED)
    vm.voice.start() else micPermission.launch(Manifest.permission.RECORD_AUDIO) }

  // ---- video: full screen + picture-in-picture ----
  private fun bars(show: Boolean) = androidx.core.view.WindowCompat.getInsetsController(window, window.decorView).run {
    systemBarsBehavior = androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    if (show) show(androidx.core.view.WindowInsetsCompat.Type.systemBars()) else hide(androidx.core.view.WindowInsetsCompat.Type.systemBars()) }
  private fun enterFullscreen() { requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE; bars(false) }
  private fun exitFullscreen() { requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED; bars(true)
    window.attributes = window.attributes.apply { screenBrightness = android.view.WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE } }
  private val pipReceiver = object : android.content.BroadcastReceiver() {
    override fun onReceive(c: android.content.Context, i: Intent) { when (i.getStringExtra("cmd")) { "toggle" -> vm.video.playPause(); "next" -> vm.video.next() } } }
  private var pipRegistered = false
  private fun pipParams(): android.app.PictureInPictureParams? {
    if (Build.VERSION.SDK_INT < 26) return null
    val v = vm.state.video
    val b = android.app.PictureInPictureParams.Builder()
    val r = PipMath.ratio(v?.aspect ?: (16f / 9f)); b.setAspectRatio(android.util.Rational(r.first, r.second))
    fun act(cmd: String, icon: Int, title: String, code: Int) = android.app.RemoteAction(android.graphics.drawable.Icon.createWithResource(this, icon), title, title,
      android.app.PendingIntent.getBroadcast(this, code, Intent(PIP_ACTION).setPackage(packageName).putExtra("cmd", cmd), android.app.PendingIntent.FLAG_IMMUTABLE))
    if (v != null) b.setActions(listOfNotNull(act("toggle", if (v.playing) R.drawable.ic_w_pause else R.drawable.ic_w_play, if (v.playing) "Pausar" else "Tocar", 1),
      if (v.hasNext) act("next", R.drawable.ic_w_next, "Próximo", 2) else null))
    if (Build.VERSION.SDK_INT >= 31) b.setAutoEnterEnabled(v != null && v.playing).setSeamlessResizeEnabled(true)
    return b.build()
  }
  private var lastPip: Triple<Boolean, Boolean, Float>? = null
  private fun updatePip() { if (Build.VERSION.SDK_INT < 26) return
    val v = vm.state.video; val key = Triple(v != null, v?.playing == true, v?.aspect ?: 0f); if (key == lastPip) return; lastPip = key
    if (v != null && !pipRegistered) { androidx.core.content.ContextCompat.registerReceiver(this, pipReceiver, android.content.IntentFilter(PIP_ACTION),
      androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED); pipRegistered = true }
    runCatching { pipParams()?.let(::setPictureInPictureParams) } }
  private fun enterPip() { if (Build.VERSION.SDK_INT >= 26 && vm.state.video != null) runCatching { pipParams()?.let(::enterPictureInPictureMode) } }
  override fun onUserLeaveHint() { super.onUserLeaveHint(); if (Build.VERSION.SDK_INT in 26..30 && vm.state.video?.playing == true) enterPip() }
  override fun onPictureInPictureModeChanged(inPip: Boolean, cfg: android.content.res.Configuration) { super.onPictureInPictureModeChanged(inPip, cfg)
    vm.pip(inPip); if (!inPip && !lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED)) vm.video.pause() }
  override fun onStop() { super.onStop(); if (!isInPictureInPictureMode) vm.video.pause() }
  override fun onDestroy() { if (pipRegistered) runCatching { unregisterReceiver(pipReceiver) }; super.onDestroy() }

  override fun onNewIntent(i: Intent) { super.onNewIntent(i); handle(i) }
  private fun handle(i: Intent?) { when (i?.action) {
    Intent.ACTION_SEND -> vm.onIncomingLink(i.getStringExtra(Intent.EXTRA_TEXT))
    Shortcuts.SHUFFLE -> { vm.page("Início"); vm.shuffleAll() }
    Shortcuts.STATIONS -> vm.page("Início")
    Shortcuts.SEARCH -> vm.page("Buscar")
    Shortcuts.DOWNLOADS -> vm.openDownloads()
  } }
  override fun onWindowFocusChanged(f: Boolean) { super.onWindowFocusChanged(f)
    if (!f || vm.state.query.isNotBlank()) return
    val t = getSystemService(ClipboardManager::class.java).primaryClip?.getItemAt(0)?.text?.toString() ?: return
    if (Regex("(youtube\\.com|youtu\\.be|music\\.youtube)").containsMatchIn(t)) vm.onIncomingLink(t)
  }
}


const val PIP_ACTION = "com.rise.musique.PIP"
object PipMath {
  /** Android only accepts 1:2.39 .. 2.39:1. Visible for tests. */
  fun ratio(aspect: Float): Pair<Int, Int> { val a = aspect.takeIf { it.isFinite() && it > 0f } ?: (16f / 9f)
    val c = a.coerceIn(1f / 2.39f + .001f, 2.39f - .001f); return (c * 1000).toInt() to 1000 }
}
