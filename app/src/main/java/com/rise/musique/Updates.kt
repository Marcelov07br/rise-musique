package com.rise.musique

import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.runtime.*
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

object Updates {
  private fun connect(url: String): HttpURLConnection {
    require(URL(url).protocol == "https")
    return (URL(url).openConnection() as HttpURLConnection).apply {
      connectTimeout = 15_000; readTimeout = 30_000
      setRequestProperty("User-Agent", "RiseMusique/${BuildConfig.VERSION_NAME}")
      setRequestProperty("Accept", "application/vnd.github+json")
    }
  }

  private fun json(url: String, missingIsEmpty: Boolean = false): JSONObject? {
    val c = connect(url)
    try {
      if (c.responseCode == 404 && missingIsEmpty) return null
      require(c.responseCode == 200) { "Não foi possível consultar o GitHub (${c.responseCode})" }
      val bytes = c.inputStream.use { it.readBytesLimited(256 * 1024) }
      return JSONObject(bytes.toString(Charsets.UTF_8))
    } finally { c.disconnect() }
  }

  private fun java.io.InputStream.readBytesLimited(limit: Int): ByteArray {
    val out = java.io.ByteArrayOutputStream()
    val buffer = ByteArray(8192)
    while (true) { val n = read(buffer); if (n < 0) break
      require(out.size() + n <= limit) { "Resposta muito grande" }; out.write(buffer, 0, n) }
    return out.toByteArray()
  }

  suspend fun latest(): UpdateRelease? = withContext(Dispatchers.IO) {
    val release = json(UpdateReleaseParser.API, true) ?: return@withContext null
    val assets = release.getJSONArray("assets")
    val manifest = (0 until assets.length()).map(assets::getJSONObject)
      .singleOrNull { it.optString("name") == "update.json" } ?: return@withContext null
    val url = manifest.getString("browser_download_url")
    require(UpdateReleaseParser.assetUrl(url)) { "Origem da publicação inválida" }
    UpdateReleaseParser.parse(release, json(url) ?: return@withContext null).takeIf { it.versionCode > BuildConfig.VERSION_CODE }
  }

  suspend fun download(ctx: Context, release: UpdateRelease, progress: (Float) -> Unit): File = withContext(Dispatchers.IO) {
    require(UpdateReleaseParser.assetUrl(release.url))
    val dir = File(ctx.cacheDir, "updates").apply { mkdirs() }
    val part = File(dir, "update.part")
    val apk = File(dir, "rise-${release.versionCode}.apk")
    val c = connect(release.url)
    try {
      require(c.responseCode == 200) { "Falha ao baixar atualização (${c.responseCode})" }
      val digest = MessageDigest.getInstance("SHA-256")
      var total = 0L
      c.inputStream.use { input -> part.outputStream().use { output ->
        val buffer = ByteArray(64 * 1024)
        while (true) {
          currentCoroutineContext().ensureActive()
          val n = input.read(buffer); if (n < 0) break
          total += n
          require(total <= release.size && total <= UpdateReleaseParser.MAX_APK_BYTES) { "Arquivo maior que o esperado" }
          output.write(buffer, 0, n); digest.update(buffer, 0, n)
          withContext(Dispatchers.Main) { progress(total.toFloat() / release.size) }
        }
      } }
      require(total == release.size) { "Download incompleto. Tente novamente" }
      val actual = digest.digest().joinToString("") { "%02x".format(it.toInt() and 255) }
      require(actual == release.sha256) { "Arquivo não passou na verificação de integridade" }
      validateApk(ctx, part, release)
      if (apk.exists()) require(apk.delete())
      require(part.renameTo(apk)) { "Não foi possível preparar a atualização" }
      apk
    } finally { c.disconnect(); part.delete() }
  }

  @Suppress("DEPRECATION")
  private fun validateApk(ctx: Context, file: File, release: UpdateRelease) {
    val flags = if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES
    val installed = ctx.packageManager.getPackageInfo(ctx.packageName, flags)
    val candidate = ctx.packageManager.getPackageArchiveInfo(file.absolutePath, flags) ?: error("APK inválido")
    require(candidate.packageName == ctx.packageName) { "APK de outro aplicativo" }
    val code = if (Build.VERSION.SDK_INT >= 28) candidate.longVersionCode else candidate.versionCode.toLong()
    require(code == release.versionCode.toLong() && code > BuildConfig.VERSION_CODE) { "Versão do APK divergente" }
    fun certs(info: android.content.pm.PackageInfo): Set<String> {
      val signatures = if (Build.VERSION.SDK_INT >= 28) info.signingInfo?.apkContentsSigners else info.signatures
      return signatures.orEmpty().map { s -> MessageDigest.getInstance("SHA-256").digest(s.toByteArray())
        .joinToString("") { "%02x".format(it.toInt() and 255) } }.toSet()
    }
    val current = certs(installed)
    require(current.isNotEmpty() && current == certs(candidate)) { "Assinatura diferente. Esta versão não pode atualizar o app instalado" }
  }

  fun installIntent(ctx: Context, apk: File): Intent = Intent(Intent.ACTION_VIEW)
    .setDataAndType(FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", apk), "application/vnd.android.package-archive")
    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)

  fun permissionIntent(ctx: Context) = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${ctx.packageName}"))
}

class UpdatesViewModel(app: Application) : AndroidViewModel(app) {
  var release by mutableStateOf<UpdateRelease?>(null); private set
  var checking by mutableStateOf(false); private set
  var downloading by mutableStateOf(false); private set
  var progress by mutableFloatStateOf(0f); private set
  var message by mutableStateOf(""); private set
  var dialog by mutableStateOf(false); private set
  var ready by mutableStateOf<File?>(null); private set
  private var lastCheck = 0L
  private var ignoredCode = 0
  private var job: Job? = null

  fun check(manual: Boolean = false) {
    if (checking || downloading) return
    val now = android.os.SystemClock.elapsedRealtime()
    if (!manual && lastCheck != 0L && now - lastCheck < 5 * 60_000L) return
    lastCheck = now; checking = true
    if (manual) dialog = true
    viewModelScope.launch {
      try {
        val latest = Updates.latest()
        if (latest?.versionCode != release?.versionCode) ready = null
        release = latest
        message = if (latest == null) "Você está na versão ${BuildConfig.VERSION_NAME}. Nenhuma atualização disponível." else "Nova versão ${latest.versionName} disponível"
        if (latest != null && (manual || latest.versionCode != ignoredCode)) dialog = true
      } catch (e: CancellationException) { throw e
      } catch (e: Exception) { message = e.message ?: "Não foi possível verificar atualizações. Tente novamente." }
      finally { checking = false }
    }
  }

  fun download() {
    val target = release ?: return
    if (downloading) return
    downloading = true; progress = 0f; ready = null
    job = viewModelScope.launch {
      try { ready = Updates.download(getApplication(), target) { progress = it }; message = "Atualização pronta para instalar" }
      catch (e: CancellationException) { message = "Download cancelado"; throw e }
      catch (e: Exception) { message = e.message ?: "Falha no download. Tente novamente." }
      finally { downloading = false }
    }
  }
  fun cancel() { job?.cancel() }
  fun dismiss() { if (!downloading) { ignoredCode = release?.versionCode ?: 0; dialog = false } }
  fun installError(e: Exception) { message = e.message ?: "Não foi possível abrir o instalador" }
}
