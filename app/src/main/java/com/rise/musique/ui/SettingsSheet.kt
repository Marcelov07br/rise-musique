package com.rise.musique.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.unit.sp
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.rise.musique.BuildConfig

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun SettingsSheet(s: UiState, a: Actions) {
  ModalBottomSheet({ a.settings(false) }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) { SettingsContent(s, a) }
}

@Composable fun SettingsContent(s: UiState, a: Actions) {
    Column(Modifier.fillMaxWidth().padding(24.dp).verticalScroll(rememberScrollState()).navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
      Text("Seu Rise", style = MaterialTheme.typography.headlineLarge)
      Text("Aparência", style = MaterialTheme.typography.titleMedium)
      PillSelector(listOf("Sistema", "Escuro", "Claro"), s.theme, { it }, a.theme, Modifier.fillMaxWidth())
      Text("Transições", style = MaterialTheme.typography.titleMedium)
      Text("Crossfade entre faixas locais. Faixas seguidas do mesmo álbum tocam sem pausa e sem fade; prévias não entram.",
        color = MaterialTheme.colorScheme.onSurfaceVariant)
      PillSelector(com.rise.musique.CrossfadeRules.OPTIONS, s.crossfade, { if (it == 0) "Não" else "${it}s" }, a.crossfade, Modifier.fillMaxWidth())
      Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) { Text("Automix", style = MaterialTheme.typography.titleMedium)
          Text("Transições no ritmo: entra no tempo forte da próxima e ajusta o andamento. Sem análise confiável, usa o crossfade.",
            color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp) }
        NeutralSwitch(s.automix, a.automix)
      }
      Text("Som", style = MaterialTheme.typography.titleMedium)
      PillButton("Equalizador", { a.eqOpen(true) }, Modifier.fillMaxWidth())
      ToggleRow("Normalizar volume", "Todas as faixas locais no mesmo volume (−14 LUFS), com limitador. Usa tags ReplayGain quando existem.", s.normalize, a.normalize)
      if (s.normalize) ToggleRow("Modo álbum", "Mantém a diferença de volume entre as faixas de um mesmo álbum.", s.normalizeAlbum, a.normalizeAlbum)
      ToggleRow("Letra na tela de bloqueio", "Mostra a linha atual da letra sincronizada na notificação e na tela de bloqueio.", s.lockLyrics, a.lockLyrics)
      Text("Downloads", style = MaterialTheme.typography.titleMedium)
      ToggleRow("Só no Wi-Fi", "Baixa apenas em rede sem limite de dados; o resto espera na fila e continua de onde parou.", s.wifiOnly, a.wifiOnly)
      Text("Last.fm", style = MaterialTheme.typography.titleMedium)
      LastFmRow(s, a)
      Text("Estatísticas", style = MaterialTheme.typography.titleMedium)
      PillButton("Ver o que você mais ouviu", { a.stats(true) }, Modifier.fillMaxWidth(), filled = false)
      Text("Biblioteca", style = MaterialTheme.typography.titleMedium)
      PillButton("Importar arquivos locais", { a.settings(false); a.importFiles() }, Modifier.fillMaxWidth(), filled = false, enabled = !s.importing)
      PillButton("Importar uma pasta", { a.settings(false); a.importFolder() }, Modifier.fillMaxWidth(), filled = false, enabled = !s.importing)
      PillButton("Ler informações e capas", { a.settings(false); a.refreshMetadata() }, Modifier.fillMaxWidth(), filled = false, enabled = !s.importing && s.history.isNotEmpty())
      Text("Backup da biblioteca", style = MaterialTheme.typography.titleMedium)
      Text("Guarda favoritos, playlists, registros e estatísticas. Os arquivos de mídia devem ser guardados separadamente.", color = MaterialTheme.colorScheme.onSurfaceVariant)
      Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        PillButton("Exportar", a.exportBackup, Modifier.weight(1f), filled = false)
        PillButton("Importar", a.importBackup, Modifier.weight(1f), filled = false)
      }
      PillButton("Verificar atualizações", a.updates, Modifier.fillMaxWidth())
      Text("Rise Musique ${BuildConfig.VERSION_NAME}", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable private fun NeutralSwitch(on: Boolean, change: (Boolean) -> Unit) {
  val c = MaterialTheme.colorScheme; val haptics = rememberHaptics()
  Switch(on, { haptics.tick(); change(it) }, colors = SwitchDefaults.colors(checkedThumbColor = c.background, checkedTrackColor = c.onSurface,
    uncheckedThumbColor = c.onSurfaceVariant, uncheckedTrackColor = c.surfaceVariant, uncheckedBorderColor = c.outline))
}

@Composable private fun LastFmRow(s: UiState, a: Actions) {
  val c = MaterialTheme.colorScheme
  var login by remember { mutableStateOf(false) }
  when {
    !s.lastfmConfigured -> Text("Não configurado nesta versão (falta a chave de API).", color = c.onSurfaceVariant, fontSize = 13.sp)
    s.lastfmUser == null -> PillButton("Conectar ao Last.fm", { login = true }, Modifier.fillMaxWidth(), filled = false)
    else -> Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
      Column(Modifier.weight(1f)) { Text("Scrobbles como ${s.lastfmUser}")
        Text("Conta após metade da faixa ou 4 min (faixas com mais de 30 s).", color = c.onSurfaceVariant, fontSize = 12.sp) }
      NeutralSwitch(s.lastfmOn, a.lastfmToggle)
      TextButton(a.lastfmLogout) { Text("Sair") }
    }
  }
  if (login) {
    var user by remember { mutableStateOf("") }; var pass by remember { mutableStateOf("") }
    AlertDialog({ login = false }, title = { Text("Entrar no Last.fm") },
      text = { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedTextField(user, { user = it.take(64) }, label = { Text("Usuário") }, singleLine = true)
        OutlinedTextField(pass, { pass = it.take(128) }, label = { Text("Senha") }, singleLine = true,
          visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation())
        Text("A senha só é usada para obter a sessão e não fica salva.", fontSize = 12.sp, color = c.onSurfaceVariant) } },
      confirmButton = { TextButton({ a.lastfmLogin(user, pass); login = false }, enabled = user.isNotBlank() && pass.isNotBlank()) { Text("Entrar") } },
      dismissButton = { TextButton({ login = false }) { Text("Cancelar") } })
  }
}

@Composable fun ToggleRow(title: String, sub: String, on: Boolean, set: (Boolean) -> Unit) =
  Row(Modifier.heightIn(min = 48.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
    Column(Modifier.weight(1f)) { Text(title, style = MaterialTheme.typography.titleMedium)
      Text(sub, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp) }
    Spacer(Modifier.width(12.dp)); NeutralSwitch(on, set)
  }
