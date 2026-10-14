package com.rise.musique.ui

import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** 0.24: spacing and type tokens (4 dp grid). New/edited UI uses these; older screens migrate as they are touched. */
object Space { val xxs = 4.dp; val xs = 6.dp; val s = 8.dp; val m = 12.dp; val l = 16.dp; val xl = 24.dp; val xxl = 32.dp
  /** Minimum touch target. */ val touch = 48.dp }
object TypeScale { val caption = 12.sp; val footnote = 13.sp; val body = 15.sp; val callout = 16.sp; val headline = 18.sp; val title = 22.sp; val large = 34.sp }
