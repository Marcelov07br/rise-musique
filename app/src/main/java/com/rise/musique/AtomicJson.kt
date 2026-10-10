package com.rise.musique

import android.util.AtomicFile
import java.io.File

/** Commit or roll back a JSON write, including when the process is interrupted. */
object AtomicJson {
  @Synchronized fun read(file: File): String? {
    val atomic = AtomicFile(file)
    if (!file.exists() && !File(file.path + ".bak").exists()) return null
    return atomic.openRead().use { it.readBytes().toString(Charsets.UTF_8) }
  }
  @Synchronized fun write(file: File, json: String) {
    val atomic = AtomicFile(file)
    val out = atomic.startWrite()
    try { out.write(json.toByteArray(Charsets.UTF_8)); atomic.finishWrite(out) }
    catch (e: Exception) { atomic.failWrite(out); throw e }
  }
}
