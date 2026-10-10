package com.rise.musique.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.rise.musique.UpdatesViewModel

@Composable fun UpdateDialog(vm: UpdatesViewModel, install: () -> Unit) {
  if (!vm.dialog) return
  AlertDialog(onDismissRequest = vm::dismiss,
    title = { Text("Atualizações do Rise Musique") },
    text = { Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
      Text(vm.message.ifBlank { "Consultando o GitHub…" })
      if (vm.checking) LinearProgressIndicator(Modifier.fillMaxWidth())
      vm.release?.notes?.takeIf { it.isNotBlank() }?.let { Text(it) }
      if (vm.downloading) { LinearProgressIndicator(progress = { vm.progress }, modifier = Modifier.fillMaxWidth()); Text("Baixando ${(vm.progress * 100).toInt()}%") }
      if (vm.ready != null) Text("O Android pedirá sua confirmação para instalar. Suas músicas e preferências serão mantidas.")
    } },
    confirmButton = { if (vm.release != null && !vm.checking) TextButton(
      onClick = { if (vm.ready != null) install() else vm.download() }, enabled = !vm.downloading) {
      Text(if (vm.ready != null) "Instalar" else "Baixar atualização")
    } else TextButton(onClick = { vm.check(true) }, enabled = !vm.checking) { Text("Verificar novamente") } },
    dismissButton = { TextButton(onClick = { if (vm.downloading) vm.cancel() else vm.dismiss() }) {
      Text(if (vm.downloading) "Cancelar download" else "Fechar")
    } })
}
