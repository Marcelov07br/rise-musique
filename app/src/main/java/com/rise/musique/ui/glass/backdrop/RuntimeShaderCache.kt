// Rise Musique 0.28.0: copiado de Lelonio/Square (GPL-3.0), app/src/main/java/dev/lelonio/square/ui/glass/backdrop/RuntimeShaderCache.kt,
// https://github.com/Lelonio/Square — modificado: o pacote e @SuppressLint(NewApi) documentado (dev.lelonio.square → com.rise.musique). Cabeçalho original abaixo.
/*
 * Vendored from Kyant0/backdrop v2.0.0 (io.github.kyant0:backdrop)
 * https://github.com/Kyant0/backdrop — Copyright 2025 Kyant0, Apache License 2.0
 *
 * Vendored so the library ships as source with this app (binary AARs compiled
 * against older Compose broke at runtime) and to add a backdrop resolution
 * scale for cheaper effect rendering. KMP expect/actual declarations were
 * merged into this single Android source set. Package renamed accordingly.
 */
package com.rise.musique.ui.glass.backdrop

import org.intellij.lang.annotations.Language

sealed interface RuntimeShaderCache {

    fun obtainRuntimeShader(key: String, @Language("AGSL") string: String): RuntimeShader
}

internal class RuntimeShaderCacheImpl : RuntimeShaderCache {

    private val runtimeShaders = mutableMapOf<String, RuntimeShader>()

    // Rise: todos os chamadores checam isRuntimeShaderSupported() (API 33+) antes; anotado para o lint.
    @android.annotation.SuppressLint("NewApi")
    override fun obtainRuntimeShader(key: String, string: String): RuntimeShader {
        return runtimeShaders.getOrPut(key) { RuntimeShader(string) }
    }

    fun clear() {
        runtimeShaders.clear()
    }
}
