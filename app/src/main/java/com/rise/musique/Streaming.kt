package com.rise.musique

import android.content.Context
import android.net.Uri
import androidx.media3.common.C
import androidx.media3.datasource.*
import androidx.media3.datasource.cache.*
import androidx.media3.database.StandaloneDatabaseProvider
import kotlinx.coroutines.runBlocking
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Full-length streaming of YouTube tracks. Items carry a stable `rise-stream://<videoId>` URI; the real googlevideo URL
 * (which expires) is resolved lazily with yt-dlp right when bytes are needed, re-resolved before expiry and again on 403/410.
 * Bytes go through a SimpleCache keyed by video id, so a fully played track can be saved without downloading again.
 */
@androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])
object Streams {
  const val SCHEME = "rise-stream"
  fun uriFor(url: String): Uri? = Engine.videoId(url)?.let { Uri.parse("$SCHEME://$it") }
  fun idOf(uri: Uri?): String? = uri?.takeIf { it.scheme == SCHEME }?.host
  fun cacheKey(id: String) = "a:$id"

  data class Resolved(val url: String, val headers: Map<String, String>, val expiresAtMs: Long, val formatId: String, val ext: String)
  private val resolved = ConcurrentHashMap<String, Resolved>()

  /** `expire=<unix seconds>` in googlevideo URLs. Visible for tests. */
  fun expiryOf(url: String, now: Long = System.currentTimeMillis()): Long =
    Regex("[?&/]expire[=/](\\d+)").find(url)?.groupValues?.get(1)?.toLongOrNull()?.times(1000) ?: (now + 5 * 60_000)
  fun fresh(r: Resolved?, now: Long = System.currentTimeMillis()) = r != null && r.expiresAtMs - now > 60_000

  fun resolve(id: String, force: Boolean = false): Resolved {
    if (!force) resolved[id]?.takeIf { fresh(it) }?.let { return it }
    val s = runBlocking { Engine.audioStream("https://music.youtube.com/watch?v=$id") }
    return Resolved(s.url, s.headers, expiryOf(s.url), s.formatId, s.ext).also { resolved[id] = it }
  }
  fun known(id: String) = resolved[id]
  /** Warm up the next item so the gap between tracks is just a TCP connect. */
  fun prefetch(id: String) { if (!fresh(resolved[id])) runCatching { resolve(id) } }

  @Volatile private var cache: SimpleCache? = null
  @Synchronized fun cache(ctx: Context): SimpleCache = cache ?: SimpleCache(File(ctx.cacheDir, "stream"),
    LeastRecentlyUsedCacheEvictor(768L * 1024 * 1024), StandaloneDatabaseProvider(ctx.applicationContext)).also { cache = it }

  /** Bytes cached vs total for [id] (total -1 if unknown). */
  fun cachedBytes(ctx: Context, id: String): Pair<Long, Long> { val c = cache(ctx); val k = cacheKey(id)
    val len = ContentMetadata.getContentLength(c.getContentMetadata(k)); return (if (len > 0) c.getCachedBytes(k, 0, len) else 0L) to len }
  fun fullyCached(ctx: Context, id: String) = cachedBytes(ctx, id).let { (have, len) -> len > 0 && have >= len }

  /** Copies a fully cached stream into [out]. Returns false if anything is missing. */
  fun copyCached(ctx: Context, id: String, out: File): Boolean = runCatching {
    if (!fullyCached(ctx, id)) return false
    val src = CacheDataSource(cache(ctx), PlaceholderDataSource.INSTANCE, CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
    src.open(DataSpec.Builder().setUri(Uri.parse("$SCHEME://$id")).setKey(cacheKey(id)).build())
    out.outputStream().use { o -> val buf = ByteArray(64 * 1024); while (true) { val n = src.read(buf, 0, buf.size); if (n == C.RESULT_END_OF_INPUT) break; o.write(buf, 0, n) } }
    src.close(); true
  }.getOrDefault(false)
}

/** Opens `rise-stream://` by resolving it, and transparently re-resolves once when googlevideo answers 403/410 (expired URL). */
@androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])
class StreamDataSource(private val http: HttpDataSource) : BaseDataSource(true) {
  private var opened: DataSpec? = null
  override fun open(spec: DataSpec): Long {
    val id = Streams.idOf(spec.uri) ?: return http.open(spec).also { opened = spec }
    fun real(r: Streams.Resolved) = spec.buildUpon().setUri(r.url).setHttpRequestHeaders(spec.httpRequestHeaders + r.headers.filterKeys { !it.equals("Accept-Encoding", true) }).build()
    return try { http.open(real(Streams.resolve(id))) } catch (e: HttpDataSource.InvalidResponseCodeException) {
      if (e.responseCode != 403 && e.responseCode != 410) throw e
      http.close(); http.open(real(Streams.resolve(id, force = true)))
    }.also { opened = spec }
  }
  override fun read(buffer: ByteArray, offset: Int, length: Int) = http.read(buffer, offset, length)
  override fun getUri(): Uri? = opened?.uri
  override fun close() { opened = null; http.close() }
  override fun getResponseHeaders() = http.responseHeaders

  class Factory(private val ctx: Context) : DataSource.Factory {
    override fun createDataSource(): DataSource {
      val http = DefaultHttpDataSource.Factory().setUserAgent(Engine.DEFAULT_UA).setAllowCrossProtocolRedirects(true)
        .setConnectTimeoutMs(15_000).setReadTimeoutMs(20_000).createDataSource()
      return StreamDataSource(http)
    }
  }
}

/** Player data sources: local files/content, http with yt-dlp headers, and cached `rise-stream://` items. */
@androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])
fun playerDataSource(ctx: Context): DataSource.Factory {
  val stream = CacheDataSource.Factory().setCache(Streams.cache(ctx)).setUpstreamDataSourceFactory(StreamDataSource.Factory(ctx))
    .setCacheKeyFactory { spec -> Streams.idOf(spec.uri)?.let(Streams::cacheKey) ?: spec.key ?: spec.uri.toString() }
    .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
  val other = streamDataSource(ctx)
  return DataSource.Factory { RoutingDataSource(stream.createDataSource(), other.createDataSource()) }
}

@androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])
private class RoutingDataSource(private val stream: DataSource, private val other: DataSource) : DataSource {
  private var cur: DataSource? = null
  override fun addTransferListener(l: TransferListener) { stream.addTransferListener(l); other.addTransferListener(l) }
  override fun open(spec: DataSpec): Long { val d = if (spec.uri.scheme == Streams.SCHEME) stream else other; cur = d; return d.open(spec) }
  override fun read(b: ByteArray, o: Int, l: Int) = (cur ?: throw java.io.IOException("Fonte fechada")).read(b, o, l)
  override fun getUri() = cur?.uri
  override fun getResponseHeaders() = cur?.responseHeaders ?: emptyMap()
  override fun close() { cur?.close(); cur = null }
}
