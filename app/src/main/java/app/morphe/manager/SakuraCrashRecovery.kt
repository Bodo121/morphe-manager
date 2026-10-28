/*
 * Copyright 2026 SakuraPatcher contributors.
 * Local-only crash recovery for launch failures; no telemetry and no app-data reset.
 */
package app.morphe.manager

import android.content.Context
import android.os.Process
import android.util.Log
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter

object SakuraCrashRecovery {
    private const val CRASH_FILE = "sakura_last_crash.txt"
    private const val PREFS = "sakura_crash_recovery"
    private const val SAFE_MODE = "safe_mode"
    private const val MAX_CRASH_AGE_MS = 24L * 60 * 60 * 1000
    private const val MAX_REPORT_CHARS = 32_768

    /** Installs before Koin and Compose so startup failures are persisted on-device. */
    @Synchronized
    fun install(context: Context) {
        val old = Thread.getDefaultUncaughtExceptionHandler()
        if (old is RecoveryHandler) return
        Thread.setDefaultUncaughtExceptionHandler(RecoveryHandler(context.applicationContext, old))
    }

    fun recentCrash(context: Context): String? {
        val file = File(context.filesDir, CRASH_FILE)
        if (!file.isFile || System.currentTimeMillis() - file.lastModified() > MAX_CRASH_AGE_MS) return null
        return runCatching { file.readText().take(MAX_REPORT_CHARS) }.getOrNull()
    }

    fun clearCrash(context: Context) {
        runCatching { File(context.filesDir, CRASH_FILE).delete() }
    }

    fun safeMode(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(SAFE_MODE, false)

    fun setSafeMode(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(SAFE_MODE, enabled).apply()
    }

    private class RecoveryHandler(
        val context: Context,
        val previous: Thread.UncaughtExceptionHandler?
    ) : Thread.UncaughtExceptionHandler {
        override fun uncaughtException(thread: Thread, error: Throwable) {
            try {
                val writer = StringWriter()
                PrintWriter(writer).use { print ->
                    print.println("SakuraPatcher local crash report")
                    print.println("Time: " + java.util.Date())
                    print.println("Android API: " + android.os.Build.VERSION.SDK_INT)
                    print.println("Thread: " + thread.name)
                    print.println("Message: " + (error.message ?: error.javaClass.simpleName))
                    error.printStackTrace(print)
                }
                File(context.filesDir, CRASH_FILE).writeText(writer.toString().take(MAX_REPORT_CHARS))
            } catch (ignored: Exception) {
                Log.e("SakuraCrashRecovery", "Unable to write crash report", ignored)
            }
            if (previous != null) previous.uncaughtException(thread, error)
            else {
                Process.killProcess(Process.myPid())
                System.exit(10)
            }
        }
    }
}
