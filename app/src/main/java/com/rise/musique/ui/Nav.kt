package com.rise.musique.ui

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.*

/** Destinations pushed on a tab's own back stack. */
sealed interface Route { val key: String
  /** [src] tells which card opened it, so the shared cover key is unique (hero vs. rows showing the same album). */
  data class Album(val name: String, val src: String = "") : Route { override val key get() = "al:$name" }
  data class RemoteAlbum(val id: String, val title: String, val art: String, val artist: String, val src: String = "") : Route { override val key get() = "ra:$id" }
  data class Playlist(val name: String) : Route { override val key get() = "pl:$name" }
  data class Artist(val name: String, val src: String = "") : Route { override val key get() = "ar:$name" }
  data class Smart(val kind: String, val value: String) : Route { override val key get() = "sm:$kind:$value" }
  data object Downloads : Route { override val key get() = "dl" }
}

/** What the screen shows: a tab and the top of its stack (null = the tab root). */
data class Screen(val tab: String, val route: Route?)

val Tabs = listOf("Início", "Buscar", "Biblioteca")

/** Three tabs, each with its own back stack and its own scroll states (kept while switching tabs). */
@Stable class NavState {
  private val stacks = mutableStateMapOf<String, List<Route>>()
  private val lists = HashMap<String, LazyListState>()
  fun stack(tab: String) = stacks[tab].orEmpty()
  fun top(tab: String) = stack(tab).lastOrNull()
  fun push(tab: String, r: Route) { if (top(tab) != r) stacks[tab] = stack(tab) + r }
  fun pop(tab: String): Boolean { val s = stack(tab); if (s.isEmpty()) return false; stacks[tab] = s.dropLast(1); return true }
  fun popToRoot(tab: String) { stacks[tab] = emptyList() }
  /** One LazyListState per tab root / route, so each keeps its own scroll position. */
  fun list(key: String, initial: Int = 0) = lists.getOrPut(key) { LazyListState(initial) }
  fun previous(tab: String) = Screen(tab, stack(tab).dropLast(1).lastOrNull())
}
