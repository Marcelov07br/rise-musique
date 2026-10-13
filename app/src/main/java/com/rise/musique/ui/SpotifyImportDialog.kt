package com.rise.musique.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rise.musique.SpotifyImport

private val SpotifyGreen = Color(0xFF1DB954)

@Composable fun SpotifyImportDialog(
  onDismiss: () -> Unit,
  onImport: (String) -> Unit
) {
  val clipboard = LocalClipboardManager.current
  val keyboard = LocalSoftwareKeyboardController.current
  var url by remember {
    val clip = clipboard.getText()?.text?.trim().orEmpty()
    mutableStateOf(if (SpotifyImport.isSpotifyUrl(clip)) clip else "")
  }
  var loading by remember { mutableStateOf(false) }

  AlertDialog(
    onDismissRequest = { if (!loading) onDismiss() },
    shape = RoundedCornerShape(28.dp),
    title = {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
          Modifier.size(36.dp).background(SpotifyGreen.copy(alpha = .15f), CircleShape),
          contentAlignment = Alignment.Center
        ) {
          Icon(Icons.Rounded.QueueMusic, null, tint = SpotifyGreen, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(12.dp))
        Text("Importar do Spotify", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
      }
    },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(
          "Cole o link de uma playlist ou álbum público do Spotify para ver as faixas, ouvir ou baixar para o seu celular.",
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          fontSize = 14.sp,
          lineHeight = 20.sp
        )

        OutlinedTextField(
          value = url,
          onValueChange = { url = it },
          modifier = Modifier.fillMaxWidth(),
          shape = RoundedCornerShape(16.dp),
          placeholder = { Text("https://open.spotify.com/playlist/…", fontSize = 13.sp) },
          singleLine = true,
          leadingIcon = { Icon(Icons.Rounded.Link, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
          trailingIcon = {
            if (url.isNotBlank()) {
              IconButton({ url = "" }) { Icon(Icons.Rounded.Close, "Limpar") }
            } else {
              TextButton({
                val clipText = clipboard.getText()?.text?.trim().orEmpty()
                if (clipText.isNotBlank()) url = clipText
              }) {
                Text("Colar", fontSize = 13.sp)
              }
            }
          },
          keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
          keyboardActions = KeyboardActions(onDone = {
            if (url.isNotBlank() && !loading) {
              keyboard?.hide()
              loading = true
              onImport(url.trim())
            }
          })
        )

        if (loading) {
          Row(
            Modifier.fillMaxWidth().padding(top = 4.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
          ) {
            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = SpotifyGreen)
            Spacer(Modifier.width(10.dp))
            Text("Buscando playlist…", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
          }
        }
      }
    },
    confirmButton = {
      Button(
        onClick = {
          keyboard?.hide()
          loading = true
          onImport(url.trim())
        },
        enabled = url.isNotBlank() && !loading,
        colors = ButtonDefaults.buttonColors(containerColor = SpotifyGreen, contentColor = Color.White),
        shape = CircleShape
      ) {
        Text("Carregar", fontWeight = FontWeight.SemiBold)
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss, enabled = !loading) {
        Text("Cancelar")
      }
    }
  )
}
