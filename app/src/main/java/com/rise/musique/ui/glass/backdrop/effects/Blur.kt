// Rise Musique 0.28.0: copiado de Lelonio/Square (GPL-3.0), app/src/main/java/dev/lelonio/square/ui/glass/backdrop/effects/Blur.kt,
// https://github.com/Lelonio/Square — modificado: só o pacote (dev.lelonio.square → com.rise.musique). Cabeçalho original abaixo.
/*
 * Vendored from Kyant0/backdrop v2.0.0 (io.github.kyant0:backdrop)
 * https://github.com/Kyant0/backdrop — Copyright 2025 Kyant0, Apache License 2.0
 *
 * Vendored so the library ships as source with this app (binary AARs compiled
 * against older Compose broke at runtime) and to add a backdrop resolution
 * scale for cheaper effect rendering. KMP expect/actual declarations were
 * merged into this single Android source set. Package renamed accordingly.
 */
package com.rise.musique.ui.glass.backdrop.effects

import androidx.annotation.FloatRange
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.TileMode
import com.rise.musique.ui.glass.backdrop.BackdropEffectScope
import com.rise.musique.ui.glass.backdrop.isRenderEffectSupported

fun BackdropEffectScope.blur(
    @FloatRange(from = 0.0) radius: Float,
    edgeTreatment: TileMode = TileMode.Clamp
) {
    if (!isRenderEffectSupported()) return
    if (radius <= 0f) return

    if (edgeTreatment != TileMode.Clamp || renderEffect != null) {
        if (radius > padding) {
            padding = radius
        }
    }

    renderEffect =
        BlurEffect(
            renderEffect,
            radius,
            radius,
            edgeTreatment
        )
}
