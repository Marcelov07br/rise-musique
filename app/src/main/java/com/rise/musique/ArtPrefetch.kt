package com.rise.musique

import android.content.Context

/** 0.24: warms Coil's memory/disk cache with the next covers (queue, search results) so rows appear with art already drawn. */
object ArtPrefetch {
  private val seen = java.util.Collections.synchronizedSet(LinkedHashSet<String>())
  /** Remote/content URLs worth prefetching, without duplicates or blanks. Pure, tested. */
  fun pick(urls: List<String>, limit: Int, already: Set<String> = emptySet()): List<String> =
    urls.asSequence().filter { it.isNotBlank() && it !in already }.distinct().take(limit).toList()
  fun ahead(ctx: Context, urls: List<String>, limit: Int = 6) {
    val todo = pick(urls, limit, synchronized(seen) { seen.toSet() }); if (todo.isEmpty()) return
    runCatching { val loader = coil.Coil.imageLoader(ctx)
      todo.forEach { u -> seen.add(u)
        loader.enqueue(coil.request.ImageRequest.Builder(ctx).data(u).size(256).build()) } }
    synchronized(seen) { while (seen.size > 400) seen.remove(seen.first()) }
  }
}
