package com.rise.musique.ui
import androidx.compose.material.icons.rounded.ContentCut

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable fun DownloadOptions(state: UiState, a: Actions, capped: Boolean = true) {
  Column(Modifier.fillMaxWidth().then(if (capped) Modifier.heightIn(max = 260.dp).verticalScroll(rememberScrollState()) else Modifier), verticalArrangement = Arrangement.spacedBy(8.dp)) {
    Text("Tipo de mídia", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    // Tipo de mídia e corte na mesma linha: o chip de corte abre a folha com forma de onda / linha do tempo.
    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      ChoiceRow(Mode.entries, state.mode.takeIf { state.choices.media }, { it.label }, a.mode, Modifier.weight(1f))
      if (state.dlItems.size == 1) CutChip(state, a)
    }
    AnimatedVisibility(state.choices.media) {
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (state.mode == Mode.Musica) {
          Text("Formato do áudio", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
          ChoiceRow(audioFormats, state.format.takeIf { state.choices.format }, { it }, a.format)
        }
        AnimatedVisibility(state.mode == Mode.Video || state.choices.format) {
          Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (state.mode == Mode.Musica && state.format == "FLAC")
              Text("FLAC preserva o áudio obtido; não recupera qualidade perdida na fonte.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            else {
              Text(if (state.mode == Mode.Video) "Qualidade do vídeo" else "Qualidade do áudio", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
              ChoiceRow(state.mode.qualities, state.quality.takeIf { state.choices.quality }, { it }, a.quality)
            }
          }
        }
        PillButton(state.folder?.lastPathSegment?.substringAfter(':')?.ifBlank { "Raiz" } ?: "Escolher pasta (opcional)",
          a.pickFolder, Modifier.fillMaxWidth(), Icons.Rounded.FolderOpen, filled = false)
      }
    }
  }
}

@Composable private fun <T> ChoiceRow(options: List<T>, chosen: T?, label: (T) -> String, choose: (T) -> Unit, modifier: Modifier = Modifier.fillMaxWidth()) {
  Row(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
    options.forEach { option ->
      // Compact padding keeps four formats usable on narrow phones and large fonts.
      FilterChip(selected = chosen == option, onClick = { choose(option) }, label = { Text(label(option), maxLines = 1) },
        modifier = Modifier.weight(1f).semantics { selected = chosen == option })
    }
  }
}

@Composable private fun CutChip(state: UiState, a: Actions) {
  val on = state.cutOn && (state.cutStart.isNotBlank() || state.cutEnd.isNotBlank())
  FilterChip(selected = on, onClick = { a.openCut(true) },
    leadingIcon = { Icon(androidx.compose.material.icons.Icons.Rounded.ContentCut, null, Modifier.size(18.dp)) },
    label = { Text(if (on) "${state.cutStart.ifBlank { "0:00" }}–${state.cutEnd.ifBlank { "fim" }}" else "Cortar", maxLines = 1) },
    modifier = Modifier.heightIn(min = 48.dp).semantics { selected = on })
}
