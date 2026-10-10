package com.rise.musique

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class UpdateReleaseTest {
  private val sha = "a".repeat(64)
  private fun release() = JSONObject("""{"tag_name":"v0.7.0","draft":false,"prerelease":false,"body":"Mudanças",
    "assets":[{"name":"RiseMusique-GPT-0.7.0.apk","size":1024,
    "browser_download_url":"https://github.com/Marcelov07br/rise-musique-releases/releases/download/v0.7.0/RiseMusique-GPT-0.7.0.apk"}]}""")
  private fun manifest() = JSONObject().put("versionCode", 7).put("versionName", "0.7.0")
    .put("asset", "RiseMusique-GPT-0.7.0.apk").put("sha256", sha)
  private fun rejects(release: JSONObject = release(), manifest: JSONObject = manifest()) {
    try { UpdateReleaseParser.parse(release, manifest); fail("Should reject invalid publication") }
    catch (_: IllegalArgumentException) { }
  }
  @Test fun readsStableRelease() { val r = UpdateReleaseParser.parse(release(), manifest()); assertEquals(7, r.versionCode); assertEquals(sha, r.sha256) }
  @Test fun rejectsDraftsAndPrereleases() { rejects(release().put("draft", true)); rejects(release().put("prerelease", true)) }
  @Test fun rejectsVersionMismatch() { rejects(manifest = manifest().put("versionName", "0.8.0")) }
  @Test fun rejectsMissingDigest() { rejects(manifest = manifest().put("sha256", "oops")) }
  @Test fun rejectsNegativeVersion() { rejects(manifest = manifest().put("versionCode", -1)) }
  @Test fun rejectsWrongDownloadOrigin() { val r = release(); r.getJSONArray("assets").getJSONObject(0).put("browser_download_url", "https://evil.example/a.apk"); rejects(r) }
  @Test fun rejectsOversizedApk() { val r = release(); r.getJSONArray("assets").getJSONObject(0).put("size", UpdateReleaseParser.MAX_APK_BYTES + 1); rejects(r) }
  @Test fun rejectsDigestMismatch() { val r = release(); r.getJSONArray("assets").getJSONObject(0).put("digest", "sha256:" + "b".repeat(64)); rejects(r) }
  @Test fun restrictsAssetUrls() {
    assertFalse(UpdateReleaseParser.assetUrl("http://github.com/Marcelov07br/rise-musique-releases/releases/download/v0.7.0/a.apk"))
    assertFalse(UpdateReleaseParser.assetUrl("https://github.com@evil.example/Marcelov07br/rise-musique-releases/releases/download/v0.7.0/a.apk"))
    assertFalse(UpdateReleaseParser.assetUrl("https://github.com/other/repo/releases/download/v0.7.0/a.apk"))
    assertFalse(UpdateReleaseParser.assetUrl("https://github.com/Marcelov07br/rise-musique-releases/releases/download/v0.7.0/a.apk?redirect=evil"))
  }
}
