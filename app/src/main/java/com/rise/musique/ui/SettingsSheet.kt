package com.rise.musique.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.rise.musique.BuildConfig

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun SettingsSheet(s: UiState, a: Actions) {
  ModalBottomSheet({ a.settings(false) }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
    Column(Modifier.fillMaxWidth().padding(24.dp).verticalScroll(rememberScrollState()).navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
      Text("Seu Rise", style = MaterialTheme.typography.headlineLarge)
      Text("Aparência", style = MaterialTheme.typography.titleMedium)
      PillSelector(listOf("Sistema", "Escuro", "Claro"), s.theme, { it }, a.theme, Modifier.fillMaxWidth())
      Text("Biblioteca", style = MaterialTheme.typography.titleMedium)
      PillButton("Importar arquivos locais", { a.settings(false); a.importFiles() }, Modifier.fillMaxWidth(), filled = false, enabled = !s.importing)
      PillButton("Importar uma pasta", { a.settings(false); a.importFolder() }, Modifier.fillMaxWidth(), filled = false, enabled = !s.importing)
      PillButton("Ler informações e capas", { a.settings(false); a.refreshMetadata() }, Modifier.fillMaxWidth(), filled = false, enabled = !s.importing && s.history.isNotEmpty())
      Text("Backup da biblioteca", style = MaterialTheme.typography.titleMedium)
      Text("Guarda favoritos, playlists e registros. Os arquivos de mídia devem ser guardados separadamente.", color = MaterialTheme.colorScheme.onSurfaceVariant)
      Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        PillButton("Exportar", a.exportBackup, Modifier.weight(1f), filled = false)
        PillButton("Importar", a.importBackup, Modifier.weight(1f), filled = false)
      }
      PillButton("Verificar atualizações", a.updates, Modifier.fillMaxWidth())
      Text("Rise Musique GPT ${BuildConfig.VERSION_NAME}", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
  }
}
