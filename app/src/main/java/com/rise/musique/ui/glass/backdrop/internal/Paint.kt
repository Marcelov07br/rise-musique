// Rise Musique 0.28.0: copiado de Lelonio/Square (GPL-3.0), app/src/main/java/dev/lelonio/square/ui/glass/backdrop/internal/Paint.kt,
// https://github.com/Lelonio/Square — modificado: o pacote e guarda de API em setRuntimeShader (dev.lelonio.square → com.rise.musique). Cabeçalho original abaixo.
/*
 * Vendored from Kyant0/backdrop v2.0.0 (io.github.kyant0:backdrop)
 * https://github.com/Kyant0/backdrop — Copyright 2025 Kyant0, Apache License 2.0
 *
 * Vendored so the library ships as source with this app (binary AARs compiled
 * against older Compose broke at runtime) and to add a backdrop resolution
 * scale for cheaper effect rendering. KMP expect/actual declarations were
 * merged into this single Android source set. Package renamed accordingly.
 */
package com.rise.musique.ui.glass.backdrop.internal

import android.graphics.BlurMaskFilter
import androidx.compose.ui.graphics.Paint
import com.rise.musique.ui.glass.backdrop.RuntimeShader
import com.rise.musique.ui.glass.backdrop.asAndroidRuntimeShader

internal fun Paint.blur(radius: Float) {
    this.asFrameworkPaint().maskFilter =
        if (radius > 0f) BlurMaskFilter(radius, BlurMaskFilter.Blur.NORMAL)
        else null
}

@android.annotation.SuppressLint("NewApi") // guarda de API logo abaixo
internal fun Paint.setRuntimeShader(runtimeShader: RuntimeShader?) {
    // Rise: guarda explícita de API para o lint (só há RuntimeShader no API 33+).
    this.asFrameworkPaint().shader =
        if (runtimeShader != null && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) runtimeShader.asAndroidRuntimeShader() else null
}
