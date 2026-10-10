package com.rise.musique.ui

import com.rise.musique.Moods

/** Auto collection: by genre (tag), decade (year tag) or mood (BPM + loudness from the background analysis). */
data class SmartFolder(val kind: String, val value: String, val tracks: List<HistoryItem>) { val key get() = "cover-sm-$kind-$value" }

const val MinSmart = 2

fun smartFolders(s: UiState): List<SmartFolder> {
  val local = s.history.filter { !it.video && it.uri !in s.hidden }
  fun group(kind: String, f: (HistoryItem) -> String?) = local.groupBy { h -> s.trackInfo[h.uri]?.let { f(h) } }
    .filterKeys { !it.isNullOrBlank() }.filterValues { it.size >= MinSmart }.map { (k, v) -> SmartFolder(kind, k!!, v) }
  val moods = group("Clima") { h -> s.trackInfo[h.uri]?.let(Moods::of) }.sortedBy { Moods.ALL.indexOf(it.value) }
  val genres = group("Gênero") { h -> s.trackInfo[h.uri]?.genre }.sortedByDescending { it.tracks.size }
  val decades = group("Década") { h -> s.trackInfo[h.uri]?.year?.let(Moods::decade) }.sortedByDescending { it.value }
  return moods + genres + decades
}

fun smartFolder(s: UiState, kind: String, value: String) = smartFolders(s).firstOrNull { it.kind == kind && it.value == value }
