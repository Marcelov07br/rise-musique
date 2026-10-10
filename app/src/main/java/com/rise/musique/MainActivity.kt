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
    if (Build.VERSION.SDK_INT >= 33) registerForActivityResult(ActivityResultContracts.RequestPermission()) {}
      .launch(Manifest.permission.POST_NOTIFICATIONS)
    setContent { val sys = isSystemInDarkTheme(); var dark by remember { mutableStateOf(sys) }
      val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree(), vm::onFolder)
      val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json"), vm::exportBackup)
      val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument(), vm::importBackup)
      var crash by remember { mutableStateOf(java.io.File(filesDir, "crash.txt").takeIf { it.exists() }?.readText()) }
      RiseTheme(dark) {
        UpdateDialog(updates, ::installUpdate)
        crash?.let { log -> androidx.compose.material3.AlertDialog(onDismissRequest = { java.io.File(filesDir, "crash.txt").delete(); crash = null },
          title = { androidx.compose.material3.Text("O app fechou sozinho da última vez") },
          text = { androidx.compose.material3.Text("Compartilhe o relatório com o Dev Compose pra ele achar a causa.\n\n" + log.lines().take(6).joinToString("\n"), fontSize = androidx.compose.ui.unit.TextUnit(12f, androidx.compose.ui.unit.TextUnitType.Sp)) },
          confirmButton = { androidx.compose.material3.TextButton({ startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, log), null))
            java.io.File(filesDir, "crash.txt").delete(); crash = null }) { androidx.compose.material3.Text("Compartilhar") } },
          dismissButton = { androidx.compose.material3.TextButton({ java.io.File(filesDir, "crash.txt").delete(); crash = null }) { androidx.compose.material3.Text("Ignorar") } }) }
        RiseApp(vm.state, Actions(vm::onQuery, vm::onMode, vm::onQuality, { picker.launch(null) },
        vm::enqueue, { dark = !dark }, vm::search, vm::select, vm::togglePreview,
        selectAll = vm::selectAll, format = vm::onFormat, cut = vm::onCut, cancel = vm::cancel, retry = vm::retry, dismiss = vm::dismiss,
        open = { h -> vm.openIntent(h, false)?.let(::startActivity) }, share = { h -> vm.openIntent(h, true)?.let(::startActivity) },
        delete = vm::delete, play = vm::play, playPause = vm::playPause, seek = vm::seek, closePlayer = vm::closePlayer, next = vm::next, prev = vm::prev, shuffle = vm::shuffle,
        favorite = vm::favorite, addToPlaylist = vm::addToPlaylist, removeFromPlaylist = vm::removeFromPlaylist, deletePlaylist = vm::deletePlaylist,
        historyFilter = vm::historyFilter, historyView = vm::historyView, sleep = vm::sleep,
        exportBackup = { exporter.launch("rise-musique-backup.json") }, importBackup = { importer.launch(arrayOf("application/json", "*/*")) },
        updates = { updates.check(true) }, page = vm::page, repeat = vm::repeat, showQueue = vm::showQueue,
        moveQueue = vm::moveQueue, removeQueue = vm::removeQueue, playQueue = vm::playQueue, playNext = vm::playNext, addQueue = vm::addQueue)) } }
  }
  override fun onNewIntent(i: Intent) { super.onNewIntent(i); handle(i) }
  private fun handle(i: Intent?) { if (i?.action == Intent.ACTION_SEND) vm.onIncomingLink(i.getStringExtra(Intent.EXTRA_TEXT)) }
  override fun onWindowFocusChanged(f: Boolean) { super.onWindowFocusChanged(f)
    if (!f || vm.state.query.isNotBlank()) return
    val t = getSystemService(ClipboardManager::class.java).primaryClip?.getItemAt(0)?.text?.toString() ?: return
    if (Regex("(youtube\\.com|youtu\\.be|music\\.youtube)").containsMatchIn(t)) vm.onIncomingLink(t)
  }
}

