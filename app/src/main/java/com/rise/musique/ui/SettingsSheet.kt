package com.rise.musique.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.ui.unit.sp
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.rise.musique.BuildConfig

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun SettingsSheet(s: UiState, a: Actions) {
  ModalBottomSheet({ a.settings(false) }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) { SettingsContent(s, a) }
}

/** One option of Ajustes: lives in a section subpage and is found by the search at the top. */
private class SettingEntry(val section: String, val title: String, val keywords: String, val content: @Composable () -> Unit) {
  fun matches(q: String) = q.isBlank() || (title + " " + keywords + " " + section).lowercase().contains(q.trim().lowercase())
}
data class SettingsSection(val name: String, val summary: String, val icon: androidx.compose.ui.graphics.vector.ImageVector)
val SettingsSections = listOf(
  SettingsSection("Reprodução", "Transições, automix, fila inteligente, letra", Icons.Rounded.PlayCircle),
  SettingsSection("Áudio", "Equalizador, volume, qualidade online", Icons.Rounded.GraphicEq),
  SettingsSection("Downloads", "Wi-Fi, qualidade e formato", Icons.Rounded.Download),
  SettingsSection("Aparência", "Tema, capas animadas, reduzir movimento", Icons.Rounded.Palette),
  SettingsSection("Integrações", "Last.fm, importar arquivos, backup", Icons.Rounded.Extension),
  SettingsSection("Sobre", "Estatísticas, atualizações, novidades", Icons.Rounded.Info))

@Composable private fun settingEntries(s: UiState, a: Actions, about: () -> Unit): List<SettingEntry> {
  val sub = MaterialTheme.colorScheme.onSurfaceVariant
  return listOf(
    SettingEntry("Reprodução", "Transições", "crossfade fade entre faixas") {
      Column(verticalArrangement = Arrangement.spacedBy(10.dp)) { Text("Transições", style = MaterialTheme.typography.titleMedium)
        Text("Crossfade entre faixas locais. Faixas seguidas do mesmo álbum tocam sem pausa e sem fade; prévias não entram.", color = sub, fontSize = 13.sp)
        PillSelector(com.rise.musique.CrossfadeRules.OPTIONS, s.crossfade, { if (it == 0) "Não" else "${it}s" }, a.crossfade, Modifier.fillMaxWidth()) } },
    SettingEntry("Reprodução", "Automix", "ritmo batida andamento") {
      ToggleRow("Automix", "Transições no ritmo: entra no tempo forte da próxima e ajusta o andamento. Sem análise confiável, usa o crossfade.", s.automix, a.automix) },
    SettingEntry("Reprodução", "Fila inteligente", "smart queue continuar parecidas autoplay rádio") {
      ToggleRow("Fila inteligente", "Quando a fila acaba, continua com músicas parecidas com a última (precisa de internet).", s.smartQueue, a.smartQueue) },
    SettingEntry("Reprodução", "Letra na tela de bloqueio", "lyrics notificação") {
      ToggleRow("Letra na tela de bloqueio", "Mostra a linha atual da letra sincronizada na notificação e na tela de bloqueio.", s.lockLyrics, a.lockLyrics) },
    SettingEntry("Áudio", "Equalizador", "eq graves agudos som") { PillButton("Equalizador", { a.eqOpen(true) }, Modifier.fillMaxWidth()) },
    SettingEntry("Áudio", "Normalizar volume", "loudness replaygain lufs limitador") {
      Column { ToggleRow("Normalizar volume", "Todas as faixas locais no mesmo volume (−14 LUFS), com limitador. Usa tags ReplayGain quando existem.", s.normalize, a.normalize)
        if (s.normalize) ToggleRow("Modo álbum", "Mantém a diferença de volume entre as faixas de um mesmo álbum.", s.normalizeAlbum, a.normalizeAlbum) } },
    SettingEntry("Áudio", "Qualidade de reprodução online", "streaming dados econômica normal alta") {
      Column(verticalArrangement = Arrangement.spacedBy(10.dp)) { Text("Qualidade de reprodução online (Streaming)", style = MaterialTheme.typography.titleMedium)
        Text("Econômica consome muito menos dados móveis e inicia mais rápido. Alta prioriza a máxima fidelidade de áudio.", color = sub, fontSize = 13.sp)
        PillSelector(listOf("Econômica", "Normal", "Alta"), s.streamQuality, { it }, a.streamQuality, Modifier.fillMaxWidth()) } },
    SettingEntry("Downloads", "Só no Wi-Fi", "rede dados móveis wifi") {
      ToggleRow("Só no Wi-Fi", "Baixa apenas em rede sem limite de dados; o resto espera na fila e continua de onde parou.", s.wifiOnly, a.wifiOnly) },
    SettingEntry("Downloads", "Qualidade padrão dos downloads", "bitrate kbps 320") {
      Column(verticalArrangement = Arrangement.spacedBy(10.dp)) { Text("Qualidade padrão dos downloads", style = MaterialTheme.typography.titleMedium)
        PillSelector(listOf("128k", "192k", "320k"), s.quality, { it }, a.quality, Modifier.fillMaxWidth()) } },
    SettingEntry("Downloads", "Formato de áudio", "mp3 m4a flac") {
      Column(verticalArrangement = Arrangement.spacedBy(10.dp)) { Text("Formato de áudio", style = MaterialTheme.typography.titleMedium)
        PillSelector(listOf("MP3", "M4A", "FLAC"), s.format, { it }, a.format, Modifier.fillMaxWidth()) } },
    SettingEntry("Aparência", "Tema", "escuro claro sistema modo noturno") {
      Column(verticalArrangement = Arrangement.spacedBy(10.dp)) { Text("Tema", style = MaterialTheme.typography.titleMedium)
        PillSelector(listOf("Sistema", "Escuro", "Claro"), s.theme, { it }, a.theme, Modifier.fillMaxWidth()) } },
    SettingEntry("Aparência", "Reduzir movimento", "animação parallax giroscópio partículas acessibilidade movimento") {
      val ctx = androidx.compose.ui.platform.LocalContext.current
      ReduceMotion.load(ctx)
      ToggleRow("Reduzir movimento", "Desliga o parallax do vidro, as partículas e os movimentos grandes (capa voando, profundidade, odômetro). Também segue \"remover animações\" do sistema.",
        ReduceMotion.user) { ReduceMotion.set(ctx, it) } },
    SettingEntry("Aparência", "Capas animadas", "motion artwork vídeo capa animada apple music wifi dados") {
      val ctx = androidx.compose.ui.platform.LocalContext.current
      MotionArtPrefs.load(ctx); ReduceMotion.load(ctx)
      Column(verticalArrangement = Arrangement.spacedBy(10.dp)) { Text("Capas animadas", style = MaterialTheme.typography.titleMedium)
        Text(if (ReduceMotion.user) "Desligadas por \"Reduzir movimento\"." else "Vídeo em loop no lugar da capa quando o álbum tem (fonte externa; sem ela, a capa normal).", color = sub, fontSize = 13.sp)
        PillSelector(MotionArtPrefs.OPTIONS, MotionArtPrefs.mode, MotionArtPrefs::label, { MotionArtPrefs.set(ctx, it) }, Modifier.fillMaxWidth()) } },
    SettingEntry("Integrações", "Last.fm", "scrobble conta") {
      Column(verticalArrangement = Arrangement.spacedBy(10.dp)) { Text("Last.fm", style = MaterialTheme.typography.titleMedium); LastFmRow(s, a) } },
    SettingEntry("Integrações", "Biblioteca", "importar arquivos pasta capas informações metadados") {
      Column(verticalArrangement = Arrangement.spacedBy(10.dp)) { Text("Biblioteca", style = MaterialTheme.typography.titleMedium)
        PillButton("Importar arquivos locais", { a.settings(false); a.importFiles() }, Modifier.fillMaxWidth(), filled = false, enabled = !s.importing)
        PillButton("Importar uma pasta", { a.settings(false); a.importFolder() }, Modifier.fillMaxWidth(), filled = false, enabled = !s.importing)
        PillButton("Ler informações e capas", { a.settings(false); a.refreshMetadata() }, Modifier.fillMaxWidth(), filled = false, enabled = !s.importing && s.history.isNotEmpty()) } },
    SettingEntry("Integrações", "Backup da biblioteca", "exportar importar favoritos playlists") {
      Column(verticalArrangement = Arrangement.spacedBy(10.dp)) { Text("Backup da biblioteca", style = MaterialTheme.typography.titleMedium)
        Text("Guarda favoritos, playlists, registros e estatísticas. Os arquivos de mídia devem ser guardados separadamente.", color = sub, fontSize = 13.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
          PillButton("Exportar", a.exportBackup, Modifier.weight(1f), filled = false)
          PillButton("Importar", a.importBackup, Modifier.weight(1f), filled = false) } } },
    SettingEntry("Integrações", "Exportar e importar a biblioteca", "json curtidas playlists álbuns artistas mesclar") {
      Column(verticalArrangement = Arrangement.spacedBy(10.dp)) { Text("Biblioteca (arquivo JSON)", style = MaterialTheme.typography.titleMedium)
        Text("Curtidas, playlists, álbuns salvos, artistas seguidos e histórico. Importar mescla com o que já existe, sem duplicar faixas.", color = sub, fontSize = 13.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
          PillButton("Exportar", a.exportLibrary, Modifier.weight(1f), filled = false)
          PillButton("Importar", a.importLibrary, Modifier.weight(1f), filled = false) } } },
    SettingEntry("Sobre", "Estatísticas", "mais ouvidas retrospectiva") { PillButton("Ver o que você mais ouviu", { a.stats(true) }, Modifier.fillMaxWidth(), filled = false) },
    SettingEntry("Sobre", "Verificar atualizações", "update versão nova") { PillButton("Verificar atualizações", a.updates, Modifier.fillMaxWidth()) },
    SettingEntry("Sobre", "Sobre o Rise", "novidades versão notas") {
      Column(verticalArrangement = Arrangement.spacedBy(10.dp)) { PillButton("Sobre o Rise", about, Modifier.fillMaxWidth(), Icons.Rounded.Info, filled = false)
        Text("Rise Musique ${BuildConfig.VERSION_NAME}", color = sub) } },
  )
}

/** Ajustes: search on top, six grouped sections, each a subpage (spring slide, back returns). Every option is kept. */
@Composable fun SettingsContent(s: UiState, a: Actions, initialSection: String? = null, initialQuery: String = "") {
    var about by remember { mutableStateOf(false) }
    var section by remember { mutableStateOf(initialSection) }
    var query by remember { mutableStateOf(initialQuery) }
    val ctx = androidx.compose.ui.platform.LocalContext.current
    if (about) { androidx.activity.compose.BackHandler { about = false }
      AboutContent(BuildConfig.VERSION_NAME, remember { releaseNotes(ctx) }, { runCatching { ctx.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(ReleasesUrl))) } },
        { runCatching { com.rise.musique.ErrorLog.export(ctx) } }) { about = false }
      return }
    androidx.activity.compose.BackHandler(section != null) { section = null }
    val entries = settingEntries(s, a) { about = true }
    val haptics = rememberHaptics()
    val c = MaterialTheme.colorScheme
    androidx.compose.animation.AnimatedContent(section, label = "settings", transitionSpec = {
      val dir = if (targetState != null) 1 else -1
      (androidx.compose.animation.slideInHorizontally(Motion.spring(Motion.SpringMedium, threshold = androidx.compose.ui.unit.IntOffset.VisibilityThreshold)) { it / 5 * dir } +
        androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(220, 40))) togetherWith
        (androidx.compose.animation.slideOutHorizontally(Motion.spring(420f, threshold = androidx.compose.ui.unit.IntOffset.VisibilityThreshold)) { -it / 8 * dir } +
          androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(140))) using androidx.compose.animation.SizeTransform(clip = false)
    }) { sec ->
      Column(Modifier.fillMaxWidth().padding(24.dp).verticalScroll(rememberScrollState()).navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        if (sec == null) {
          Text("Seu Rise", style = MaterialTheme.typography.headlineLarge)
          OutlinedTextField(query, { query = it.take(40) }, Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(20.dp),
            placeholder = { Text("Buscar nos ajustes") }, leadingIcon = { Icon(Icons.Rounded.Search, null) },
            trailingIcon = { if (query.isNotBlank()) IconButton({ query = "" }) { Icon(Icons.Rounded.Close, "Limpar busca") } })
          if (query.isBlank()) SettingsSections.forEach { g ->
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).clickable { haptics.tick(); section = g.name }.padding(vertical = 10.dp, horizontal = 4.dp),
              verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
              Box(Modifier.size(40.dp).clip(CircleShape).background(c.surfaceVariant), contentAlignment = androidx.compose.ui.Alignment.Center) { Icon(g.icon, null, Modifier.size(22.dp)) }
              Spacer(Modifier.width(14.dp))
              Column(Modifier.weight(1f)) { Text(g.name, style = MaterialTheme.typography.titleMedium); Text(g.summary, color = c.onSurfaceVariant, fontSize = 13.sp) }
              Icon(Icons.Rounded.ChevronRight, null, tint = c.onSurfaceVariant)
            }
          } else {
            val hits = entries.filter { it.matches(query) }
            if (hits.isEmpty()) Text("Nenhum ajuste com “${query.trim()}”", color = c.onSurfaceVariant)
            hits.groupBy { it.section }.forEach { (name, list) ->
              Text(name.uppercase(), color = c.onSurfaceVariant, fontSize = 11.sp, letterSpacing = 1.sp)
              list.forEach { it.content() }
            }
          }
          Text("Rise Musique ${BuildConfig.VERSION_NAME}", color = c.onSurfaceVariant, fontSize = 13.sp)
        } else {
          Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            IconButton({ haptics.tick(); section = null }) { Icon(Icons.Rounded.ArrowBack, "Voltar aos ajustes") }
            Text(sec, style = MaterialTheme.typography.headlineMedium)
          }
          entries.filter { it.section == sec }.forEach { it.content() }
        }
      }
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
