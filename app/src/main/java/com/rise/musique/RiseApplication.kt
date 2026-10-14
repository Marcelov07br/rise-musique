package com.rise.musique
import android.app.Application
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
class RiseApplication : Application() {
  override fun onCreate() { super.onCreate()
    ErrorLog.init(this); ErrorLog.i("app", "início ${BuildConfig.VERSION_NAME}")
    val prev = Thread.getDefaultUncaughtExceptionHandler()
    Thread.setDefaultUncaughtExceptionHandler { t, e ->
      runCatching { java.io.File(filesDir, "crash.txt").writeText(com.rise.musique.ui.CrashReport.build(BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE,
        android.os.Build.MANUFACTURER, android.os.Build.MODEL, android.os.Build.VERSION.RELEASE, android.os.Build.VERSION.SDK_INT, t.name, System.currentTimeMillis(), e)) }
      ErrorLog.e("crash", "exceção não tratada em ${t.name}", e)
      prev?.uncaughtException(t, e) }
    runCatching { YoutubeDL.getInstance().init(this); FFmpeg.getInstance().init(this) }
      .onFailure { ErrorLog.e("app", "motor de downloads", it); java.io.File(filesDir, "crash.txt").writeText("Não foi possível inicializar o motor de downloads.\n" + it.stackTraceToString()) } }
}

