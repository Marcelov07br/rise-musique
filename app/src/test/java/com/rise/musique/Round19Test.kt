package com.rise.musique

import com.rise.musique.ui.CrashReport
import org.junit.Assert.*
import org.junit.Test

class Round19Test {
  @Test fun crashReportHasEverythingAndASummary() {
    val e = RuntimeException("x", IllegalStateException("An instance of SeekableTransitionState has been used"))
    val r = CrashReport.build("0.19.0", 22, "Google", "Pixel 8", "15", 35, "main", 0, e)
    listOf("Rise Musique 0.19.0 (22)", "Google Pixel 8", "Android 15 (API 35)", "Thread: main", "IllegalStateException").forEach { assertTrue(it, r.contains(it)) }
    val s = CrashReport.summary(r)
    assertTrue(s, s.startsWith("IllegalStateException: An instance"))
    assertTrue(s, s.contains("em Round19Test"))
    assertEquals("", CrashReport.summary(""))
  }
  @Test fun creditsCoverTheCoreLibraries() {
    val names = com.rise.musique.ui.Credits.joinToString { it.first }
    listOf("yt-dlp", "FFmpeg", "Media3", "Coil").forEach { assertTrue(it, names.contains(it)) }
  }
}
