package com.rise.musique

import org.json.JSONObject
import org.json.JSONArray

object PlaylistEdits {
  fun rename(pl: JSONObject, old: String, name: String) {
    require(name.isNotBlank() && name.length <= 80 && name !in setOf("Tudo", "Favoritos")) { "Escolha um nome de até 80 caracteres" }
    require(pl.has(old)) { "Playlist não encontrada" }
    if (name == old) return
    require(!pl.has(name)) { "Já existe uma playlist com esse nome" }
    val tracks = pl.getJSONArray(old); pl.remove(old); pl.put(name, tracks)
  }
  fun move(tracks: List<String>, from: Int, to: Int): List<String> {
    require(from in tracks.indices && to in tracks.indices) { "Posição inválida" }
    return tracks.toMutableList().apply { add(to, removeAt(from)) }
  }
}
