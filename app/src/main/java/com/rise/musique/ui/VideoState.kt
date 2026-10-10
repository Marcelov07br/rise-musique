package com.rise.musique.ui

data class VideoUi(val title: String, val channel: String, val thumb: String, val playing: Boolean = false, val loading: Boolean = false,
                   val position: Long = 0, val duration: Long = 0, val qualities: List<Int> = emptyList(), val quality: Int = 0,
                   val subtitles: List<String> = emptyList(), val subtitle: String? = null, val fullscreen: Boolean = false,
                   val aspect: Float = 16f / 9f, val hasNext: Boolean = false, val local: Boolean = false, val error: String? = null)

data class VideoActions(val close: () -> Unit = {}, val playPause: () -> Unit = {}, val seek: (Long) -> Unit = {}, val seekBy: (Long) -> Unit = {},
                        val quality: (Int) -> Unit = {}, val subtitle: (String?) -> Unit = {}, val fullscreen: (Boolean) -> Unit = {},
                        val next: () -> Unit = {}, val pip: () -> Unit = {})

/** Voice search: listening state, live partial text and mic level 0..1. */
data class VoiceUi(val listening: Boolean = true, val partial: String = "", val level: Float = 0f, val error: String? = null)
