package com.rise.musique
import android.app.Application
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
class RiseApplication : Application() {
  override fun onCreate() { super.onCreate()
    val prev = Thread.getDefaultUncaughtExceptionHandler()
    Thread.setDefaultUncaughtExceptionHandler { t, e ->
      runCatching { java.io.File(filesDir, "crash.txt").writeText("Rise Musique ${BuildConfig.VERSION_NAME} · Android ${android.os.Build.VERSION.RELEASE} · ${android.os.Build.MODEL}\n\n" + e.stackTraceToString()) }
      prev?.uncaughtException(t, e) }
    runCatching { YoutubeDL.getInstance().init(this); FFmpeg.getInstance().init(this) }
      .onFailure { java.io.File(filesDir, "crash.txt").writeText("Não foi possível inicializar o motor de downloads.\n" + it.stackTraceToString()) } }
}

