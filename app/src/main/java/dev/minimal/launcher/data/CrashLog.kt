package dev.minimal.launcher.data

import android.content.Context
import android.os.Build
import dev.minimal.launcher.BuildConfig
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Schreibt Abstürze in eine lokale Datei, damit sie später geteilt werden können. */
object CrashLog {
    private const val MAX_BYTES = 64 * 1024

    private fun file(context: Context) = File(context.filesDir, "crash.log")

    fun install(context: Context) {
        val appContext = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            try {
                record(appContext, thread.name, error)
            } catch (_: Throwable) {
            }
            previous?.uncaughtException(thread, error)
        }
    }

    private fun record(context: Context, threadName: String, error: Throwable) {
        val trace = StringWriter().also { error.printStackTrace(PrintWriter(it)) }.toString()
        val time = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ROOT).format(Date())
        val entry = buildString {
            append("=== $time · v${BuildConfig.VERSION_NAME} · Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}) · ")
            append("${Build.MANUFACTURER} ${Build.MODEL} · Thread $threadName\n")
            append(trace)
            append('\n')
        }
        val f = file(context)
        val old = if (f.exists()) f.readText() else ""
        // Neueste Einträge zuerst, alte abschneiden.
        f.writeText((entry + old).take(MAX_BYTES))
    }

    fun read(context: Context): String? = file(context).takeIf { it.exists() }?.readText()?.ifBlank { null }

    fun clear(context: Context) {
        file(context).delete()
    }
}
