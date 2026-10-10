package com.rise.musique.ui

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

@Composable fun DownloadOptions(state: UiState, a: Actions) {
  Column(Modifier.fillMaxWidth().heightIn(max = 260.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
    Text("1 · Tipo de mídia", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    ChoiceRow(Mode.entries, state.mode.takeIf { state.choices.media }, { it.label }, a.mode)
    AnimatedVisibility(state.choices.media) {
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (state.mode == Mode.Musica) {
          Text("2 · Formato do áudio", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
          ChoiceRow(audioFormats, state.format.takeIf { state.choices.format }, { it }, a.format)
        }
        AnimatedVisibility(state.mode == Mode.Video || state.choices.format) {
          Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (state.mode == Mode.Musica && state.format == "FLAC")
              Text("FLAC preserva o áudio obtido; não recupera qualidade perdida na fonte.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            else {
              Text(if (state.mode == Mode.Video) "2 · Qualidade do vídeo" else "3 · Qualidade do áudio", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
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

@Composable private fun <T> ChoiceRow(options: List<T>, chosen: T?, label: (T) -> String, choose: (T) -> Unit) {
  Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
    options.forEach { option ->
      // Compact padding keeps four formats usable on narrow phones and large fonts.
      FilterChip(selected = chosen == option, onClick = { choose(option) }, label = { Text(label(option), maxLines = 1) },
        modifier = Modifier.weight(1f).semantics { selected = chosen == option })
    }
  }
}
