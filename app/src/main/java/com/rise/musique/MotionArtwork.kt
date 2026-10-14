package com.rise.musique

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.text.Normalizer

/**
 * 0.26 Capas animadas (motion artwork estilo Apple Music) via API de terceiros que raspa o Apple Music
 * (https://artwork.m8tec.top — sem MusicKit). Pode cair/mudar a qualquer momento: tudo aqui vira "sem capa animada".
 *
 * Formato real (curl em 14/10/2026):
 *  200 {"url":"https://mvod.itunes.apple.com/…/P517392561_default.m3u8","url_tall":"…_default.m3u8","artist":"Taylor Swift","album":"Midnights","isCached":true}
 *  404 {"message":"No animated artwork found."}
 * Só HLS (m3u8 mestre, quadrado 360–1080 px, H.264/HEVC, sem áudio); nenhum MP4 nem WebP na resposta.
 * Se algum dia vier MP4 (`url`/`mp4`/`video`), ele é preferido.
 */
data class MotionArt(val videoUrl: String, val tallUrl: String = "") {
  val isHls get() = videoUrl.substringBefore('?').endsWith(".m3u8", ignoreCase = true)
}

object MotionArtParser {
  private fun ok(u: String?) = !u.isNullOrBlank() && u.startsWith("https://")
  private fun mp4(u: String?) = ok(u) && u!!.substringBefore('?').endsWith(".mp4", ignoreCase = true)
  /** null = sem capa animada (inclui JSON inválido ou sem URL utilizável). */
  fun parse(body: String?): MotionArt? {
    if (body.isNullOrBlank()) return null
    val o = runCatching { JSONObject(body) }.getOrNull() ?: return null
    fun s(k: String) = o.optString(k, "").trim().takeIf { it.isNotEmpty() && it != "null" }
    val candidates = listOfNotNull(s("mp4"), s("video"), s("url"), s("videoUrl"), s("hls"))
    val pick = candidates.firstOrNull(::mp4) ?: candidates.firstOrNull(::ok) ?: return null
    return MotionArt(pick, s("url_tall")?.takeIf(::ok).orEmpty())
  }
}

object MotionArtKey {
  private val edition = Regex("""\s*[(\[][^)\]]*(deluxe|edition|edição|remaster|expanded|anniversary|version|versão|bonus|explicit)[^)\]]*[)\]]""", RegexOption.IGNORE_CASE)
  private val suffix = Regex("""\s+-\s+(single|ep)$""", RegexOption.IGNORE_CASE)
  private val feat = Regex("""\s*(\(|\s)(feat\.?|ft\.?|featuring)\s.*$""", RegexOption.IGNORE_CASE)
  /** Nome do álbum para a consulta: sem "(Deluxe Edition)", " - Single" etc. */
  fun cleanAlbum(a: String) = a.replace(edition, "").replace(suffix, "").trim()
  fun cleanArtist(a: String) = a.replace(feat, "").substringBefore(",").substringBefore(" & ").substringBefore(" x ").trim()
  private fun norm(s: String) = Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "")
    .replace(Regex("[^a-z0-9]+"), " ").trim()
  fun of(artist: String, album: String) = norm(cleanArtist(artist)) + "|" + norm(cleanAlbum(album))
}

/** Cliente HTTP mínimo (o app usa HttpURLConnection; sem OkHttp). Retorna código e corpo; exceção = falha de rede. */
fun interface MotionHttp {
  fun get(url: String): Pair<Int, String>
  companion object {
    val Default = MotionHttp { u ->
      val c = URL(u).openConnection() as HttpURLConnection
      try {
        c.connectTimeout = 5_000; c.readTimeout = 8_000; c.setRequestProperty("Accept", "application/json")
        c.setRequestProperty("User-Agent", "RiseMusique")
        val code = c.responseCode
        val body = (if (code in 200..299) c.inputStream else c.errorStream)?.bufferedReader()?.use { it.readText().take(64_000) }.orEmpty()
        code to body
      } finally { c.disconnect() }
    }
  }
}

/**
 * Repositório: consulta a API (em [io]), guarda o resultado por artista+álbum normalizados em memória e em [cacheFile]
 * (JSON), inclusive negativos. TTL: achou 14 dias; 404/sem URL 3 dias; erro (rede/5xx/429/JSON) 30 min.
 * Qualquer falha vira null ("nenhuma"); nunca lança.
 */
class MotionArtworkRepository(
  private val cacheFile: File?,
  private val http: MotionHttp = MotionHttp.Default,
  private val clock: () -> Long = System::currentTimeMillis,
  private val io: CoroutineDispatcher = Dispatchers.IO,
  private val baseUrl: String = BASE,
) {
  companion object {
    const val BASE = "https://artwork.m8tec.top"
    const val TTL_FOUND = 14L * 24 * 3600_000
    const val TTL_NONE = 3L * 24 * 3600_000
    const val TTL_ERROR = 30L * 60_000
    const val MAX_ENTRIES = 600
  }
  data class Entry(val url: String, val tall: String, val at: Long, val ttl: Long) {
    val art get() = if (url.isBlank()) null else MotionArt(url, tall)
  }
  private val mutex = Mutex()
  private var mem: LinkedHashMap<String, Entry>? = null
  /** Chamadas de rede feitas (para testes/diagnóstico). */
  @Volatile var networkCalls = 0; private set

  suspend fun find(artist: String, album: String): MotionArt? {
    if (artist.isBlank() || album.isBlank()) return null
    val key = MotionArtKey.of(artist, album)
    if (key.startsWith("|") || key.endsWith("|")) return null
    return withContext(io) {
      mutex.withLock {
        val map = load()
        map[key]?.let { e -> if (clock() - e.at in 0 until e.ttl) return@withLock e.art }
        val e = fetch(artist, album)
        map.remove(key); map[key] = e
        while (map.size > MAX_ENTRIES) map.remove(map.keys.first())
        save(map)
        e.art
      }
    }
  }

  /** Só leitura do cache (sem rede). */
  suspend fun cached(artist: String, album: String): Entry? = withContext(io) { mutex.withLock { load()[MotionArtKey.of(artist, album)] } }

  private fun fetch(artist: String, album: String): Entry {
    val now = clock()
    val url = "$baseUrl/api/v1/artwork/search?artist=${enc(MotionArtKey.cleanArtist(artist))}&album=${enc(MotionArtKey.cleanAlbum(album))}"
    return try {
      networkCalls++
      val (code, body) = http.get(url)
      when {
        code in 200..299 -> MotionArtParser.parse(body)?.let { Entry(it.videoUrl, it.tallUrl, now, TTL_FOUND) }
          ?: if (runCatching { JSONObject(body) }.isSuccess) Entry("", "", now, TTL_NONE) else Entry("", "", now, TTL_ERROR)
        code == 404 -> Entry("", "", now, TTL_NONE)
        else -> Entry("", "", now, TTL_ERROR)
      }
    } catch (t: Throwable) {
      Entry("", "", now, TTL_ERROR)
    }
  }
  private fun enc(s: String) = URLEncoder.encode(s, "UTF-8").replace("+", "%20")

  private fun load(): LinkedHashMap<String, Entry> {
    mem?.let { return it }
    val m = LinkedHashMap<String, Entry>()
    runCatching {
      val f = cacheFile ?: return@runCatching
      if (!f.exists()) return@runCatching
      val o = JSONObject(f.readText())
      for (k in o.keys()) { val e = o.getJSONObject(k)
        m[k] = Entry(e.optString("u"), e.optString("t"), e.optLong("at"), e.optLong("ttl")) }
    }
    mem = m; return m
  }
  private fun save(m: Map<String, Entry>) {
    val f = cacheFile ?: return
    runCatching {
      val o = JSONObject()
      for ((k, e) in m) o.put(k, JSONObject().put("u", e.url).put("t", e.tall).put("at", e.at).put("ttl", e.ttl))
      f.parentFile?.mkdirs(); val tmp = File(f.path + ".tmp"); tmp.writeText(o.toString()); tmp.renameTo(f)
    }
  }
}
