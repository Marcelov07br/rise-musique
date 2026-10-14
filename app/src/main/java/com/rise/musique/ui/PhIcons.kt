package com.rise.musique.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import com.rise.musique.R
import androidx.compose.ui.unit.dp

/**
 * 0.27: ícones base (barra de abas e controles do player) da família Phosphor (MIT), baixados do repositório oficial
 * phosphor-icons/core e convertidos para vector drawables (`res/drawable/ph_*.xml`). Peso: Fill nos controles de
 * transporte e abas, Bold nos de traço (busca, aleatório, repetir, fechar).
 */
object PhIcons {
  val Home: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ph_house_fill)
  val Library: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ph_music_notes_fill)
  val Search: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ph_magnifying_glass_bold)
  val Play: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ph_play_fill)
  val Pause: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ph_pause_fill)
  val Prev: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ph_skip_back_fill)
  val Next: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ph_skip_forward_fill)
  val Shuffle: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ph_shuffle_bold)
  val Repeat: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ph_repeat_bold)
  val RepeatOne: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ph_repeat_once_bold)
  val Close: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ph_x_bold)
}

/** 0.27: medidas do vidro flutuante. A dock tem altura fixa para o mini player ficar logo acima dela. */
object GlassDock {
  val RowHeight = 64.dp
  val VPad = 8.dp
  /** Altura total da dock (linha + margens), sem a barra de navegação do sistema. */
  val Height get() = RowHeight + VPad * 2
}
