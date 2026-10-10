package com.rise.musique

import org.json.JSONObject
import java.net.URI

data class UpdateRelease(val versionCode: Int, val versionName: String, val url: String, val sha256: String,
                         val size: Long, val notes: String)

object UpdateReleaseParser {
  const val REPOSITORY = "Marcelov07br/rise-musique-releases"
  const val API = "https://api.github.com/repos/$REPOSITORY/releases/latest"
  const val MAX_APK_BYTES = 250L * 1024 * 1024

  fun assetUrl(url: String): Boolean = runCatching {
    val u = URI(url)
    u.scheme == "https" && u.host == "github.com" && u.userInfo == null && u.port == -1 &&
      u.rawQuery == null && u.rawFragment == null && u.path.startsWith("/$REPOSITORY/releases/download/")
  }.getOrDefault(false)

  fun parse(release: JSONObject, manifest: JSONObject): UpdateRelease {
    require(!release.optBoolean("draft") && !release.optBoolean("prerelease")) { "Publicação não estável" }
    val code = manifest.getInt("versionCode")
    val name = manifest.getString("versionName")
    require(code > 0 && release.getString("tag_name") == "v$name") { "Versão da publicação inválida" }
    val sha = manifest.getString("sha256").lowercase()
    require(sha.matches(Regex("[a-f0-9]{64}"))) { "SHA-256 inválido" }
    val file = manifest.getString("asset")
    require(file.endsWith(".apk")) { "Arquivo inválido" }
    val assets = release.getJSONArray("assets")
    val a = (0 until assets.length()).map(assets::getJSONObject).single { it.getString("name") == file }
    val url = a.getString("browser_download_url")
    require(assetUrl(url)) { "Origem da atualização inválida" }
    val size = a.getLong("size")
    require(size in 1..MAX_APK_BYTES) { "Tamanho da atualização inválido" }
    val digest = a.optString("digest")
    require(digest.isBlank() || digest == "null" || digest == "sha256:$sha") { "Resumo do arquivo divergente" }
    return UpdateRelease(code, name, url, sha, size, release.optString("body").take(3000))
  }
}
